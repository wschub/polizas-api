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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU9-a, CU9-b, CU9-d y CU9-e: notificaciones simuladas al tomador. CU9-c va aparte. */
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
	private Long idIndividual;
	private Long idRiesgoDeVigente;

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

		Poliza individual =
				new Poliza(
						"POL-IND-001",
						TipoPoliza.INDIVIDUAL,
						"Ana Gomez",
						INICIO,
						12,
						new BigDecimal("900.00"),
						"Calle 100 # 15-20",
						"Carlos Ruiz",
						"Ana Gomez");

		idVigente = polizaRepository.save(vigente).getId();
		idCancelada = polizaRepository.save(cancelada).getId();
		idIndividual = polizaRepository.save(individual).getId();
		idRiesgoDeVigente = riesgoRepository.findByPolizaId(idVigente).getFirst().getId();

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

	private long lineasQueContienen(String fragmento) {
		return appender.list.stream()
				.filter(evento -> evento.getFormattedMessage().contains("[NOTIFICACION]"))
				.filter(evento -> evento.getFormattedMessage().contains(fragmento))
				.count();
	}

	private static final String BODY_RIESGO =
			"""
			{"direccionInmueble":"Transversal 12 # 50-60, Cali","arrendatario":"Sofia Pena",\
			"arrendador":"Inmobiliaria Andina SAS"}""";

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

	// ------------------------------------------------------------------
	// CU9-d y CU9-e: notificacion al agregar un riesgo
	// ------------------------------------------------------------------

	@Test
	@DisplayName("CU9-d: agregar un riesgo deja la linea [NOTIFICACION] con el tomador, la direccion y la poliza")
	void agregarRiesgoProduceLaNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isCreated());

		assertThat(appender.list)
				.anySatisfy(
						evento -> {
							assertThat(evento.getLevel()).isEqualTo(Level.INFO);
							assertThat(evento.getFormattedMessage())
									.contains("[NOTIFICACION]")
									.contains("inclusión de un riesgo")
									.contains("Inmobiliaria Andina SAS")
									.contains("Transversal 12 # 50-60, Cali")
									.contains("POL-COL-001");
						});

		// una sola linea, y es la de inclusion de riesgo, no la de renovacion
		assertThat(lineasQueContienen("inclusión de un riesgo")).isEqualTo(1);
		assertThat(lineasQueContienen("renovación")).isZero();
	}

	@Test
	@DisplayName("CU9-e: agregar un riesgo a una poliza INDIVIDUAL (409) no deja ninguna linea [NOTIFICACION]")
	void agregarRiesgoAIndividualNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idIndividual)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isConflict());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-e: agregar un riesgo a una poliza CANCELADA (409) no deja ninguna linea [NOTIFICACION]")
	void agregarRiesgoAPolizaCanceladaNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isConflict());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-e: agregar un riesgo a una poliza inexistente (404) no deja ninguna linea [NOTIFICACION]")
	void agregarRiesgoAPolizaInexistenteNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/999999/riesgos")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isNotFound());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-e: un body invalido (400) no deja ninguna linea [NOTIFICACION]")
	void agregarRiesgoConBodyInvalidoNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}

	@Test
	@DisplayName("CU9-e: cancelar un riesgo no notifica al tomador")
	void cancelarRiesgoNoProduceNotificacion() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idRiesgoDeVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(hayLineaDeNotificacion()).isFalse();
	}
}
