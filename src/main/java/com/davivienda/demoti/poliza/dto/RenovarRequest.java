package com.davivienda.demoti.poliza.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/**
 * Cuerpo opcional de {@code POST /polizas/{id}/renovar}.
 *
 * <p>El {@code ipc} va en porcentaje (5.2 significa 5,2 %). Si no viene, el
 * service usa el valor de {@code polizas.ipc}. Las anotaciones no se disparan con
 * un nulo, asi que omitirlo es valido y solo se valida el rango cuando se envia.
 */
public record RenovarRequest(
		@DecimalMin(value = "0", message = "debe ser mayor o igual a 0")
		@DecimalMax(value = "100", message = "debe ser menor o igual a 100")
		BigDecimal ipc) {}
