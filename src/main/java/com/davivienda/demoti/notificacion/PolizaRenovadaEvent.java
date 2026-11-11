package com.davivienda.demoti.notificacion;

import com.davivienda.demoti.poliza.domain.Poliza;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Hecho de dominio: una poliza se renovo.
 *
 * <p>Lleva copiados los datos que el consumidor necesita en lugar de una
 * referencia a la entidad. El listener corre despues del commit, cuando la sesion
 * de persistencia ya se cerro, asi que una entidad podria no ser navegable; y
 * ademas un evento debe describir lo que paso en el momento en que paso, no el
 * estado actual de la fila.
 */
public record PolizaRenovadaEvent(
		Long polizaId,
		String numero,
		String tomador,
		BigDecimal canonMensual,
		BigDecimal prima,
		LocalDate fechaFinVigencia) {

	public static PolizaRenovadaEvent de(Poliza poliza) {
		return new PolizaRenovadaEvent(
				poliza.getId(),
				poliza.getNumero(),
				poliza.getTomador(),
				poliza.getCanonMensual(),
				poliza.getPrima(),
				poliza.getFechaFinVigencia());
	}
}
