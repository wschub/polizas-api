package com.davivienda.demoti.core;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davivienda.demoti.TestcontainersConfiguration;
import com.davivienda.demoti.shared.security.ApiKeyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** CU8-a y CU8-b: el mock del CORE. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CoreMockControllerIT {

	private static final String API_KEY = "123456";

	@Autowired private MockMvc mockMvc;

	/**
	 * MockMvc no abre un puerto real, asi que el adaptador HTTP no podria llamarse a
	 * si mismo. Se reemplaza por un mock aunque este test no lo invoque.
	 */
	@MockitoBean private CoreGateway coreGateway;

	@Test
	@DisplayName("CU8-a: con un body valido responde 202 y recibido=true")
	void aceptaEventoValido() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.recibido").value(true));
	}

	@Test
	@DisplayName("CU8-b: sin el campo evento responde 400")
	void rechazaEventoSinEvento() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"polizaId\":555}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("evento")));
	}

	@Test
	@DisplayName("CU8-b: con el campo evento en blanco responde 400")
	void rechazaEventoEnBlanco() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"evento\":\"   \",\"polizaId\":555}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("CU8-b: sin polizaId responde 400")
	void rechazaEventoSinPolizaId() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"evento\":\"ACTUALIZACION\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("polizaId")));
	}

	@Test
	@DisplayName("CU8-b: con un body que no es JSON valido responde 400")
	void rechazaBodyIlegible() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.header(ApiKeyFilter.HEADER_API_KEY, API_KEY)
								.contentType(MediaType.APPLICATION_JSON)
								.content("{esto no es json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("el mock tambien exige api key")
	void exigeApiKey() throws Exception {
		mockMvc
				.perform(
						post("/core-mock/evento")
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"evento\":\"ACTUALIZACION\",\"polizaId\":555}"))
				.andExpect(status().isUnauthorized());
	}
}
