package com.davivienda.demoti.poliza.repository;

import com.davivienda.demoti.poliza.domain.Riesgo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

	/**
	 * Riesgos de una poliza, ordenados por id para que la respuesta no dependa del
	 * orden de insercion.
	 *
	 * <p>El {@code join fetch} trae la poliza en la misma consulta: con
	 * {@code open-in-view=false} el mapeo a DTO ocurre dentro del service y
	 * {@code RiesgoResponse} necesita el id de la poliza. Sin el fetch, la relacion
	 * lazy se resolveria por cada riesgo (N+1); con el, es una sola consulta y
	 * todos los riesgos comparten la misma poliza ya cargada.
	 */
	@Query("select r from Riesgo r join fetch r.poliza where r.poliza.id = :polizaId order by r.id")
	List<Riesgo> findByPolizaId(@Param("polizaId") Long polizaId);
}
