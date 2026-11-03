package com.flety.cargas.viajes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * La prueba de que la carga llegó: sin comprobante no se puede pasar a ENTREGADO (FLY-27).
 * Una vez registrado no se edita, igual que los eventos: es lo que se mira si después hay una disputa.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComprobanteEntrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Un viaje tiene a lo sumo un comprobante. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viaje_id", nullable = false, unique = true, updatable = false)
    private Viaje viaje;

    @Column(nullable = false, updatable = false)
    private String numeroRemito;

    /** Quién recibió la carga en destino. */
    @Column(nullable = false, updatable = false)
    private String recibioNombre;

    @Column(nullable = false, updatable = false, length = 20)
    private String recibioDni;

    @Column(nullable = false, updatable = false)
    private Instant registradoEn;

    public ComprobanteEntrega(Viaje viaje, String numeroRemito, String recibioNombre, String recibioDni) {
        this.viaje = viaje;
        this.numeroRemito = numeroRemito;
        this.recibioNombre = recibioNombre;
        this.recibioDni = recibioDni;
        this.registradoEn = Instant.now();
    }
}
