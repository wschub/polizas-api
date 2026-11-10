package com.davivienda.demoti.poliza.repository;

import com.davivienda.demoti.poliza.domain.Riesgo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

	/**
	 * Riesgos de una poliza. Lleva {@code @Query} en lugar de quedarse con la
	 * consulta derivada del nombre solo para fijar el orden por id, y que las
	 * respuestas de la API sean deterministas.
	 */
	@Query("select r from Riesgo r where r.poliza.id = :polizaId order by r.id")
	List<Riesgo> findByPolizaId(@Param("polizaId") Long polizaId);
}
