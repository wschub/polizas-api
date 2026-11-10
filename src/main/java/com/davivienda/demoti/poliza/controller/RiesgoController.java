package com.davivienda.demoti.poliza.controller;

import com.davivienda.demoti.poliza.dto.RiesgoResponse;
import com.davivienda.demoti.poliza.service.RiesgoService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operaciones sobre un riesgo identificado por si mismo. El alta vive en
 * {@link PolizaController}, porque cuelga de {@code /polizas/{id}/riesgos}: un
 * riesgo no existe sin su poliza.
 */
@RestController
@RequestMapping("/riesgos")
public class RiesgoController {

	private final RiesgoService riesgoService;

	public RiesgoController(RiesgoService riesgoService) {
		this.riesgoService = riesgoService;
	}

	/** CU6. */
	@PostMapping("/{id}/cancelar")
	RiesgoResponse cancelar(@PathVariable Long id) {
		return riesgoService.cancelar(id);
	}
}
