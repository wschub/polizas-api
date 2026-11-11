package com.davivienda.demoti.notificacion;

import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.Riesgo;

/**
 * Hecho de dominio: se incluyo un inmueble en la cobertura de una poliza.
 *
 * <p>Como {@link PolizaRenovadaEvent}, lleva los datos copiados y no referencias a
 * las entidades: el listener corre despues del commit, cuando la sesion de
 * persistencia ya se cerro.
 */
public record RiesgoAgregadoEvent(
		Long polizaId,
		String numeroPoliza,
		String tomador,
		Long riesgoId,
		String direccionInmueble) {

	public static RiesgoAgregadoEvent de(Poliza poliza, Riesgo riesgo) {
		return new RiesgoAgregadoEvent(
				poliza.getId(),
				poliza.getNumero(),
				poliza.getTomador(),
				riesgo.getId(),
				riesgo.getDireccionInmueble());
	}
}
