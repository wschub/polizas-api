package com.davivienda.demoti.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * CU8-c: un fallo del CORE no se propaga.
 *
 * <p>Unitario: no levanta Spring. Se engancha un appender a el logger de
 * {@link CoreGatewayHttp} para comprobar que el fallo queda registrado y no solo
 * silenciado.
 */
class CoreGatewayHttpTest {

	private static final Long POLIZA_ID = 555L;
	private static final String API_KEY = "123456";

	private Logger logger;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void engancharAppender() {
		logger = (Logger) LoggerFactory.getLogger(CoreGatewayHttp.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void desengancharAppender() {
		logger.detachAppender(appender);
		appender.stop();
	}

	@Test
	@DisplayName("CU8-c: con el CORE en un puerto cerrado no lanza excepcion y registra el error")
	void noPropagaFalloDeConexion() {
		// Puerto 1: reservado y sin nada escuchando, la conexion se rechaza de inmediato.
		CoreGatewayHttp gateway = new CoreGatewayHttp("http://localhost:1", API_KEY);

		assertThatNoException().isThrownBy(() -> gateway.notificarActualizacion(POLIZA_ID));

		assertThat(appender.list)
				.as("el fallo del CORE debe quedar registrado como ERROR")
				.anySatisfy(
						evento -> {
							assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
							assertThat(evento.getFormattedMessage())
									.contains("[CORE]")
									.contains(POLIZA_ID.toString());
						});
	}

	@Test
	@DisplayName("CU8-c: con un host que no resuelve tampoco lanza excepcion y registra el error")
	void noPropagaFalloDeResolucionDeNombre() {
		// El TLD .invalid esta reservado por la RFC 2606: nunca resuelve.
		CoreGatewayHttp gateway = new CoreGatewayHttp("http://core-que-no-existe.invalid", API_KEY);

		assertThatNoException().isThrownBy(() -> gateway.notificarActualizacion(POLIZA_ID));

		assertThat(appender.list)
				.anySatisfy(
						evento -> {
							assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
							assertThat(evento.getFormattedMessage()).contains("[CORE]");
						});
	}
}
