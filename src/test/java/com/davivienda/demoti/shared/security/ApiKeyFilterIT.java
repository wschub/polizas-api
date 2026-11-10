package com.davivienda.demoti.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.davivienda.demoti.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** CU7-a y CU7-b. CU7-c se completa en PolizaControllerIT, cuando existe GET /polizas. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ApiKeyFilterIT {

	@Autowired private MockMvc mockMvc;

	@Test
	@DisplayName("CU7-a: sin el header x-api-key responde 401 con cuerpo ProblemDetail")
	void rechazaPeticionSinApiKey() throws Exception {
		mockMvc
				.perform(get("/polizas"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("No autorizado"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("x-api-key")))
				.andExpect(jsonPath("$.instance").value("/polizas"));
	}

	@Test
	@DisplayName("CU7-b: con una api key incorrecta responde 401")
	void rechazaPeticionConApiKeyIncorrecta() throws Exception {
		mockMvc
				.perform(get("/polizas").header(ApiKeyFilter.HEADER_API_KEY, "clave-equivocada"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	@DisplayName("CU7-b: una api key con el prefijo correcto tampoco pasa")
	void rechazaPeticionConApiKeyParcial() throws Exception {
		mockMvc
				.perform(get("/polizas").header(ApiKeyFilter.HEADER_API_KEY, "123"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("el header vacio se trata como invalido")
	void rechazaPeticionConApiKeyVacia() throws Exception {
		mockMvc
				.perform(get("/polizas").header(ApiKeyFilter.HEADER_API_KEY, ""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("/actuator/health es la unica ruta que no exige api key")
	void permiteActuatorHealthSinApiKey() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("el resto de actuator si exige api key")
	void exigeApiKeyEnElRestoDeActuator() throws Exception {
		mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
	}
}
