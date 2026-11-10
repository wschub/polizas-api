package com.davivienda.demoti.poliza.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.davivienda.demoti.shared.exception.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reglas y calculos de {@link Poliza}, sin Spring ni base de datos. */
class PolizaTest {

	private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
	private static final BigDecimal IPC_DEFECTO = new BigDecimal("5.2");

	private static Poliza poliza(TipoPoliza tipo, int mesesVigencia, String canonMensual) {
		return new Poliza(
				"POL-001",
				tipo,
				"Inmobiliaria Andina SAS",
				INICIO,
				mesesVigencia,
				new BigDecimal(canonMensual),
				"Carrera 7 # 45-10",
				"Luisa Martinez",
				"Inmobiliaria Andina SAS");
	}

	private static Poliza colectiva() {
		return poliza(TipoPoliza.COLECTIVA, 12, "1000.00");
	}

	private static Poliza individual() {
		return poliza(TipoPoliza.INDIVIDUAL, 12, "1000.00");
	}

	@Test
	@DisplayName("nace VIGENTE, con un riesgo ACTIVO, prima derivada y vigencia calculada")
	void naceVigenteConSuPrimerRiesgo() {
		Poliza poliza = colectiva();

		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
		assertThat(poliza.getFechaInicioVigencia()).isEqualTo(INICIO);
		assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1000.00");
		assertThat(poliza.getPrima()).isEqualByComparingTo("12000.00");
		assertThat(poliza.getRiesgos()).hasSize(1);
		assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
	}

	@Nested
	@DisplayName("renovar")
	class Renovar {

		@Test
		@DisplayName("CU3-a: con el IPC por defecto recalcula canon y prima, corre la vigencia y queda RENOVADA")
		void renuevaConIpcPorDefecto() {
			Poliza poliza = colectiva();

			poliza.renovar(IPC_DEFECTO);

			// 1000.00 x 1.052 = 1052.00
			assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1052.00");
			// 1052.00 x 12 meses
			assertThat(poliza.getPrima()).isEqualByComparingTo("12624.00");
			// la nueva vigencia arranca donde terminaba la anterior
			assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
			assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
			assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
		}

		@Test
		@DisplayName("CU3-c: no renueva una poliza CANCELADA")
		void noRenuevaPolizaCancelada() {
			Poliza poliza = colectiva();
			poliza.cancelar();

			assertThatExceptionOfType(ReglaNegocioException.class)
					.isThrownBy(() -> poliza.renovar(IPC_DEFECTO))
					.withMessageContaining("CANCELADA");

			assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1000.00");
			assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
		}

		@Test
		@DisplayName("CU3-d: una poliza ya RENOVADA vuelve a renovarse e incrementa otra vez")
		void renuevaVariasVeces() {
			Poliza poliza = colectiva();

			poliza.renovar(IPC_DEFECTO);
			poliza.renovar(IPC_DEFECTO);

			// 1000.00 -> 1052.00 -> 1052.00 x 1.052 = 1106.704 -> 1106.70
			assertThat(poliza.getCanonMensual()).isEqualByComparingTo("1106.70");
			assertThat(poliza.getPrima()).isEqualByComparingTo("13280.40");
			assertThat(poliza.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
			assertThat(poliza.getFechaFinVigencia()).isEqualTo(LocalDate.of(2029, 1, 1));
			assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
		}

		@Test
		@DisplayName("CU3-e: redondea a escala 2 con HALF_UP")
		void redondeaAEscalaDosHalfUp() {
			// 10.10 x 1.05 = 10.605 exacto: HALF_UP da 10.61 y HALF_EVEN daria 10.60,
			// asi que este caso distingue el modo de redondeo pedido.
			Poliza poliza = poliza(TipoPoliza.COLECTIVA, 12, "10.10");

			poliza.renovar(new BigDecimal("5.0"));

			assertThat(poliza.getCanonMensual()).isEqualByComparingTo("10.61");
			assertThat(poliza.getCanonMensual().scale()).isEqualTo(2);
			assertThat(poliza.getPrima()).isEqualByComparingTo("127.32");
			assertThat(poliza.getPrima().scale()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("cancelar")
	class Cancelar {

		@Test
		@DisplayName("CU4-a: cancela la poliza y todos sus riesgos ACTIVO")
		void cancelaPolizaYSusRiesgos() {
			Poliza poliza = colectiva();
			poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");
			poliza.agregarRiesgo("Avenida 68 # 24-15", "Marta Quintero", "Inmobiliaria Andina SAS");

			poliza.cancelar();

			assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
			assertThat(poliza.getRiesgos())
					.hasSize(3)
					.allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.CANCELADO);
		}

		@Test
		@DisplayName("CU4-a: no falla si algun riesgo ya estaba CANCELADO")
		void cancelaPolizaConRiesgoYaCancelado() {
			Poliza poliza = colectiva();
			Riesgo segundo =
					poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");
			segundo.cancelar();

			poliza.cancelar();

			assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
			assertThat(poliza.getRiesgos())
					.allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.CANCELADO);
		}

		@Test
		@DisplayName("CU4-b: no cancela una poliza ya CANCELADA")
		void noCancelaPolizaYaCancelada() {
			Poliza poliza = colectiva();
			poliza.cancelar();

			assertThatExceptionOfType(ReglaNegocioException.class)
					.isThrownBy(poliza::cancelar)
					.withMessageContaining("ya esta CANCELADA");
		}
	}

	@Nested
	@DisplayName("agregarRiesgo")
	class AgregarRiesgo {

		@Test
		@DisplayName("CU5-a: agrega un riesgo ACTIVO a una poliza COLECTIVA")
		void agregaRiesgoAColectiva() {
			Poliza poliza = colectiva();

			Riesgo riesgo =
					poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");

			assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
			assertThat(riesgo.getPoliza()).isSameAs(poliza);
			assertThat(riesgo.getDireccionInmueble()).isEqualTo("Calle 85 # 12-30");
			assertThat(riesgo.getArrendatario()).isEqualTo("Pedro Alvarez");
			assertThat(riesgo.getArrendador()).isEqualTo("Inmobiliaria Andina SAS");
			assertThat(poliza.getRiesgos()).hasSize(2).contains(riesgo);
		}

		@Test
		@DisplayName("CU5-a: una poliza COLECTIVA RENOVADA sigue admitiendo riesgos")
		void agregaRiesgoAColectivaRenovada() {
			Poliza poliza = colectiva();
			poliza.renovar(IPC_DEFECTO);

			poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");

			assertThat(poliza.getRiesgos()).hasSize(2);
		}

		@Test
		@DisplayName("CU5-b: no agrega riesgos a una poliza INDIVIDUAL")
		void noAgregaRiesgoAIndividual() {
			Poliza poliza = individual();

			assertThatExceptionOfType(ReglaNegocioException.class)
					.isThrownBy(
							() -> poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Ana Gomez"))
					.withMessageContaining("COLECTIVA");

			assertThat(poliza.getRiesgos()).hasSize(1);
		}

		@Test
		@DisplayName("CU5-c: no agrega riesgos a una poliza CANCELADA")
		void noAgregaRiesgoAPolizaCancelada() {
			Poliza poliza = colectiva();
			poliza.cancelar();

			assertThatExceptionOfType(ReglaNegocioException.class)
					.isThrownBy(
							() ->
									poliza.agregarRiesgo(
											"Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS"))
					.withMessageContaining("CANCELADA");

			assertThat(poliza.getRiesgos()).hasSize(1);
		}
	}
}
