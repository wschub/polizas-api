package com.davivienda.demoti.poliza.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.davivienda.demoti.core.CoreGateway;
import com.davivienda.demoti.notificacion.RiesgoAgregadoEvent;
import com.davivienda.demoti.poliza.domain.EstadoRiesgo;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.Riesgo;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.dto.CrearRiesgoRequest;
import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.exception.RecursoNoEncontradoException;
import com.davivienda.demoti.shared.exception.ReglaNegocioException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Orquestacion de {@link RiesgoService}: 404, y que el CORE se invoque solo
 * cuando la operacion tiene exito y siempre con el id de la POLIZA.
 */
@ExtendWith(MockitoExtension.class)
class RiesgoServiceTest {

	private static final Long POLIZA_ID = 42L;
	private static final Long RIESGO_ID = 7L;

	private static final CrearRiesgoRequest PETICION =
			new CrearRiesgoRequest("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");

	@Mock private PolizaRepository polizaRepository;
	@Mock private RiesgoRepository riesgoRepository;
	@Mock private CoreGateway coreGateway;
	@Mock private ApplicationEventPublisher publicadorDeEventos;

	@Captor private ArgumentCaptor<RiesgoAgregadoEvent> eventoCapturado;

	private RiesgoService riesgoService;

	@BeforeEach
	void prepararService() {
		riesgoService =
				new RiesgoService(
						polizaRepository, riesgoRepository, coreGateway, publicadorDeEventos);
	}

	/** Sin JPA nadie asigna los ids, y el service los usa para notificar al CORE. */
	private static void asignarId(Object entidad, Long id) {
		try {
			Field campoId = entidad.getClass().getDeclaredField("id");
			campoId.setAccessible(true);
			campoId.set(entidad, id);
		} catch (ReflectiveOperationException excepcion) {
			throw new IllegalStateException("no se pudo asignar el id de prueba", excepcion);
		}
	}

	private static Poliza polizaCon(Long id, TipoPoliza tipo) {
		Poliza poliza =
				new Poliza(
						"POL-001",
						tipo,
						"Inmobiliaria Andina SAS",
						LocalDate.of(2026, 1, 1),
						12,
						new BigDecimal("1000.00"),
						"Carrera 7 # 45-10",
						"Luisa Martinez",
						"Inmobiliaria Andina SAS");
		asignarId(poliza, id);
		return poliza;
	}

	@Test
	@DisplayName("CU5-a: agrega el riesgo a una poliza COLECTIVA y notifica al CORE con el id de la poliza")
	void agregaRiesgoAColectiva() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		// riesgoRepository.save es lo que en produccion puebla el id; aqui se simula
		when(riesgoRepository.save(any(Riesgo.class)))
				.thenAnswer(
						invocacion -> {
							Riesgo guardado = invocacion.getArgument(0);
							asignarId(guardado, RIESGO_ID);
							return guardado;
						});

		RiesgoResponse respuesta = riesgoService.agregar(POLIZA_ID, PETICION);

		assertThat(respuesta.id()).isEqualTo(RIESGO_ID);
		assertThat(respuesta.polizaId()).isEqualTo(POLIZA_ID);
		assertThat(respuesta.estado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(respuesta.direccionInmueble()).isEqualTo("Calle 85 # 12-30");
		assertThat(respuesta.arrendatario()).isEqualTo("Pedro Alvarez");
		assertThat(respuesta.arrendador()).isEqualTo("Inmobiliaria Andina SAS");
		assertThat(poliza.getRiesgos()).hasSize(2);

		verify(coreGateway, times(1)).notificarActualizacion(POLIZA_ID);
	}

	@Test
	@DisplayName("CU5-b: agregar a una poliza INDIVIDUAL lanza ReglaNegocioException y no toca el CORE")
	void noAgregaRiesgoAIndividual() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.INDIVIDUAL);
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION))
				.withMessageContaining("COLECTIVA");

		assertThat(poliza.getRiesgos()).hasSize(1);
		verifyNoInteractions(coreGateway);
		verify(riesgoRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU5-c: agregar a una poliza CANCELADA lanza ReglaNegocioException y no toca el CORE")
	void noAgregaRiesgoAPolizaCancelada() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		poliza.cancelar();
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION))
				.withMessageContaining("CANCELADA");

		assertThat(poliza.getRiesgos()).hasSize(1);
		verifyNoInteractions(coreGateway);
		verify(riesgoRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU5-e: agregar a una poliza inexistente lanza RecursoNoEncontradoException")
	void noAgregaRiesgoAPolizaInexistente() {
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION))
				.withMessageContaining(POLIZA_ID.toString());

		verifyNoInteractions(coreGateway);
		verify(riesgoRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU6-a: cancelar el riesgo notifica al CORE con el id de la POLIZA, no el del riesgo")
	void cancelaRiesgoYNotificaConElIdDeLaPoliza() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		Riesgo riesgo = poliza.getRiesgos().getFirst();
		asignarId(riesgo, RIESGO_ID);
		when(riesgoRepository.findByIdConPoliza(RIESGO_ID)).thenReturn(Optional.of(riesgo));
		when(riesgoRepository.save(riesgo)).thenReturn(riesgo);

		RiesgoResponse respuesta = riesgoService.cancelar(RIESGO_ID);

		assertThat(respuesta.id()).isEqualTo(RIESGO_ID);
		assertThat(respuesta.estado()).isEqualTo(EstadoRiesgo.CANCELADO);

		verify(coreGateway, times(1)).notificarActualizacion(POLIZA_ID);
		verify(coreGateway, never()).notificarActualizacion(RIESGO_ID);
	}

	@Test
	@DisplayName("CU6-b: cancelar un riesgo ya CANCELADO lanza ReglaNegocioException y no toca el CORE")
	void noCancelaRiesgoYaCancelado() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		Riesgo riesgo = poliza.getRiesgos().getFirst();
		asignarId(riesgo, RIESGO_ID);
		riesgo.cancelar();
		when(riesgoRepository.findByIdConPoliza(RIESGO_ID)).thenReturn(Optional.of(riesgo));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> riesgoService.cancelar(RIESGO_ID));

		verifyNoInteractions(coreGateway);
		verify(riesgoRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU6-c: cancelar un riesgo inexistente lanza RecursoNoEncontradoException")
	void noCancelaRiesgoInexistente() {
		when(riesgoRepository.findByIdConPoliza(RIESGO_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> riesgoService.cancelar(RIESGO_ID))
				.withMessageContaining(RIESGO_ID.toString());

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU9-d: agregar un riesgo publica RiesgoAgregadoEvent una vez con los datos correctos")
	void publicaElEventoDeRiesgoAgregado() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(riesgoRepository.save(any(Riesgo.class)))
				.thenAnswer(
						invocacion -> {
							Riesgo guardado = invocacion.getArgument(0);
							asignarId(guardado, RIESGO_ID);
							return guardado;
						});

		riesgoService.agregar(POLIZA_ID, PETICION);

		verify(publicadorDeEventos, times(1)).publishEvent(eventoCapturado.capture());
		RiesgoAgregadoEvent evento = eventoCapturado.getValue();

		assertThat(evento.polizaId()).isEqualTo(POLIZA_ID);
		assertThat(evento.numeroPoliza()).isEqualTo("POL-001");
		assertThat(evento.tomador()).isEqualTo("Inmobiliaria Andina SAS");
		// el id del riesgo ya viene poblado en el evento
		assertThat(evento.riesgoId()).isEqualTo(RIESGO_ID);
		assertThat(evento.direccionInmueble()).isEqualTo("Calle 85 # 12-30");
	}

	@Test
	@DisplayName("CU9-e: agregar a una poliza INDIVIDUAL no publica ningun evento")
	void noPublicaEventoSiLaPolizaEsIndividual() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.INDIVIDUAL);
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION));

		verifyNoInteractions(publicadorDeEventos);
	}

	@Test
	@DisplayName("CU9-e: agregar a una poliza CANCELADA no publica ningun evento")
	void noPublicaEventoSiLaPolizaEstaCancelada() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		poliza.cancelar();
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION));

		verifyNoInteractions(publicadorDeEventos);
	}

	@Test
	@DisplayName("CU9-e: agregar a una poliza inexistente no publica ningun evento")
	void noPublicaEventoSiLaPolizaNoExiste() {
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> riesgoService.agregar(POLIZA_ID, PETICION));

		verifyNoInteractions(publicadorDeEventos);
	}

	@Test
	@DisplayName("CU9-e: cancelar un riesgo no publica ningun evento de notificacion")
	void cancelarRiesgoNoPublicaEventos() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA);
		Riesgo riesgo = poliza.getRiesgos().getFirst();
		asignarId(riesgo, RIESGO_ID);
		when(riesgoRepository.findByIdConPoliza(RIESGO_ID)).thenReturn(Optional.of(riesgo));
		when(riesgoRepository.save(riesgo)).thenReturn(riesgo);

		riesgoService.cancelar(RIESGO_ID);

		verifyNoInteractions(publicadorDeEventos);
	}
}
