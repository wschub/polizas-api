package com.davivienda.demoti.poliza.domain;

import com.davivienda.demoti.shared.exception.ReglaNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Poliza de arrendamiento. Las reglas de negocio viven aqui, no en los services:
 * {@link #renovar(BigDecimal)}, {@link #cancelar()} y
 * {@link #agregarRiesgo(String, String, String)} dejan la entidad en un estado
 * valido o fallan, y los services solo cargan, invocan, guardan y notifican.
 *
 * <p>Simplificacion asumida: el canon vive en la poliza, no en cada riesgo.
 *
 * <p>Invariante: toda poliza nace con su primer riesgo. Es la unica forma de que
 * una poliza INDIVIDUAL tenga riesgo, porque la regla R1 prohibe agregarle mas.
 */
@Entity
@Getter
public class Poliza {

	private static final BigDecimal CIEN = new BigDecimal("100");
	private static final int ESCALA_MONETARIA = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String numero;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoPoliza tipo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoPoliza estado;

	@Column(nullable = false)
	private String tomador;

	@Column(nullable = false)
	private LocalDate fechaInicioVigencia;

	@Column(nullable = false)
	private LocalDate fechaFinVigencia;

	@Column(nullable = false)
	private int mesesVigencia;

	@Column(nullable = false, precision = 19, scale = ESCALA_MONETARIA)
	private BigDecimal canonMensual;

	@Column(nullable = false, precision = 19, scale = ESCALA_MONETARIA)
	private BigDecimal prima;

	@OneToMany(mappedBy = "poliza", cascade = CascadeType.ALL)
	private List<Riesgo> riesgos = new ArrayList<>();

	/** Requerido por JPA. */
	protected Poliza() {
	}

	/**
	 * Crea una poliza VIGENTE junto con su primer riesgo, derivando la fecha de
	 * fin de vigencia y la prima.
	 */
	public Poliza(
			String numero,
			TipoPoliza tipo,
			String tomador,
			LocalDate fechaInicioVigencia,
			int mesesVigencia,
			BigDecimal canonMensual,
			String direccionInmueble,
			String arrendatario,
			String arrendador) {
		if (mesesVigencia <= 0) {
			throw new IllegalArgumentException("mesesVigencia debe ser mayor que 0, recibido: " + mesesVigencia);
		}
		this.numero = numero;
		this.tipo = tipo;
		this.estado = EstadoPoliza.VIGENTE;
		this.tomador = tomador;
		this.mesesVigencia = mesesVigencia;
		this.fechaInicioVigencia = fechaInicioVigencia;
		this.fechaFinVigencia = fechaInicioVigencia.plusMonths(mesesVigencia);
		this.canonMensual = canonMensual.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
		this.prima = calcularPrima(this.canonMensual);
		this.riesgos.add(new Riesgo(this, direccionInmueble, arrendatario, arrendador));
	}

	/** Lista inmodificable: los riesgos se agregan por {@link #agregarRiesgo}. */
	public List<Riesgo> getRiesgos() {
		return Collections.unmodifiableList(riesgos);
	}

	/**
	 * Aplica el IPC al canon y corre la vigencia un periodo.
	 *
	 * @param ipc porcentaje de incremento (5.2 significa 5,2 %). El rango valido
	 *     (0 a 100) lo valida el request, no el dominio, porque un IPC fuera de
	 *     rango es un 400 y no un 409.
	 * @throws ReglaNegocioException si la poliza esta CANCELADA (R3)
	 */
	public void renovar(BigDecimal ipc) {
		if (estado == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException(
					"No se puede renovar la poliza " + numero + " porque esta CANCELADA");
		}
		// canon x (1 + ipc/100) reescrito como canon x (100 + ipc) / 100 para
		// redondear una sola vez al final en lugar de dos.
		this.canonMensual = canonMensual
				.multiply(CIEN.add(ipc))
				.divide(CIEN, ESCALA_MONETARIA, RoundingMode.HALF_UP);
		this.prima = calcularPrima(this.canonMensual);
		this.fechaInicioVigencia = this.fechaFinVigencia;
		this.fechaFinVigencia = this.fechaInicioVigencia.plusMonths(mesesVigencia);
		this.estado = EstadoPoliza.RENOVADA;
	}

	/**
	 * Cancela la poliza y, con ella, todos sus riesgos ACTIVO (R5).
	 *
	 * @throws ReglaNegocioException si ya estaba CANCELADA (R6)
	 */
	public void cancelar() {
		if (estado == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException("La poliza " + numero + " ya esta CANCELADA");
		}
		this.estado = EstadoPoliza.CANCELADA;
		riesgos.stream()
				.filter(riesgo -> riesgo.getEstado() == EstadoRiesgo.ACTIVO)
				.forEach(Riesgo::cancelar);
	}

	/**
	 * Agrega un riesgo ACTIVO a una poliza COLECTIVA vigente.
	 *
	 * @throws ReglaNegocioException si la poliza esta CANCELADA (R2) o no es
	 *     COLECTIVA (R1)
	 */
	public Riesgo agregarRiesgo(String direccionInmueble, String arrendatario, String arrendador) {
		if (estado == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException(
					"No se pueden agregar riesgos a la poliza " + numero + " porque esta CANCELADA");
		}
		if (tipo != TipoPoliza.COLECTIVA) {
			throw new ReglaNegocioException(
					"Solo una poliza COLECTIVA admite riesgos adicionales; la poliza "
							+ numero + " es " + tipo);
		}
		Riesgo riesgo = new Riesgo(this, direccionInmueble, arrendatario, arrendador);
		riesgos.add(riesgo);
		return riesgo;
	}

	private BigDecimal calcularPrima(BigDecimal canon) {
		return canon.multiply(BigDecimal.valueOf(mesesVigencia))
				.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
	}
}
