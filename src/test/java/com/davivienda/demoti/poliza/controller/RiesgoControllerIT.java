package com.davivienda.demoti.poliza.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU6-a..c: POST /riesgos/{id}/cancelar. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RiesgoControllerIT {

	private static final String API_KEY = "123456";
	private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

	@Autowired private MockMvc mockMvc;
	@Autowired private PolizaRepository polizaRepository;
	@Autowired private RiesgoRepository riesgoRepository;

	@MockitoBean private CoreGateway coreGateway;

	private Long idColectiva;
	private Long idIndividual;
	private Long idPrimerRiesgoColectiva;
	private Long idSegundoRiesgoColectiva;
	private Long idRiesgoIndividual;

	@BeforeEach
	void prepararDatos() {
		riesgoRepository.deleteAll();
		polizaRepository.deleteAll();

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

		idColectiva = polizaRepository.save(colectiva).getId();
		idIndividual = polizaRepository.save(individual).getId();

		var riesgosColectiva = riesgoRepository.findByPolizaId(idColectiva);
		idPrimerRiesgoColectiva = riesgosColectiva.getFirst().getId();
		idSegundoRiesgoColectiva = riesgosColectiva.getLast().getId();
		idRiesgoIndividual = riesgoRepository.findByPolizaId(idIndividual).getFirst().getId();
	}

	@Test
	@DisplayName("CU6-a: cancelar un riesgo responde 200 con el riesgo CANCELADO")
	void cancelaRiesgo() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(idPrimerRiesgoColectiva))
				.andExpect(jsonPath("$.polizaId").value(idColectiva))
				.andExpect(jsonPath("$.estado").value("CANCELADO"));
	}

	@Test
	@DisplayName("CU6-a: notifica al CORE con el id de la POLIZA, no el del riesgo")
	void cancelarNotificaConElIdDeLaPoliza() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		verify(coreGateway, times(1)).notificarActualizacion(idColectiva);
		verify(coreGateway, times(0)).notificarActualizacion(idPrimerRiesgoColectiva);
	}

	@Test
	@DisplayName("CU6-a: el cambio queda persistido y no toca los otros riesgos")
	void cancelarPersisteSoloEseRiesgo() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(riesgoRepository.findById(idPrimerRiesgoColectiva).orElseThrow().getEstado())
				.isEqualTo(EstadoRiesgo.CANCELADO);
		assertThat(riesgoRepository.findById(idSegundoRiesgoColectiva).orElseThrow().getEstado())
				.isEqualTo(EstadoRiesgo.ACTIVO);
	}

	@Test
	@DisplayName("CU6-a / R8: cancelar un riesgo no cambia el estado de su poliza colectiva")
	void cancelarRiesgoNoCambiaLaPolizaColectiva() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		assertThat(polizaRepository.findById(idColectiva).orElseThrow().getEstado())
				.isEqualTo(EstadoPoliza.VIGENTE);
	}

	@Test
	@DisplayName("CU6-a / R8: cancelar el ULTIMO riesgo activo de una colectiva tampoco la cancela")
	void cancelarElUltimoRiesgoActivoNoCancelaLaPoliza() throws Exception {
		for (Long idRiesgo : new Long[] {idPrimerRiesgoColectiva, idSegundoRiesgoColectiva}) {
			mockMvc
					.perform(
							post("/riesgos/{id}/cancelar", idRiesgo)
									.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
					.andExpect(status().isOk());
		}

		assertThat(riesgoRepository.findByPolizaId(idColectiva))
				.hasSize(2)
				.allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.CANCELADO);
		// la poliza sigue VIGENTE aunque no le quede ningun riesgo activo
		assertThat(polizaRepository.findById(idColectiva).orElseThrow().getEstado())
				.isEqualTo(EstadoPoliza.VIGENTE);
	}

	@Test
	@DisplayName("CU6-a / R8: cancelar el unico riesgo de una INDIVIDUAL tampoco la cancela")
	void cancelarElRiesgoDeUnaIndividualNoCancelaLaPoliza() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idRiesgoIndividual)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));

		assertThat(polizaRepository.findById(idIndividual).orElseThrow().getEstado())
				.isEqualTo(EstadoPoliza.VIGENTE);
	}

	@Test
	@DisplayName("CU6-b: cancelar un riesgo ya CANCELADO responde 409 y no notifica al CORE")
	void noCancelaRiesgoYaCancelado() throws Exception {
		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk());

		mockMvc
				.perform(
						post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva)
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Regla de negocio violada"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("CANCELADO")));

		// solo la primera cancelacion notifico
		verify(coreGateway, times(1)).notificarActualizacion(idColectiva);
	}

	@Test
	@DisplayName("CU6-c: cancelar un riesgo inexistente responde 404 y no notifica al CORE")
	void noCancelaRiesgoInexistente() throws Exception {
		mockMvc
				.perform(post("/riesgos/999999/cancelar").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Recurso no encontrado"))
				.andExpect(jsonPath("$.detail").value(Matchers.containsString("999999")));

		verifyNoInteractions(coreGateway);
	}

	@Test
	@DisplayName("un id no numerico responde 400")
	void rechazaIdNoNumerico() throws Exception {
		mockMvc
				.perform(post("/riesgos/abc/cancelar").header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("sin api key responde 401")
	void exigeApiKey() throws Exception {
		mockMvc
				.perform(post("/riesgos/{id}/cancelar", idPrimerRiesgoColectiva))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(coreGateway);
	}
}
