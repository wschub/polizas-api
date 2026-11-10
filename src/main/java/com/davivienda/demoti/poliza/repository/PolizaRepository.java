package com.davivienda.demoti.poliza.repository;

import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PolizaRepository
		extends JpaRepository<Poliza, Long>, JpaSpecificationExecutor<Poliza> {

	/**
	 * Filtro por tipo y estado, ambos opcionales y combinables.
	 *
	 * <p>Se arma con Specification y no con un JPQL del estilo
	 * {@code (:tipo is null or p.tipo = :tipo)} porque, al enviar el parametro nulo,
	 * PostgreSQL no puede inferir el tipo del enum y la consulta falla con
	 * "could not determine data type of parameter". Aqui el predicado simplemente
	 * no se agrega cuando el filtro no viene, asi que nunca viaja un nulo.
	 *
	 * <p>Vive en el repositorio, junto a la consulta que lo usa, para no mezclar la
	 * API de criteria dentro del service.
	 */
	static Specification<Poliza> conFiltros(TipoPoliza tipo, EstadoPoliza estado) {
		List<Specification<Poliza>> predicados = new ArrayList<>();
		if (tipo != null) {
			predicados.add((raiz, consulta, cb) -> cb.equal(raiz.get("tipo"), tipo));
		}
		if (estado != null) {
			predicados.add((raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado));
		}
		return predicados.isEmpty() ? Specification.unrestricted() : Specification.allOf(predicados);
	}
}
