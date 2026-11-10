package com.davivienda.demoti.poliza.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davivienda.demoti.TestcontainersConfiguration;
import com.davivienda.demoti.core.CoreGateway;
import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.EstadoRiesgo;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.security.ApiKeyFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU1-a..e, CU2-a..b, CU3-a..g, CU4-a..c y CU5-a..e. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PolizaControllerIT {

	private static final String API_KEY = "123456";
	private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

	@Autowired private MockMvc mockMvc;
	@Autowired private PolizaRepository polizaRepository;
	@Autowired private RiesgoRepository riesgoRepository;

	/** MockMvc no abre un puerto real, asi que el adaptador HTTP no puede llamarse a si mismo. */
	@MockitoBean private CoreGateway coreGateway;

	private Long idIndividualVigente;
	private Long idColectivaVigente;
	private Long idColectivaCancelada;

	@BeforeEach
	void prepararDatos() {
		riesgoRepository.deleteAll();
		polizaRepository.deleteAll();

		Poliza individual =
				new Poliza(
						"POL-IND-001",
						TipoPoliza.INDIVIDUAL,
						"Ana Gomez",
						INICIO,
						12,
						new BigDecimal("1000.00"),
						"Calle 100 # 15-20",
						"Carlos Ruiz",
						"Ana Gomez");

		Poliza colectiva =
				new Poliza(
						"POL-COL-001",
						TipoPoliza.COLECTIVA,
						"Inmobiliaria Andina SAS",
						INICIO,
						24,
						new BigDecimal("2000.00"),
						"Carrera 7 # 45-10",
						"Luisa Martinez",
						"Inmobiliaria Andina SAS");
		colectiva.agregarRiesgo("Calle 85 # 12-30", "Pedro Alvarez", "Inmobiliaria Andina SAS");
		colectiva.agregarRiesgo("Avenida 68 # 24-15", "Marta Quintero", "Inmobiliaria Andina SAS");

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

		idIndividualVigente = polizaRepository.save(individual).getId();
		idColectivaVigente = polizaRepository.save(colectiva).getId();
		idColectivaCancelada = polizaRepository.save(cancelada).getId();
	}

	@Test
	@DisplayName("CU1-a: sin filtros devuelve todas las polizas, ordenadas por id")
	void listaTodasSinFiltros() throws Exception {
		mockMvc
				.perform(get("/polizas").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(3)))
				.andExpect(jsonPath("$[0].id").value(idIndividualVigente))
				.andExpect(jsonPath("$[1].id").value(idColectivaVigente))
				.andExpect(jsonPath("$[2].id").value(idColectivaCancelada))
				.andExpect(jsonPath("$[0].numero").value("POL-IND-001"))
				.andExpect(jsonPath("$[0].tipo").value("INDIVIDUAL"))
				.andExpect(jsonPath("$[0].estado").value("VIGENTE"))
				.andExpect(jsonPath("$[0].tomador").value("Ana Gomez"))
				.andExpect(jsonPath("$[0].mesesVigencia").value(12))
				.andExpect(jsonPath("$[0].canonMensual").value(1000.00))
				.andExpect(jsonPath("$[0].prima").value(12000.00))
				.andExpect(jsonPath("$[0].fechaInicioVigencia").value("2026-01-01"))
				.andExpect(jsonPath("$[0].fechaFinVigencia").value("2027-01-01"))
				// la lista de polizas no expone riesgos
				.andExpect(jsonPath("$[0].riesgos").doesNotExist());
	}

	@Test
	@DisplayName("CU1-b: filtrando por tipo devuelve solo ese tipo")
	void listaFiltrandoPorTipo() throws Exception {
		mockMvc
				.perform(
						get("/polizas").param("tipo", "COLECTIVA").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[*].tipo", Matchers.everyItem(Matchers.is("COLECTIVA"))))
				.andExpect(jsonPath("$[0].id").value(idColectivaVigente))
				.andExpect(jsonPath("$[1].id").value(idColectivaCancelada));
	}

	@Test
	@DisplayName("CU1-c: filtrando por estado devuelve solo ese estado")
	void listaFiltrandoPorEstado() throws Exception {
		mockMvc
				.perform(
						get("/polizas").param("estado", "VIGENTE").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(2)))
				.andExpect(jsonPath("$[*].estado", Matchers.everyItem(Matchers.is("VIGENTE"))))
				.andExpect(jsonPath("$[0].id").value(idIndividualVigente))
				.andExpect(jsonPath("$[1].id").value(idColectivaVigente));
	}

	@Test
	@DisplayName("CU1-d: combinando tipo y estado devuelve la interseccion")
	void listaFiltrandoPorTipoYEstado() throws Exception {
		mockMvc
				.perform(
						get("/polizas")
								.param("tipo", "COLECTIVA")
								.param("estado", "CANCELADA")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(idColectivaCancelada))
				.andExpect(jsonPath("$[0].tipo").value("COLECTIVA"))
				.andExpect(jsonPath("$[0].estado").value("CANCELADA"));
	}

	@Test
	@DisplayName("CU1-d: una combinacion sin coincidencias devuelve 200 con lista vacia")
	void listaCombinacionSinCoincidencias() throws Exception {
		mockMvc
				.perform(
						get("/polizas")
								.param("tipo", "INDIVIDUAL")
								.param("estado", "CANCELADA")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(0)));
	}

	@Test
	@DisplayName("CU1-e: un tipo con valor invalido responde 400 con ProblemDetail")
	void rechazaTipoInvalido() throws Exception {
		mockMvc
				.perform(
						get("/polizas").param("tipo", "NO_EXISTE").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Peticion invalida"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("NO_EXISTE")))
				// el handler lista los valores aceptados
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("COLECTIVA")))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("INDIVIDUAL")));
	}

	@Test
	@DisplayName("CU1-e: un estado con valor invalido responde 400")
	void rechazaEstadoInvalido() throws Exception {
		mockMvc
				.perform(
						get("/polizas").param("estado", "VENCIDA").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("VENCIDA")));
	}

	@Test
	@DisplayName("CU2-a: devuelve los riesgos de una poliza existente, ordenados por id")
	void listaRiesgosDePolizaExistente() throws Exception {
		mockMvc
				.perform(
						get("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(3)))
				.andExpect(jsonPath("$[*].polizaId", Matchers.everyItem(Matchers.is(idColectivaVigente.intValue()))))
				.andExpect(jsonPath("$[0].direccionInmueble").value("Carrera 7 # 45-10"))
				.andExpect(jsonPath("$[0].arrendatario").value("Luisa Martinez"))
				.andExpect(jsonPath("$[0].arrendador").value("Inmobiliaria Andina SAS"))
				.andExpect(jsonPath("$[0].estado").value("ACTIVO"))
				.andExpect(jsonPath("$[1].direccionInmueble").value("Calle 85 # 12-30"))
				.andExpect(jsonPath("$[2].direccionInmueble").value("Avenida 68 # 24-15"));
	}

	@Test
	@DisplayName("CU2-a: una poliza con un solo riesgo devuelve ese riesgo")
	void listaRiesgosDePolizaIndividual() throws Exception {
		mockMvc
				.perform(
						get("/polizas/{id}/riesgos", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].estado").value("ACTIVO"));
	}

	@Test
	@DisplayName("CU2-a: los riesgos de una poliza cancelada salen CANCELADO")
	void listaRiesgosDePolizaCancelada() throws Exception {
		mockMvc
				.perform(
						get("/polizas/{id}/riesgos", idColectivaCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].estado").value("CANCELADO"));
	}

	@Test
	@DisplayName("CU2-b: los riesgos de una poliza inexistente responden 404")
	void rechazaRiesgosDePolizaInexistente() throws Exception {
		mockMvc
				.perform(get("/polizas/999999/riesgos").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Recurso no encontrado"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("999999")));
	}

	@Test
	@DisplayName("un id no numerico responde 400")
	void rechazaIdNoNumerico() throws Exception {
		mockMvc
				.perform(get("/polizas/abc/riesgos").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	// ------------------------------------------------------------------
	// CU3: renovar
	// ------------------------------------------------------------------

	@Test
	@DisplayName("CU3-a: renovar con el IPC por defecto recalcula canon y prima, corre la vigencia y deja RENOVADA")
	void renuevaConIpcPorDefecto() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idIndividualVigente))
				.andExpect(jsonPath("$.estado").value("RENOVADA"))
				// polizas.ipc=5.2 -> 1000.00 x 1.052 = 1052.00
				.andExpect(jsonPath("$.canonMensual").value(1052.00))
				// 1052.00 x 12 meses
				.andExpect(jsonPath("$.prima").value(12624.00))
				// la nueva vigencia arranca donde terminaba la anterior
				.andExpect(jsonPath("$.fechaInicioVigencia").value("2027-01-01"))
				.andExpect(jsonPath("$.fechaFinVigencia").value("2028-01-01"));

		verify(coreGateway, times(1)).notificarActualizacion(idIndividualVigente);
	}

	@Test
	@DisplayName("CU3-a: el cambio queda persistido en la base")
	void renovarPersisteElCambio() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		Poliza enBase = polizaRepository.findById(idIndividualVigente).orElseThrow();
		assertThat(enBase.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
		assertThat(enBase.getCanonMensual()).isEqualByComparingTo("1052.00");
		assertThat(enBase.getPrima()).isEqualByComparingTo("12624.00");
		assertThat(enBase.getFechaInicioVigencia()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(enBase.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
	}

	@Test
	@DisplayName("CU3-b: el ipc del body sobrescribe el valor por defecto")
	void renuevaConIpcDelBody() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":10}"))
				.andExpect(status().isOk())
				// con 10 del body: 1000.00 x 1.10 = 1100.00, no 1052.00
				.andExpect(jsonPath("$.canonMensual").value(1100.00))
				.andExpect(jsonPath("$.prima").value(13200.00))
				.andExpect(jsonPath("$.estado").value("RENOVADA"));
	}

	@Test
	@DisplayName("CU3-b: un body sin el campo ipc usa el valor por defecto")
	void renuevaConBodyVacio() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canonMensual").value(1052.00));
	}

	@Test
	@DisplayName("CU3-c: renovar una poliza CANCELADA responde 409 y no notifica al CORE")
	void noRenuevaPolizaCancelada() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idColectivaCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Regla de negocio violada"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("CANCELADA")));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU3-d / R4: renovar dos veces aplica el segundo incremento sobre el canon ya renovado")
	void renuevaDosVecesSeguidas() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canonMensual").value(1052.00));

		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				// 1052.00 x 1.052 = 1106.704 -> 1106.70, no 1104.00 (que seria 2 x 5.2 % del original)
				.andExpect(jsonPath("$.canonMensual").value(1106.70))
				.andExpect(jsonPath("$.prima").value(13280.40))
				// la vigencia se corre otro periodo
				.andExpect(jsonPath("$.fechaInicioVigencia").value("2028-01-01"))
				.andExpect(jsonPath("$.fechaFinVigencia").value("2029-01-01"))
				.andExpect(jsonPath("$.estado").value("RENOVADA"));

		verify(coreGateway, times(2)).notificarActualizacion(idIndividualVigente);
	}

	@Test
	@DisplayName("CU3-e: el redondeo es a escala 2 con HALF_UP")
	void renuevaRedondeandoHalfUp() throws Exception {
		// 10.10 x 1.05 = 10.605 exacto: HALF_UP da 10.61 y HALF_EVEN daria 10.60
		Poliza centavos =
				new Poliza(
						"POL-CENT-001",
						TipoPoliza.COLECTIVA,
						"Pruebas SAS",
						INICIO,
						12,
						new BigDecimal("10.10"),
						"Calle 1 # 1-1",
						"Arrendatario",
						"Arrendador");
		Long id = polizaRepository.save(centavos).getId();

		mockMvc
				.perform(
						post("/polizas/{id}/renovar", id)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":5.0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canonMensual").value(10.61))
				.andExpect(jsonPath("$.prima").value(127.32));
	}

	@Test
	@DisplayName("CU3-f: un ipc negativo responde 400")
	void rechazaIpcNegativo() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("ipc")));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU3-f: un ipc mayor que 100 responde 400")
	void rechazaIpcMayorQueCien() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":100.01}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU3-f: los extremos 0 y 100 si son validos")
	void aceptaLosExtremosDelRango() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":0}"))
				.andExpect(status().isOk())
				// con ipc 0 el canon no cambia, pero la vigencia si se corre
				.andExpect(jsonPath("$.canonMensual").value(1000.00))
				.andExpect(jsonPath("$.estado").value("RENOVADA"));

		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\":100}"))
				.andExpect(status().isOk())
				// 2000.00 x 2 = 4000.00
				.andExpect(jsonPath("$.canonMensual").value(4000.00));
	}

	@Test
	@DisplayName("CU3-f: un JSON malformado responde 400")
	void rechazaJsonMalformado() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"ipc\": }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU3-g: renovar una poliza inexistente responde 404 y no notifica al CORE")
	void noRenuevaPolizaInexistente() throws Exception {
		mockMvc
				.perform(post("/polizas/999999/renovar").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("999999")));

		verifyNoInteractions(coreGateway);
	}

	// ------------------------------------------------------------------
	// CU4: cancelar
	// ------------------------------------------------------------------

	@Test
	@DisplayName("CU4-a: cancelar deja la poliza CANCELADA y notifica al CORE una vez")
	void cancelaPoliza() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/cancelar", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idColectivaVigente))
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		verify(coreGateway, times(1)).notificarActualizacion(idColectivaVigente);
	}

	@Test
	@DisplayName("CU4-a: cancelar persiste los 3 riesgos de la poliza como CANCELADO")
	void cancelarPersisteLosRiesgosComoCancelados() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/cancelar", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		// se lee de la base, no de la respuesta
		assertThat(polizaRepository.findById(idColectivaVigente).orElseThrow().getEstado())
				.isEqualTo(EstadoPoliza.CANCELADA);
		assertThat(riesgoRepository.findByPolizaId(idColectivaVigente))
				.hasSize(3)
				.allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.CANCELADO);
	}

	@Test
	@DisplayName("CU4-a: cancelar no toca los riesgos de otras polizas")
	void cancelarNoAfectaOtrasPolizas() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/cancelar", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(riesgoRepository.findByPolizaId(idIndividualVigente))
				.allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.ACTIVO);
		assertThat(polizaRepository.findById(idIndividualVigente).orElseThrow().getEstado())
				.isEqualTo(EstadoPoliza.VIGENTE);
	}

	@Test
	@DisplayName("CU4-b: cancelar una poliza ya CANCELADA responde 409 y no notifica al CORE")
	void noCancelaPolizaYaCancelada() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/cancelar", idColectivaCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("CANCELADA")));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU4-c: cancelar una poliza inexistente responde 404 y no notifica al CORE")
	void noCancelaPolizaInexistente() throws Exception {
		mockMvc
				.perform(post("/polizas/999999/cancelar").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));

		verifyNoInteractions(coreGateway);
	}

	// ------------------------------------------------------------------
	// CU5: agregar riesgo (el endpoint cuelga de /polizas)
	// ------------------------------------------------------------------

	private static final String BODY_RIESGO =
			"""
			{"direccionInmueble":"Calle 85 # 12-30","arrendatario":"Pedro Alvarez",			"arrendador":"Inmobiliaria Andina SAS"}""";

	@Test
	@DisplayName("CU5-a: agregar un riesgo a una colectiva responde 201 con el id ya generado")
	void agregaRiesgoAColectiva() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isCreated())
				// el id debe venir poblado, no null
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.polizaId").value(idColectivaVigente))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.direccionInmueble").value("Calle 85 # 12-30"))
				.andExpect(jsonPath("$.arrendatario").value("Pedro Alvarez"))
				.andExpect(jsonPath("$.arrendador").value("Inmobiliaria Andina SAS"));

		verify(coreGateway, times(1)).notificarActualizacion(idColectivaVigente);
	}

	@Test
	@DisplayName("CU5-a: el riesgo queda en BD, ACTIVO y asociado a la poliza correcta")
	void agregarRiesgoPersisteEnBase() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isCreated());

		// la colectiva tenia 3 riesgos; ahora 4, y el nuevo es el ultimo por id
		var riesgos = riesgoRepository.findByPolizaId(idColectivaVigente);
		assertThat(riesgos).hasSize(4);
		var nuevo = riesgos.getLast();
		assertThat(nuevo.getId()).isNotNull();
		assertThat(nuevo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(nuevo.getPoliza().getId()).isEqualTo(idColectivaVigente);
		assertThat(nuevo.getDireccionInmueble()).isEqualTo("Calle 85 # 12-30");
	}

	@Test
	@DisplayName("CU5-b: agregar un riesgo a una INDIVIDUAL responde 409 y no crea nada en BD")
	void noAgregaRiesgoAIndividual() throws Exception {
		long antes = riesgoRepository.count();

		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idIndividualVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Regla de negocio violada"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("COLECTIVA")));

		assertThat(riesgoRepository.count()).isEqualTo(antes);
		assertThat(riesgoRepository.findByPolizaId(idIndividualVigente)).hasSize(1);
		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU5-c: agregar un riesgo a una poliza CANCELADA responde 409 y no crea nada en BD")
	void noAgregaRiesgoAPolizaCancelada() throws Exception {
		long antes = riesgoRepository.count();

		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaCancelada)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("CANCELADA")));

		assertThat(riesgoRepository.count()).isEqualTo(antes);
		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU5-d: un campo vacio responde 400 nombrando el campo")
	void rechazaCampoVacio() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										"{\"direccionInmueble\":\"   \",\"arrendatario\":\"Pedro\",\"arrendador\":\"Andina\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("direccionInmueble")));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU5-d: si faltan los tres campos, el 400 los nombra todos")
	void rechazaBodyVacio() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("direccionInmueble")))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("arrendatario")))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("arrendador")));
	}

	@Test
	@DisplayName("CU5-d: sin body responde 400, porque aqui el body es obligatorio")
	void rechazaPeticionSinBody() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("CU5-d: un JSON malformado responde 400")
	void rechazaJsonMalformadoAlCrearRiesgo() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/riesgos", idColectivaVigente)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"direccionInmueble\":}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("CU5-e: agregar un riesgo a una poliza inexistente responde 404")
	void noAgregaRiesgoAPolizaInexistente() throws Exception {
		mockMvc
				.perform(
						post("/polizas/999999/riesgos")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content(BODY_RIESGO))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("999999")));

		verifyNoInteractions(coreGateway);
	}
}
