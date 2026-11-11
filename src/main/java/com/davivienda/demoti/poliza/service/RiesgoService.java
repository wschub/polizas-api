package com.davivienda.demoti.poliza.service;

import com.davivienda.demoti.core.CoreGateway;
import com.davivienda.demoti.notificacion.RiesgoAgregadoEvent;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.Riesgo;
import com.davivienda.demoti.poliza.dto.CrearRiesgoRequest;
import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.repository.PolizaRepository;
import com.davivienda.demoti.poliza.repository.RiesgoRepository;
import com.davivienda.demoti.shared.exception.RecursoNoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta las operaciones sobre riesgos. Las reglas viven en las entidades:
 * {@link Poliza#agregarRiesgo} decide si admite el riesgo y {@link Riesgo#cancelar}
 * si puede cancelarse.
 *
 * <p>El CORE se notifica siempre con el id de la <em>poliza</em>, nunca con el del
 * riesgo: lo que cambia desde el punto de vista del CORE es la poliza.
 */
@Service
public class RiesgoService {

	private final PolizaRepository polizaRepository;
	private final RiesgoRepository riesgoRepository;
	private final CoreGateway coreGateway;
	private final ApplicationEventPublisher publicadorDeEventos;

	public RiesgoService(
			PolizaRepository polizaRepository,
			RiesgoRepository riesgoRepository,
			CoreGateway coreGateway,
			ApplicationEventPublisher publicadorDeEventos) {
		this.polizaRepository = polizaRepository;
		this.riesgoRepository = riesgoRepository;
		this.coreGateway = coreGateway;
		this.publicadorDeEventos = publicadorDeEventos;
	}

	/**
	 * CU5: agrega un riesgo a una poliza COLECTIVA vigente.
	 *
	 * @throws RecursoNoEncontradoException si la poliza no existe (404)
	 * @throws com.davivienda.demoti.shared.exception.ReglaNegocioException si la
	 *     poliza esta CANCELADA o no es COLECTIVA (409); en ese caso no se notifica
	 *     al CORE ni se publica el evento
	 */
	@Transactional
	public RiesgoResponse agregar(Long polizaId, CrearRiesgoRequest peticion) {
		Poliza poliza =
				polizaRepository
						.findById(polizaId)
						.orElseThrow(() -> RecursoNoEncontradoException.poliza(polizaId));

		Riesgo nuevo =
				poliza.agregarRiesgo(
						peticion.direccionInmueble(), peticion.arrendatario(), peticion.arrendador());

		// Se persiste el riesgo directamente, no via polizaRepository.save(poliza).
		// Guardar la poliza invoca em.merge(), y el cascade MERGE sobre un hijo
		// transitorio persiste una COPIA: el id generado aterrizaria en esa copia y
		// la respuesta saldria con id null. Al persistir esta instancia, y siendo la
		// estrategia IDENTITY, el INSERT se ejecuta de inmediato y el id queda aqui.
		Riesgo guardado = riesgoRepository.save(nuevo);

		// Igual que en la renovacion: el listener escucha en AFTER_COMMIT, asi que al
		// tomador solo se le avisa si la transaccion se confirma. Si la regla de
		// negocio hubiera fallado, la excepcion ya salio y no se publica nada.
		publicadorDeEventos.publishEvent(RiesgoAgregadoEvent.de(poliza, guardado));

		RiesgoResponse respuesta = RiesgoResponse.de(guardado);
		coreGateway.notificarActualizacion(poliza.getId());
		return respuesta;
	}

	/**
	 * CU6: cancela un riesgo sin tocar el estado de su poliza (R8).
	 *
	 * @throws RecursoNoEncontradoException si el riesgo no existe (404)
	 * @throws com.davivienda.demoti.shared.exception.ReglaNegocioException si ya
	 *     estaba CANCELADO (409); en ese caso no se notifica al CORE
	 */
	@Transactional
	public RiesgoResponse cancelar(Long riesgoId) {
		Riesgo riesgo =
				riesgoRepository
						.findByIdConPoliza(riesgoId)
						.orElseThrow(() -> RecursoNoEncontradoException.riesgo(riesgoId));

		riesgo.cancelar();
		Riesgo guardado = riesgoRepository.save(riesgo);

		// El id de la poliza se lee dentro de la transaccion, antes de notificar.
		Long polizaId = guardado.getPoliza().getId();

		RiesgoResponse respuesta = RiesgoResponse.de(guardado);
		coreGateway.notificarActualizacion(polizaId);
		return respuesta;
	}
}
