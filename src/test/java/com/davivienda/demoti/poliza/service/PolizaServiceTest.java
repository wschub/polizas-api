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
import com.davivienda.demoti.notificacion.PolizaRenovadaEvent;
import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.EstadoRiesgo;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.dto.PolizaResponse;
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
 * Orquestacion de {@link PolizaService}: 404, y que el CORE se invoque solo
 * cuando la operacion tiene exito. Sin Spring ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class PolizaServiceTest {

	private static final Long POLIZA_ID = 42L;
	private static final BigDecimal IPC_POR_DEFECTO = new BigDecimal("5.2");

	@Mock private PolizaRepository polizaRepository;
	@Mock private RiesgoRepository riesgoRepository;
	@Mock private CoreGateway coreGateway;
	@Mock private ApplicationEventPublisher publicadorDeEventos;

	@Captor private ArgumentCaptor<PolizaRenovadaEvent> eventoCapturado;

	private PolizaService polizaService;

	@BeforeEach
	void prepararService() {
		polizaService =
				new PolizaService(
						polizaRepository,
						riesgoRepository,
						coreGateway,
						publicadorDeEventos,
						IPC_POR_DEFECTO);
	}

	/**
	 * El id lo asigna la base de datos, asi que en un test sin JPA hay que ponerlo a
	 * mano: el service lo usa para notificar al CORE y hay que poder afirmar que es
	 * el correcto.
	 */
	private static Poliza polizaCon(Long id, TipoPoliza tipo, String canon) {
		Poliza poliza =
				new Poliza(
						"POL-001",
						tipo,
						"Inmobiliaria Andina SAS",
						LocalDate.of(2026, 1, 1),
						12,
						new BigDecimal(canon),
						"Carrera 7 # 45-10",
						"Luisa Martinez",
						"Inmobiliaria Andina SAS");
		try {
			Field campoId = Poliza.class.getDeclaredField("id");
			campoId.setAccessible(true);
			campoId.set(poliza, id);
		} catch (ReflectiveOperationException excepcion) {
			throw new IllegalStateException("no se pudo asignar el id de prueba", excepcion);
		}
		return poliza;
	}

	@Test
	@DisplayName("CU3-b: el ipc del body sobrescribe el valor por defecto")
	void renuevaConElIpcDelBody() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		PolizaResponse respuesta = polizaService.renovar(POLIZA_ID, new BigDecimal("10"));

		// con el 10 del body: 1000.00 x 1.10 = 1100.00, no 1052.00 del 5.2 por defecto
		assertThat(respuesta.canonMensual()).isEqualByComparingTo("1100.00");
		assertThat(respuesta.prima()).isEqualByComparingTo("13200.00");
		assertThat(respuesta.estado()).isEqualTo(EstadoPoliza.RENOVADA);
	}

	@Test
	@DisplayName("CU3-b: sin ipc en el body se aplica el valor por defecto")
	void renuevaConElIpcPorDefectoCuandoNoViene() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		PolizaResponse respuesta = polizaService.renovar(POLIZA_ID, null);

		// 1000.00 x 1.052
		assertThat(respuesta.canonMensual()).isEqualByComparingTo("1052.00");
		assertThat(respuesta.prima()).isEqualByComparingTo("12624.00");
	}

	@Test
	@DisplayName("CU3-b: notifica al CORE una sola vez con el id de la poliza renovada")
	void notificaAlCoreAlRenovar() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		polizaService.renovar(POLIZA_ID, null);

		verify(coreGateway, times(1)).notificarActualizacion(POLIZA_ID);
	}

	@Test
	@DisplayName("CU3-c: renovar una poliza CANCELADA lanza ReglaNegocioException y no toca el CORE")
	void noRenuevaPolizaCanceladaNiNotificaAlCore() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		poliza.cancelar();
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> polizaService.renovar(POLIZA_ID, null));

		verifyNoInteractions(coreGateway);
		verify(polizaRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU3-g: renovar una poliza inexistente lanza RecursoNoEncontradoException y no toca el CORE")
	void noRenuevaPolizaInexistente() {
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> polizaService.renovar(POLIZA_ID, null))
				.withMessageContaining(POLIZA_ID.toString());

		verifyNoInteractions(coreGateway);
		verify(polizaRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU4-a: cancelar notifica al CORE exactamente una vez con el id de la poliza")
	void cancelaYNotificaAlCoreUnaVez() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		poliza.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		PolizaResponse respuesta = polizaService.cancelar(POLIZA_ID);

		assertThat(respuesta.estado()).isEqualTo(EstadoPoliza.CANCELADA);
		assertThat(poliza.getRiesgos()).allMatch(r -> r.getEstado() == EstadoRiesgo.CANCELADO);
		verify(coreGateway, times(1)).notificarActualizacion(POLIZA_ID);
	}

	@Test
	@DisplayName("CU4-b: cancelar una poliza ya CANCELADA lanza ReglaNegocioException y no toca el CORE")
	void noCancelaPolizaYaCanceladaNiNotificaAlCore() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		poliza.cancelar();
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> polizaService.cancelar(POLIZA_ID));

		verifyNoInteractions(coreGateway);
		verify(polizaRepository, never()).save(any());
	}

	@Test
	@DisplayName("CU4-c: cancelar una poliza inexistente lanza RecursoNoEncontradoException")
	void noCancelaPolizaInexistente() {
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> polizaService.cancelar(POLIZA_ID));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU9-a: renovar publica PolizaRenovadaEvent exactamente una vez con los datos renovados")
	void publicaElEventoDeRenovacionUnaVez() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		polizaService.renovar(POLIZA_ID, null);

		verify(publicadorDeEventos, times(1)).publishEvent(eventoCapturado.capture());
		PolizaRenovadaEvent evento = eventoCapturado.getValue();

		assertThat(evento.polizaId()).isEqualTo(POLIZA_ID);
		assertThat(evento.numero()).isEqualTo("POL-001");
		assertThat(evento.tomador()).isEqualTo("Inmobiliaria Andina SAS");
		// los importes del evento son los de DESPUES de renovar: 1000.00 x 1.052
		assertThat(evento.canonMensual()).isEqualByComparingTo("1052.00");
		assertThat(evento.prima()).isEqualByComparingTo("12624.00");
		// la vigencia tambien es la nueva
		assertThat(evento.fechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
	}

	@Test
	@DisplayName("CU9-b: renovar una poliza CANCELADA no publica ningun evento")
	void noPublicaEventoSiLaPolizaEstaCancelada() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		poliza.cancelar();
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));

		assertThatExceptionOfType(ReglaNegocioException.class)
				.isThrownBy(() -> polizaService.renovar(POLIZA_ID, null));

		verifyNoInteractions(publicadorDeEventos);
	}

	@Test
	@DisplayName("CU9-b: renovar una poliza inexistente no publica ningun evento")
	void noPublicaEventoSiLaPolizaNoExiste() {
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.empty());

		assertThatExceptionOfType(RecursoNoEncontradoException.class)
				.isThrownBy(() -> polizaService.renovar(POLIZA_ID, null));

		verifyNoInteractions(publicadorDeEventos);
	}

	@Test
	@DisplayName("CU9-b: cancelar una poliza no publica PolizaRenovadaEvent")
	void cancelarNoPublicaEventoDeRenovacion() {
		Poliza poliza = polizaCon(POLIZA_ID, TipoPoliza.COLECTIVA, "1000.00");
		when(polizaRepository.findById(POLIZA_ID)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(poliza)).thenReturn(poliza);

		polizaService.cancelar(POLIZA_ID);

		verifyNoInteractions(publicadorDeEventos);
	}
}
