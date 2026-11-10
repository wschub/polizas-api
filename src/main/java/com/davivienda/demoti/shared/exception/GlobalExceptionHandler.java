package com.davivienda.demoti.shared.exception;

import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce las excepciones de la aplicacion a respuestas ProblemDetail (RFC 9457),
 * para que un cliente reciba siempre el mismo formato de error.
 *
 * <p>El 401 no pasa por aqui: lo escribe {@code ApiKeyFilter}, que corre antes del
 * DispatcherServlet.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail manejarRecursoNoEncontrado(RecursoNoEncontradoException excepcion) {
		return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", excepcion.getMessage());
	}

	@ExceptionHandler(ReglaNegocioException.class)
	ProblemDetail manejarReglaNegocio(ReglaNegocioException excepcion) {
		return problema(HttpStatus.CONFLICT, "Regla de negocio violada", excepcion.getMessage());
	}

	/** Body con campos invalidos (por ejemplo, un {@code @NotBlank} vacio). */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail manejarBodyInvalido(MethodArgumentNotValidException excepcion) {
		String detalle =
				excepcion.getBindingResult().getFieldErrors().stream()
						.map(error -> error.getField() + ": " + error.getDefaultMessage())
						.sorted()
						.collect(Collectors.joining("; "));
		return problema(HttpStatus.BAD_REQUEST, "Peticion invalida", detalle);
	}

	/** Validaciones sobre parametros de metodo, fuera del body. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail manejarParametrosInvalidos(HandlerMethodValidationException excepcion) {
		String detalle =
				excepcion.getParameterValidationResults().stream()
						.flatMap(
								resultado ->
										resultado.getResolvableErrors().stream()
												.map(
														error ->
																resultado.getMethodParameter().getParameterName()
																		+ ": "
																		+ error.getDefaultMessage()))
						.sorted()
						.collect(Collectors.joining("; "));
		return problema(HttpStatus.BAD_REQUEST, "Peticion invalida", detalle);
	}

	/**
	 * Parametro o variable de ruta que no se puede convertir: un {@code tipo} o
	 * {@code estado} con valor que no existe en el enum, o un id no numerico.
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail manejarTipoInvalido(MethodArgumentTypeMismatchException excepcion) {
		StringBuilder detalle =
				new StringBuilder(
						"El valor '"
								+ excepcion.getValue()
								+ "' no es valido para el parametro '"
								+ excepcion.getName()
								+ "'");
		valoresAceptados(excepcion)
				.ifPresent(valores -> detalle.append(". Valores aceptados: ").append(valores));
		return problema(HttpStatus.BAD_REQUEST, "Peticion invalida", detalle.toString());
	}

	/** Body ausente o que no es JSON valido. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail manejarBodyIlegible(HttpMessageNotReadableException excepcion) {
		return problema(
				HttpStatus.BAD_REQUEST, "Peticion invalida", "El cuerpo de la peticion no es JSON valido");
	}

	/**
	 * Si el parametro era un enum, listar sus valores le ahorra al cliente adivinar
	 * cual escribio mal.
	 */
	private java.util.Optional<String> valoresAceptados(
			MethodArgumentTypeMismatchException excepcion) {
		Class<?> tipoRequerido = excepcion.getRequiredType();
		if (tipoRequerido == null || !tipoRequerido.isEnum()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(
				Stream.of(tipoRequerido.getEnumConstants())
						.map(Object::toString)
						.sorted(Comparator.naturalOrder())
						.collect(Collectors.joining(", ")));
	}

	private ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setTitle(titulo);
		return problema;
	}
}
