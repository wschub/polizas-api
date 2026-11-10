package com.davivienda.demoti.poliza.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU1-a..e y CU2-a..b. */
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
}
