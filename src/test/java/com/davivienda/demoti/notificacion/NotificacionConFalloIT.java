package com.davivienda.demoti.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CU9-c: un fallo al notificar no afecta a la renovacion.
 *
 * <p>Va en su propia clase porque registra un listener adicional que falla, y eso
 * exige un contexto de Spring distinto al del resto de los tests.
 *
 * <p>El listener de produccion ya atrapa sus excepciones, asi que aqui se registra
 * uno que falla sin red de seguridad: comprueba la garantia de la plataforma, no
 * la del try/catch. Spring no propaga las excepciones de un listener
 * {@code AFTER_COMMIT}, de modo que la operacion queda confirmada igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, NotificacionConFalloIT.ListenerQueFalla.class})
@ActiveProfiles("test")
class NotificacionConFalloIT {

	private static final String API_KEY = "123456";

	/** Listener adicional que siempre falla, sin atrapar la excepcion. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ListenerQueFalla {

		@Bean
		ListenerQueSiempreFalla listenerQueSiempreFalla() {
			return new ListenerQueSiempreFalla();
		}

		static class ListenerQueSiempreFalla {
			@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
			public void alRenovarPoliza(PolizaRenovadaEvent evento) {
				throw new IllegalStateException("fallo simulado del canal de notificacion");
			}
		}
	}

	@Autowired private MockMvc mockMvc;
	@Autowired private PolizaRepository polizaRepository;
	@Autowired private RiesgoRepository riesgoRepository;

	@MockitoBean private CoreGateway coreGateway;

	private Long idVigente;

	@BeforeEach
	void prepararDatos() {
		riesgoRepository.deleteAll();
		polizaRepository.deleteAll();

		Poliza vigente =
				new Poliza(
						"POL-COL-001",
						TipoPoliza.COLECTIVA,
						"Inmobiliaria Andina SAS",
						LocalDate.of(2026, 1, 1),
						12,
						new BigDecimal("1000.00"),
						"Carrera 7 # 45-10",
						"Luisa Martinez",
						"Inmobiliaria Andina SAS");
		idVigente = polizaRepository.save(vigente).getId();
	}

	@Test
	@DisplayName("CU9-c: si la notificacion falla, la renovacion sigue respondiendo 200 y queda RENOVADA en BD")
	void unFalloAlNotificarNoAfectaLaRenovacion() throws Exception {
		mockMvc
				.perform(
						post("/polizas/{id}/renovar", idVigente).header(ApiKeyFilter.HEADER_API_KEY, API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RENOVADA"))
				.andExpect(jsonPath("$.canonMensual").value(1052.00));

		// lo que de verdad importa: el cambio quedo confirmado en la base
		Poliza enBase = polizaRepository.findById(idVigente).orElseThrow();
		assertThat(enBase.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
		assertThat(enBase.getCanonMensual()).isEqualByComparingTo("1052.00");
		assertThat(enBase.getFechaFinVigencia()).isEqualTo(LocalDate.of(2028, 1, 1));
	}
}
