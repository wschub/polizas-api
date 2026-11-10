package com.davivienda.demoti.poliza.dto;

import com.davivienda.demoti.poliza.domain.EstadoRiesgo;
import com.davivienda.demoti.poliza.domain.Riesgo;

/** Vista de un riesgo hacia el cliente. Expone el id de su poliza, no la poliza. */
public record RiesgoResponse(
		Long id,
		Long polizaId,
		String direccionInmueble,
		String arrendatario,
		String arrendador,
		EstadoRiesgo estado) {

	public static RiesgoResponse de(Riesgo riesgo) {
		return new RiesgoResponse(
				riesgo.getId(),
				riesgo.getPoliza().getId(),
				riesgo.getDireccionInmueble(),
				riesgo.getArrendatario(),
				riesgo.getArrendador(),
				riesgo.getEstado());
	}
}
