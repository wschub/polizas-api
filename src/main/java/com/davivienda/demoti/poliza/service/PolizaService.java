package com.davivienda.demoti.poliza.service;

import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.dto.PolizaResponse;
import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta las consultas de polizas. Con {@code open-in-view=false} la sesion se
 * cierra al salir del service, asi que el mapeo entidad -> DTO se hace aqui
 * dentro, nunca en el controller.
 */
@Service
public class PolizaService {

	/** Orden estable: las respuestas no deben depender del orden de insercion. */
	private static final Sort ORDEN_POR_ID = Sort.by(Sort.Direction.ASC, "id");

	private final PolizaRepository polizaRepository;
	private final RiesgoRepository riesgoRepository;

	public PolizaService(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository) {
		this.polizaRepository = polizaRepository;
		this.riesgoRepository = riesgoRepository;
	}

	/** CU1: lista polizas filtrando por tipo y estado, ambos opcionales. */
	@Transactional(readOnly = true)
	public List<PolizaResponse> listar(TipoPoliza tipo, EstadoPoliza estado) {
		return polizaRepository.findAll(PolizaRepository.conFiltros(tipo, estado), ORDEN_POR_ID).stream()
				.map(PolizaResponse::de)
				.toList();
	}

	/**
	 * CU2: lista los riesgos de una poliza.
	 *
	 * @throws RecursoNoEncontradoException si la poliza no existe, para distinguir
	 *     "no hay poliza" (404) de "la poliza no tiene riesgos" (200 con lista vacia)
	 */
	@Transactional(readOnly = true)
	public List<RiesgoResponse> listarRiesgos(Long polizaId) {
		if (!polizaRepository.existsById(polizaId)) {
			throw RecursoNoEncontradoException.poliza(polizaId);
		}
		return riesgoRepository.findByPolizaId(polizaId).stream().map(RiesgoResponse::de).toList();
	}
}
