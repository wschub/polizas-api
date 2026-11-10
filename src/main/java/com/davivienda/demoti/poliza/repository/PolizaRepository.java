package com.davivienda.demoti.poliza.repository;

import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolizaRepository extends JpaRepository<Poliza, Long> {

	/**
	 * Lista pólizas filtrando por tipo y estado, ambos opcionales y combinables:
	 * un parametro nulo no restringe. Orden estable por id para que las
	 * respuestas sean deterministas.
	 */
	@Query("""
			select p from Poliza p
			where (:tipo is null or p.tipo = :tipo)
			  and (:estado is null or p.estado = :estado)
			order by p.id
			""")
	List<Poliza> buscar(@Param("tipo") TipoPoliza tipo, @Param("estado") EstadoPoliza estado);
}
