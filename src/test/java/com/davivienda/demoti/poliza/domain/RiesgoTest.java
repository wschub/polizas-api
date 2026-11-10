package com.davivienda.demoti.poliza.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.davivienda.demoti.shared.exception.ReglaNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de {@link Riesgo}, sin Spring ni base de datos. */
class RiesgoTest {

	private static Poliza poliza(TipoPoliza tipo) {
		return new Poliza(
				"POL-001",
				tipo,
				"Inmobiliaria Andina SAS",
				LocalDate.of(2026, 1, 1),
				12,
				new BigDecimal("1000.00"),
				"Carrera 7 # 45-10",
				"Luisa Martinez",
				"Inmobiliaria Andina SAS");
	}

	@Test
	@DisplayName("CU6-a: cancelar un riesgo lo deja CANCELADO sin cambiar el estado de su poliza colectiva")
	void cancelaRiesgoSinTocarPolizaColectiva() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA);
		Riesgo riesgo =
				poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");

		riesgo.cancelar();

		assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		// R8: la poliza no se entera
		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
		assertThat(poliza.getRiesgos().getFirst().getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
	}

	@Test
	@DisplayName("CU6-a: cancelar el unico riesgo de una poliza INDIVIDUAL tampoco la cancela")
	void cancelaRiesgoSinTocarPolizaIndividual() {
		Poliza poliza = poliza(TipoPoliza.INDIVIDUAL);
		Riesgo unico = poliza.getRiesgos().getFirst();

		unico.cancelar();

		assertThat(unico.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		// R8: aplica igual a individual y a colectiva
		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
	}

	@Test
	@DisplayName("CU6-b: no cancela un riesgo ya CANCELADO")
	void noCancelaRiesgoYaCancelado() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA);
		Riesgo riesgo = poliza.getRiesgos().getFirst();
		riesgo.cancelar();

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(riesgo::cancelar)
				.withMessageContaining("ya esta CANCELADO");

		assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
	}

	@Test
	@DisplayName("nace ACTIVO y apuntando a su poliza")
	void naceActivo() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA);

		Riesgo riesgo = poliza.getRiesgos().getFirst();

		assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(riesgo.getPoliza()).isSameAs(poliza);
	}
}
