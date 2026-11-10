package com.davivienda.demoti.core;

/**
 * Puerto de salida hacia el CORE. Los services dependen de esta interfaz y nunca
 * de HTTP directo, para que el transporte se pueda cambiar (cola, outbox, otro
 * protocolo) sin tocar la logica de negocio.
 */
public interface CoreGateway {

	/**
	 * Avisa al CORE que la poliza cambio. Es una notificacion best-effort: un fallo
	 * del CORE no debe revertir la operacion que ya se guardo.
	 */
	void notificarActualizacion(Long polizaId);
}
