package com.flety.cargas.mercado;

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
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un transportista se ofrece para llevar una carga con una de sus unidades, en uno de sus retornos.
 * Transportista, unidad y retorno viven en transporte-service: acá se guardan solo sus ids (nunca una FK entre bases).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Propuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carga_id", nullable = false, updatable = false)
    private Carga carga;

    /** sub del JWT del transportista dueño (RN-T1: solo él la puede retirar). */
    @Column(nullable = false, updatable = false)
    private String transportistaSub;

    @Column(nullable = false, updatable = false)
    private Long transportistaId;

    @Column(nullable = false, updatable = false)
    private Long unidadId;

    @Column(nullable = false, updatable = false)
    private Long retornoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPropuesta tipo;

    /** Igual al precio ofrecido si acepta; distinto si es contraoferta. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    @Column(length = 280)
    private String mensaje;

    /** RN-06: el valor declarado supera la suma asegurada y el dador tiene que aceptar el riesgo. */
    @Column(nullable = false)
    private boolean requiereAceptacionRiesgo;

    /** RN-16: 24 h o el cierre de la ventana de carga, lo que pase primero. */
    @Column(nullable = false)
    private Instant vencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPropuesta estado = EstadoPropuesta.PENDIENTE;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant creadaEn;

    @PrePersist
    void alCrear() {
        creadaEn = Instant.now();
    }
}
