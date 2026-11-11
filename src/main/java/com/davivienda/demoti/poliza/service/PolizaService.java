package com.davivienda.demoti.poliza.service;

import com.davivienda.demoti.core.CoreGateway;
import com.davivienda.demoti.notificacion.PolizaRenovadaEvent;
import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.dto.PolizaResponse;
import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta las operaciones sobre polizas. No contiene reglas de negocio: carga la
 * entidad, le pide que se renueve o se cancele, guarda y notifica al CORE.
 *
 * <p>Con {@code open-in-view=false} la sesion se cierra al salir del service, asi
 * que el mapeo entidad -> DTO se hace aqui dentro, nunca en el controller.
 */
@Service
public class PolizaService {

	/** Orden estable: las respuestas no deben depender del orden de insercion. */
	private static final Sort ORDEN_POR_ID = Sort.by(Sort.Direction.ASC, "id");

	private final PolizaRepository polizaRepository;
	private final RiesgoRepository riesgoRepository;
	private final CoreGateway coreGateway;
	private final ApplicationEventPublisher publicadorDeEventos;
	private final BigDecimal ipcPorDefecto;

	public PolizaService(
			PolizaRepository polizaRepository,
			RiesgoRepository riesgoRepository,
			CoreGateway coreGateway,
			ApplicationEventPublisher publicadorDeEventos,
			@Value("${polizas.ipc}") BigDecimal ipcPorDefecto) {
		this.polizaRepository = polizaRepository;
		this.riesgoRepository = riesgoRepository;
		this.coreGateway = coreGateway;
		this.publicadorDeEventos = publicadorDeEventos;
		this.ipcPorDefecto = ipcPorDefecto;
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

	/**
	 * CU3: renueva la poliza aplicando el IPC.
	 *
	 * @param ipcOpcional porcentaje enviado en el body; si es nulo se usa
	 *     {@code polizas.ipc}
	 * @throws RecursoNoEncontradoException si la poliza no existe (404)
	 * @throws com.davivienda.demoti.shared.exception.ReglaNegocioException si esta
	 *     CANCELADA (409); en ese caso no se notifica al CORE ni se publica el evento
	 */
	@Transactional
	public PolizaResponse renovar(Long id, BigDecimal ipcOpcional) {
		Poliza poliza = cargar(id);
		poliza.renovar(ipcOpcional != null ? ipcOpcional : ipcPorDefecto);
		Poliza guardada = polizaRepository.save(poliza);

		// El evento se publica aqui, pero su listener escucha en AFTER_COMMIT: si la
		// transaccion no se confirma, al tomador no se le avisa de un cambio que no
		// ocurrio. Si la regla de negocio hubiera fallado, la excepcion ya salio y no
		// se publica nada.
		publicadorDeEventos.publishEvent(PolizaRenovadaEvent.de(guardada));

		coreGateway.notificarActualizacion(guardada.getId());
		return PolizaResponse.de(guardada);
	}

	/**
	 * CU4: cancela la poliza y, con ella, todos sus riesgos ACTIVO.
	 *
	 * @throws RecursoNoEncontradoException si la poliza no existe (404)
	 * @throws com.davivienda.demoti.shared.exception.ReglaNegocioException si ya
	 *     estaba CANCELADA (409); en ese caso no se notifica al CORE
	 */
	@Transactional
	public PolizaResponse cancelar(Long id) {
		Poliza poliza = cargar(id);
		// Recorre la coleccion lazy de riesgos: estamos dentro de la transaccion, asi
		// que se carga con una sola consulta adicional para toda la coleccion.
		poliza.cancelar();
		return guardarYNotificar(poliza);
	}

	private Poliza cargar(Long id) {
		return polizaRepository
				.findById(id)
				.orElseThrow(() -> RecursoNoEncontradoException.poliza(id));
	}

	/**
	 * Guarda y, como ultima accion, avisa al CORE.
	 *
	 * <p>El orden importa: si la regla de negocio hubiera fallado, la excepcion ya
	 * habria salido y el CORE no se toca. Y al notificar despues de guardar, un
	 * fallo del CORE no revierte la operacion, porque el adaptador se come la
	 * excepcion.
	 */
	private PolizaResponse guardarYNotificar(Poliza poliza) {
		Poliza guardada = polizaRepository.save(poliza);
		coreGateway.notificarActualizacion(guardada.getId());
		return PolizaResponse.de(guardada);
	}
}
