package com.davivienda.demoti.shared.exception;

/**
 * Se lanza cuando una operacion es sintacticamente valida pero el estado del
 * dominio no la permite (por ejemplo, renovar una poliza cancelada).
 *
 * <p>El {@code GlobalExceptionHandler} la traduce a 409 Conflict.
 */
public class ReglaNegocioException extends RuntimeException {

	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}
}
