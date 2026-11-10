package com.davivienda.demoti.core;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock del CORE: representa el sistema externo al que esta aplicacion notifica.
 * No persiste nada, solo deja constancia en el log de que el evento llego.
 */
@RestController
@Slf4j
public class CoreMockController {

	/** Respuesta del mock. */
	public record RespuestaEvento(boolean recibido) {}

	@PostMapping("/core-mock/evento")
	@ResponseStatus(HttpStatus.ACCEPTED)
	RespuestaEvento recibirEvento(@Valid @RequestBody EventoCoreRequest peticion) {
		log.info(
				"[CORE-MOCK] Se intentó enviar al CORE: evento={} polizaId={}",
				peticion.evento(),
				peticion.polizaId());
		return new RespuestaEvento(true);
	}
}
