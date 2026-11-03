package com.flety.cargas.viajes;

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
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Un renglón de la línea de tiempo del viaje: de qué estado a cuál pasó, quién lo hizo y cuándo.
 * <p>
 * Es una tabla de auditoría <b>append-only</b>: solo se agregan filas, nunca se cambian ni se borran.
 * Si un evento se pudiera editar, la historia del viaje dejaría de servir como prueba en una disputa.
 * Se cuida en tres niveles:
 * <ul>
 *     <li>no hay setters y el constructor es del paquete: solo Viaje.cambiarEstado crea eventos;</li>
 *     <li>todas las columnas son updatable = false: Hibernate nunca arma un UPDATE para esta tabla, así que
 *     aunque un bug cambie un campo en memoria, el cambio no llega a la base;</li>
 *     <li>@PreRemove corta con una excepción si algo intenta borrar uno, y @PreUpdate queda de resguardo
 *     por si algún día se agrega una columna actualizable.</li>
 * </ul>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoViaje {

    /** Para los cambios que hace el sistema solo, como la conformidad automática a las 48 h. */
    public static final String ACTOR_SISTEMA = "SISTEMA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viaje_id", nullable = false, updatable = false)
    private Viaje viaje;

    /** Null en el primer evento: el viaje nace CONFIRMADO, no viene de otro estado. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private TipoEstadoViaje estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoEstadoViaje estadoNuevo;

    /** sub del JWT de quien hizo el cambio, o ACTOR_SISTEMA. */
    @Column(nullable = false, updatable = false)
    private String actorSub;

    @Column(nullable = false, updatable = false)
    private Instant ocurridoEn;

    /** Un dato extra para leer la línea de tiempo, por ejemplo el número de remito de la entrega. */
    @Column(length = 500, updatable = false)
    private String detalle;

    EventoViaje(Viaje viaje, TipoEstadoViaje estadoAnterior, TipoEstadoViaje estadoNuevo, String actorSub,
                String detalle) {
        this.viaje = viaje;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.actorSub = actorSub;
        this.detalle = detalle;
        this.ocurridoEn = Instant.now();
    }

    @PreUpdate
    void alModificar() {
        throw new IllegalStateException("EventoViaje " + id + " no se puede modificar: la auditoría es append-only");
    }

    @PreRemove
    void alBorrar() {
        throw new IllegalStateException("EventoViaje " + id + " no se puede borrar: la auditoría es append-only");
    }
}
