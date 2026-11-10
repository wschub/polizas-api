package com.davivienda.demoti.poliza.controller;

import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import com.davivienda.demoti.poliza.dto.CrearRiesgoRequest;
import com.davivienda.demoti.poliza.dto.PolizaResponse;
import com.davivienda.demoti.poliza.dto.RenovarRequest;
import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.service.PolizaService;
import com.davivienda.demoti.poliza.service.RiesgoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Todo lo que cuelga de /polizas, incluidos los riesgos de una poliza. */
@RestController
@RequestMapping("/polizas")
public class PolizaController {

	private final PolizaService polizaService;
	private final RiesgoService riesgoService;

	public PolizaController(PolizaService polizaService, RiesgoService riesgoService) {
		this.polizaService = polizaService;
		this.riesgoService = riesgoService;
	}

	/**
	 * CU1. Un valor que no existe en el enum lo rechaza la conversion de Spring con
	 * un type mismatch, que el GlobalExceptionHandler traduce a 400.
	 */
	@GetMapping
	List<PolizaResponse> listar(
			@RequestParam(required = false) TipoPoliza tipo,
			@RequestParam(required = false) EstadoPoliza estado) {
		return polizaService.listar(tipo, estado);
	}

	/** CU2. */
	@GetMapping("/{id}/riesgos")
	List<RiesgoResponse> listarRiesgos(@PathVariable Long id) {
		return polizaService.listarRiesgos(id);
	}

	/**
	 * CU3. El body es opcional: sin el, o con el sin {@code ipc}, se aplica el IPC
	 * por defecto de {@code polizas.ipc}.
	 */
	@PostMapping("/{id}/renovar")
	PolizaResponse renovar(
			@PathVariable Long id, @Valid @RequestBody(required = false) RenovarRequest peticion) {
		return polizaService.renovar(id, peticion == null ? null : peticion.ipc());
	}

	/** CU4. */
	@PostMapping("/{id}/cancelar")
	PolizaResponse cancelar(@PathVariable Long id) {
		return polizaService.cancelar(id);
	}

	/**
	 * CU5. Cuelga de /polizas porque un riesgo no existe sin su poliza, pero la
	 * orquestacion es de RiesgoService. El body es obligatorio.
	 */
	@PostMapping("/{id}/riesgos")
	@ResponseStatus(HttpStatus.CREATED)
	RiesgoResponse agregarRiesgo(
			@PathVariable Long id, @Valid @RequestBody CrearRiesgoRequest peticion) {
		return riesgoService.agregar(id, peticion);
	}
}
