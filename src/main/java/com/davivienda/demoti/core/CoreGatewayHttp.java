package com.davivienda.demoti.core;

import com.davivienda.demoti.shared.security.ApiKeyFilter;
import java.net.http.HttpClient;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador HTTP del puerto {@link CoreGateway}: publica el evento en el mock del
 * CORE.
 *
 * <p>El {@code RestClient} se construye a mano en lugar de inyectar un
 * {@code RestClient.Builder} autoconfigurado, para que los timeouts sean
 * explicitos y no dependan de la configuracion global de la aplicacion.
 */
@Component
@Slf4j
public class CoreGatewayHttp implements CoreGateway {

	private static final String EVENTO_ACTUALIZACION = "ACTUALIZACION";
	private static final String RUTA_EVENTO = "/core-mock/evento";

	/** Un CORE lento no puede dejar colgada la peticion del cliente. */
	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private final RestClient restClient;

	public CoreGatewayHttp(
			@Value("${core.base-url}") String baseUrl,
			@Value("${seguridad.api-key}") String apiKey) {
		// El timeout de conexion es del HttpClient del JDK y el de lectura de la
		// fabrica: JdkClientHttpRequestFactory solo expone el segundo.
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
		fabrica.setReadTimeout(TIMEOUT);

		this.restClient =
				RestClient.builder()
						.baseUrl(baseUrl)
						.requestFactory(fabrica)
						.defaultHeader(ApiKeyFilter.HEADER_API_KEY, apiKey)
						.build();
	}

	/**
	 * Notifica al CORE y se come cualquier fallo.
	 *
	 * <p>La operacion de negocio ya se guardo cuando se llama a este metodo, asi que
	 * propagar la excepcion revertiria una renovacion o una cancelacion valida
	 * porque un sistema externo no respondio. Queda registrado en el log para que
	 * el evento se pueda reenviar.
	 */
	@Override
	public void notificarActualizacion(Long polizaId) {
		try {
			restClient
					.post()
					.uri(RUTA_EVENTO)
					.contentType(MediaType.APPLICATION_JSON)
					.body(new EventoCoreRequest(EVENTO_ACTUALIZACION, polizaId))
					.retrieve()
					.toBodilessEntity();
		} catch (Exception excepcion) {
			// Se incluye el tipo de excepcion porque el mensaje de una
			// ResourceAccessException suele terminar en "null" y no dice nada.
			log.error(
					"[CORE] No se pudo notificar la actualizacion de la poliza {}: {}: {}",
					polizaId,
					excepcion.getClass().getSimpleName(),
					excepcion.getMessage());
		}
	}
}
