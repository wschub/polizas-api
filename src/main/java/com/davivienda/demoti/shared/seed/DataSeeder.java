package com.davivienda.demoti.shared.seed;

import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga datos de ejemplo al arrancar, solo si la base esta vacia, para que la
 * API sea explorable sin pasos manuales.
 *
 * <p>No corre en tests: cada test de integracion construye y limpia sus propios
 * datos, y un seeder compartido los volveria dependientes entre si.
 */
@Component
@Profile("!test")
@Slf4j
public class DataSeeder implements CommandLineRunner {

	private final PolizaRepository polizaRepository;

	public DataSeeder(PolizaRepository polizaRepository) {
		this.polizaRepository = polizaRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		if (polizaRepository.count() > 0) {
			log.info("[SEED] La base ya tiene polizas, no se insertan datos de ejemplo");
			return;
		}

		LocalDate inicio = LocalDate.of(2026, 1, 1);

		Poliza individual = new Poliza(
				"POL-IND-001",
				TipoPoliza.INDIVIDUAL,
				"Ana Gomez",
				inicio,
				12,
				new BigDecimal("1800000.00"),
				"Calle 100 # 15-20, Bogota",
				"Carlos Ruiz",
				"Ana Gomez");

		Poliza colectiva = new Poliza(
				"POL-COL-001",
				TipoPoliza.COLECTIVA,
				"Inmobiliaria Andina SAS",
				inicio,
				24,
				new BigDecimal("2500000.00"),
				"Carrera 7 # 45-10, Bogota",
				"Luisa Martinez",
				"Inmobiliaria Andina SAS");
		colectiva.agregarRiesgo(
				"Calle 85 # 12-30 apto 402, Bogota", "Pedro Alvarez", "Inmobiliaria Andina SAS");
		colectiva.agregarRiesgo(
				"Avenida 68 # 24-15 local 3, Bogota", "Marta Quintero", "Inmobiliaria Andina SAS");

		Poliza colectivaCancelada = new Poliza(
				"POL-COL-002",
				TipoPoliza.COLECTIVA,
				"Arriendos del Norte Ltda",
				inicio,
				12,
				new BigDecimal("1200000.00"),
				"Diagonal 33 # 8-45, Medellin",
				"Jorge Benitez",
				"Arriendos del Norte Ltda");
		// Cancelar la poliza arrastra su unico riesgo a CANCELADO (R5).
		colectivaCancelada.cancelar();

		polizaRepository.saveAll(java.util.List.of(individual, colectiva, colectivaCancelada));

		log.info(
				"[SEED] Datos de ejemplo insertados: {} polizas ({} individual vigente con 1 riesgo, "
						+ "{} colectiva vigente con 3 riesgos, {} colectiva cancelada con 1 riesgo cancelado)",
				3,
				individual.getNumero(),
				colectiva.getNumero(),
				colectivaCancelada.getNumero());
	}
}
