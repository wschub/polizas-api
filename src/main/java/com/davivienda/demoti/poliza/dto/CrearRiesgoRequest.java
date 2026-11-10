package com.davivienda.demoti.poliza.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /polizas/{id}/riesgos}. A diferencia del de renovar, este
 * body es obligatorio: no hay valores por defecto razonables para un inmueble.
 */
public record CrearRiesgoRequest(
		@NotBlank(message = "no puede estar vacio") String direccionInmueble,
		@NotBlank(message = "no puede estar vacio") String arrendatario,
		@NotBlank(message = "no puede estar vacio") String arrendador) {}
