package com.davivienda.demoti.poliza.dto;

import com.davivienda.demoti.poliza.domain.EstadoPoliza;
import com.davivienda.demoti.poliza.domain.Poliza;
import com.davivienda.demoti.poliza.domain.TipoPoliza;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vista de una poliza hacia el cliente. No incluye los riesgos: para eso esta
 * {@code GET /polizas/{id}/riesgos}, y asi listar polizas no toca la coleccion
 * lazy ni provoca un N+1.
 */
public record PolizaResponse(
		Long id,
		String numero,
		TipoPoliza tipo,
		EstadoPoliza estado,
		String tomador,
		LocalDate fechaInicioVigencia,
		LocalDate fechaFinVigencia,
		int mesesVigencia,
		BigDecimal canonMensual,
		BigDecimal prima) {

	public static PolizaResponse de(Poliza poliza) {
		return new PolizaResponse(
				poliza.getId(),
				poliza.getNumero(),
				poliza.getTipo(),
				poliza.getEstado(),
				poliza.getTomador(),
				poliza.getFechaInicioVigencia(),
				poliza.getFechaFinVigencia(),
				poliza.getMesesVigencia(),
				poliza.getCanonMensual(),
				poliza.getPrima());
	}
}
