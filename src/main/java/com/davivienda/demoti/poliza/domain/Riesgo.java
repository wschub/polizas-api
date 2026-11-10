package com.davivienda.demoti.poliza.domain;

import com.davivienda.demoti.shared.exception.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;

/**
 * Inmueble cubierto por una poliza. El arrendatario es el asegurado y el
 * arrendador el beneficiario.
 *
 * <p>Se construye siempre a traves de la poliza, que es la que decide si admite
 * el riesgo ({@link Poliza#agregarRiesgo}) o lo crea al nacer.
 */
@Entity
@Getter
public class Riesgo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "poliza_id", nullable = false)
	private Poliza poliza;

	@Column(nullable = false)
	private String direccionInmueble;

	@Column(nullable = false)
	private String arrendatario;

	@Column(nullable = false)
	private String arrendador;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoRiesgo estado;

	/** Requerido por JPA. */
	protected Riesgo() {
	}

	Riesgo(Poliza poliza, String direccionInmueble, String arrendatario, String arrendador) {
		this.poliza = poliza;
		this.direccionInmueble = direccionInmueble;
		this.arrendatario = arrendatario;
		this.arrendador = arrendador;
		this.estado = EstadoRiesgo.ACTIVO;
	}

	/**
	 * Cancela este riesgo sin tocar el estado de la poliza (R8): que un inmueble
	 * salga de la cobertura no termina el contrato.
	 *
	 * @throws ReglaNegocioException si ya estaba CANCELADO (R7)
	 */
	public void cancelar() {
		if (estado == EstadoRiesgo.CANCELADO) {
			throw new ReglaNegocioException("El riesgo " + id + " ya esta CANCELADO");
		}
		this.estado = EstadoRiesgo.CANCELADO;
	}
}
