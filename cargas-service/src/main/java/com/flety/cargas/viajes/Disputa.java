package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ConflictoException;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un reclamo del dador sobre la entrega, abierto dentro de la ventana de conformidad (FLY-31).
 * Lo resuelve un OPERADOR (FLY-31). Los datos de la apertura no se editan; solo se completa la resolución.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Disputa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viaje_id", nullable = false, updatable = false)
    private Viaje viaje;

    /** Motivo corto, por ejemplo "Faltante" o "Mercadería dañada". */
    @Column(nullable = false, updatable = false, length = 100)
    private String motivo;

    @Column(nullable = false, updatable = false, length = 1000)
    private String descripcion;

    /** sub del dador que la abrió. */
    @Column(nullable = false, updatable = false)
    private String abiertaPorSub;

    @Column(nullable = false, updatable = false)
    private Instant abiertaEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDisputa estado;

    @Column(length = 1000)
    private String resolucion;

    /** sub del OPERADOR que la resolvió. */
    private String operadorSub;

    private Instant resueltaEn;

    /** Cómo se resolvió (FLY-31); null mientras siga abierta. */
    private Boolean aFavorDelTransportista;

    public Disputa(Viaje viaje, String motivo, String descripcion, String abiertaPorSub) {
        this.viaje = viaje;
        this.motivo = motivo;
        this.descripcion = descripcion;
        this.abiertaPorSub = abiertaPorSub;
        this.abiertaEn = Instant.now();
        this.estado = EstadoDisputa.ABIERTA;
    }

    /**
     * Completa la resolución, una sola vez: los datos de la apertura no cambian y una disputa resuelta no se reabre.
     * Quién decide qué pasa con el viaje es el viaje (EnDisputa.resolverDisputa); esto solo deja constancia.
     */
    void resolver(boolean aFavorDelTransportista, String resolucion, String operadorSub) {
        if (estado != EstadoDisputa.ABIERTA) {
            throw new ConflictoException("La disputa ya está resuelta");
        }
        this.estado = EstadoDisputa.RESUELTA;
        this.aFavorDelTransportista = aFavorDelTransportista;
        this.resolucion = resolucion;
        this.operadorSub = operadorSub;
        this.resueltaEn = Instant.now();
    }
}
