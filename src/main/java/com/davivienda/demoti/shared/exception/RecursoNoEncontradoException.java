package com.davivienda.demoti.shared.exception;

/**
 * Se lanza cuando se pide una poliza o un riesgo que no existe.
 *
 * <p>El {@code GlobalExceptionHandler} la traduce a 404 Not Found.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

	public static RecursoNoEncontradoException poliza(Long id) {
		return new RecursoNoEncontradoException("No existe la poliza con id " + id);
	}

	public static RecursoNoEncontradoException riesgo(Long id) {
		return new RecursoNoEncontradoException("No existe el riesgo con id " + id);
	}
}
