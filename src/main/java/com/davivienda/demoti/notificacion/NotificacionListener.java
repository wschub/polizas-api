package com.davivienda.demoti.notificacion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Simula los avisos al tomador. No envia nada: deja constancia en el log de lo
 * que un servicio de correo y SMS haria.
 *
 * <p>Todos los metodos escuchan en fase {@code AFTER_COMMIT}, asi que solo se
 * ejecutan si la transaccion de la operacion realmente se confirmo. Si la
 * operacion se deshiciera, no se avisaria al tomador de un cambio que no
 * ocurrio.
 */
@Component
@Slf4j
public class NotificacionListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alRenovarPoliza(PolizaRenovadaEvent evento) {
		notificar(
				evento.polizaId(),
				() ->
						log.info(
								"[NOTIFICACION] Se enviaría correo y SMS al tomador {} por renovación de la "
										+ "póliza {} (nuevo canon {}, vigente hasta {})",
								evento.tomador(),
								evento.numero(),
								evento.canonMensual(),
								evento.fechaFinVigencia()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alAgregarRiesgo(RiesgoAgregadoEvent evento) {
		notificar(
				evento.polizaId(),
				() ->
						log.info(
								"[NOTIFICACION] Se enviaría correo y SMS al tomador {} por la inclusión de un "
										+ "riesgo ({}) en la póliza {}",
								evento.tomador(),
								evento.direccionInmueble(),
								evento.numeroPoliza()));
	}

	/**
	 * Envuelve el envio para que la garantia viva en un solo sitio: un fallo al
	 * notificar no puede romper ni revertir la operacion. Cuando estos metodos
	 * corren, la transaccion ya se confirmo; queda el registro para poder reenviar
	 * el aviso.
	 */
	private void notificar(Long polizaId, Runnable envio) {
		try {
			envio.run();
		} catch (Exception excepcion) {
			log.error(
					"[NOTIFICACION] No se pudo notificar al tomador de la poliza {}: {}: {}",
					polizaId,
					excepcion.getClass().getSimpleName(),
					excepcion.getMessage());
		}
	}
}
