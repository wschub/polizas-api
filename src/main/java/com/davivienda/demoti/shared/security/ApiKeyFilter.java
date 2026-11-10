package com.davivienda.demoti.shared.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige el header {@code x-api-key} en toda peticion y corta la cadena con 401 si
 * falta o no coincide.
 *
 * <p>No se anota con {@code @Component}: lo instancia {@link SecurityConfig}. Si
 * fuera un bean, Spring Boot lo registraria tambien como filtro del servlet
 * container y correria dos veces por peticion.
 *
 * <p>La autorizacion la resuelve este filtro, no las reglas de Spring Security,
 * que dejan pasar todo ({@code anyRequest().permitAll()}).
 */
@Slf4j
public class ApiKeyFilter extends OncePerRequestFilter {

	public static final String HEADER_API_KEY = "x-api-key";

	/** Unica ruta sin API key: permite que un orquestador consulte el estado. */
	private static final String RUTA_HEALTH = "/actuator/health";

	private final byte[] apiKeyEsperada;
	private final ObjectMapper objectMapper;

	ApiKeyFilter(String apiKeyEsperada, ObjectMapper objectMapper) {
		this.apiKeyEsperada = apiKeyEsperada.getBytes(StandardCharsets.UTF_8);
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String ruta = request.getRequestURI();
		return RUTA_HEALTH.equals(ruta) || ruta.startsWith(RUTA_HEALTH + "/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String recibida = request.getHeader(HEADER_API_KEY);
		if (!esValida(recibida)) {
			// Se registra la ruta, nunca el valor recibido: iria a los logs en claro.
			log.warn(
					"[SEGURIDAD] Peticion rechazada por x-api-key {}: {} {}",
					recibida == null ? "ausente" : "invalida",
					request.getMethod(),
					request.getRequestURI());
			responderNoAutorizado(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean esValida(String recibida) {
		if (recibida == null) {
			return false;
		}
		// Comparacion de tiempo constante: equals() sale en el primer caracter
		// distinto y filtra informacion sobre la clave esperada.
		return MessageDigest.isEqual(recibida.getBytes(StandardCharsets.UTF_8), apiKeyEsperada);
	}

	/**
	 * Responde el 401 en formato ProblemDetail a mano. Este filtro corre antes del
	 * DispatcherServlet, asi que el {@code GlobalExceptionHandler} no lo alcanza.
	 *
	 * <p>El mensaje no distingue entre clave ausente e invalida: no hay razon para
	 * confirmarle a quien prueba claves que el header tenia el formato correcto.
	 */
	private void responderNoAutorizado(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		ProblemDetail problema =
				ProblemDetail.forStatusAndDetail(
						HttpStatus.UNAUTHORIZED,
						"Falta el header " + HEADER_API_KEY + " o su valor no es valido");
		problema.setTitle("No autorizado");
		problema.setInstance(URI.create(request.getRequestURI()));

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), problema);
	}
}
