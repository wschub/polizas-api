package com.davivienda.demoti.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.davivienda.demoti.TestcontainersConfiguration;
import com.davivienda.demoti.core.CoreGateway;
import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.security.ApiKeyFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU9-a, CU9-b y CU9-c: notificacion simulada al tomador tras renovar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificacionListenerIT {

	private static final String API_KEY = "123456";
	private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

	@Autowired private MockMvc mockMvc;
	@Autowired private PolizaRepository polizaRepository;
	@Autowired private RiesgoRepository riesgoRepository;

	@MockitoBean private CoreGateway coreGateway;

	private Logger logger;
	private ListAppender<ILoggingEvent> appender;

	private Long idVigente;
	private Long idCancelada;

	@BeforeEach
	void prepararDatosYAppender() {
		riesgoRepository.deleteAll();
		polizaRepository.deleteAll();

		Poliza vigente =
				new Poliza(
						"POL-COL-001",
						TipoPoliza.COLECTIVA,
						"Inmobiliaria Andina SAS",
						INICIO,
						12,
						new BigDecimal("1000.00"),
						"Carrera 7 # 45-10",
						"Luisa Martinez",
						"Inmobiliaria Andina SAS");

		Poliza cancelada =
				new Poliza(
						"POL-COL-002",
						TipoPoliza.COLECTIVA,
						"Arriendos del Norte Ltda",
						INICIO,
						12,
						new BigDecimal("1500.00"),
						"Diagonal 33 # 8-45",
						"Jorge Benitez",
						"Arriendos del Norte Ltda");
		cancelada.cancelar();

		idVigente = polizaRepository.save(vigente).getId();
		idCancelada = polizaRepository.save(cancelada).getId();

		logger = (Logger) LoggerFactory.getLogger(NotificacionListener.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void desengancharAppender() {
		logger.detachAppender(appender);
		appender.stop();
	}

	private boolean hayLineaDeNotificacion() {
		return appender.list.stream()
				.anyMatch(
						evento ->
								evento.getLevel() == Level.INFO
										&& evento.getFormattedMessage().contains("[NOTIFICACION]"));
	}

	@Test
	@DisplayName("CU9-a: renovar deja la linea [NOTIFICACION] con el tomador, la poliza, el canon y la vigencia")
	void renovarProduceLaNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idVigente).header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(appender.list)
				.as("el listener corre en AFTER_COMMIT y debe haberse ejecutado al volver la peticion")
				.anySatisfy(
						evento -> {
							assertThat(evento.getLevel()).isEqualTo(Level.INFO);
							assertThat(evento.getFormattedMessage())
									.contains("[NOTIFICACION]")
									.contains("Inmobiliaria Andina SAS")
									.contains("POL-COL-001")
									// 1000.00 x 1.052
									.contains("1052.00")
									.contains("2028-01-01");
						});
	}

	@Test
	@DisplayName("CU9-b: renovar una poliza CANCELADA no deja ninguna linea [NOTIFICACION]")
	void renovacionFallidaNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isConflict());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-b: renovar una poliza inexistente no deja ninguna linea [NOTIFICACION]")
	void renovacionDePolizaInexistenteNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(post("/polizas/999999/renovar").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isNotFound());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-b: cancelar una poliza no notifica al tomador")
	void cancelarNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/cancelar", idVigente).header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}
}
