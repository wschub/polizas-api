package com.davivienda.demoti;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Levanta un PostgreSQL real para los tests de integracion. {@code @ServiceConnection} registra
 * las propiedades de datasource del contenedor, por lo que las clases de test no necesitan
 * declarar url, usuario ni clave.
 *
 * <p>Requiere Docker encendido. El contenedor usa un puerto aleatorio del host, asi que no choca
 * con el PostgreSQL de desarrollo del 5433 ni con instalaciones locales.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:16-alpine");
	}
}
