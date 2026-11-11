package com.davivienda.demoti.notificacion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Simula el aviso al tomador cuando su poliza se renueva. No envia nada: deja
 * constancia en el log de lo que un servicio de correo y SMS haria.
 *
 * <p>Escucha en fase {@code AFTER_COMMIT}, asi que solo se ejecuta si la
 * transaccion de la renovacion realmente se confirmo. Si la operacion se
 * deshiciera, no se avisaria al tomador de un cambio que no ocurrio.
 */
@Component
@Slf4j
public class NotificacionListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alRenovarPoliza(PolizaRenovadaEvent evento) {
		try {
			log.info(
					"[NOTIFICACION] Se enviaría correo y SMS al tomador {} por renovación de la póliza {} "
							+ "(nuevo canon {}, vigente hasta {})",
					evento.tomador(),
					evento.numero(),
					evento.canonMensual(),
					evento.fechaFinVigencia());
		} catch (Exception excepcion) {
			// Un fallo al notificar no puede romper ni revertir la renovacion: cuando
			// este metodo corre, la transaccion ya se confirmo. Queda el registro para
			// poder reenviar el aviso.
			log.error(
					"[NOTIFICACION] No se pudo notificar la renovacion de la poliza {}: {}: {}",
					evento.polizaId(),
					excepcion.getClass().getSimpleName(),
					excepcion.getMessage());
		}
	}
}
