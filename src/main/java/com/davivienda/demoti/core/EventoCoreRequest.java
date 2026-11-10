package com.davivienda.demoti.core;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del evento que viaja hacia el CORE. */
public record EventoCoreRequest(@NotBlank String evento, @NotNull Long polizaId) {}
