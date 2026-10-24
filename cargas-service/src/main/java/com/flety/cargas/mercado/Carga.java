package com.flety.cargas.mercado;

import com.flety.commons.contrato.TipoCarroceria;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Una carga que un dador necesita mover. Es la entidad central del mercado.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Carga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /**
     * Bloqueo optimista (RN-14): JPA suma 1 en cada UPDATE y, si otro pedido la modificó antes,
     * el UPDATE no encuentra la versión que leyó y falla con 409 en vez de pisar el cambio.
     */
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    /** sub del JWT del dador dueño (RN-T1). */
    @Column(nullable = false, updatable = false)
    private String dadorSub;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provincia", column = @Column(name = "origen_provincia", nullable = false)),
            @AttributeOverride(name = "localidad", column = @Column(name = "origen_localidad", nullable = false)),
            @AttributeOverride(name = "lat", column = @Column(name = "origen_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "origen_lng", nullable = false)),
            @AttributeOverride(name = "direccion", column = @Column(name = "origen_direccion"))
    })
    private Ubicacion origen;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "provincia", column = @Column(name = "destino_provincia", nullable = false)),
            @AttributeOverride(name = "localidad", column = @Column(name = "destino_localidad", nullable = false)),
            @AttributeOverride(name = "lat", column = @Column(name = "destino_lat", nullable = false)),
            @AttributeOverride(name = "lng", column = @Column(name = "destino_lng", nullable = false)),
            @AttributeOverride(name = "direccion", column = @Column(name = "destino_direccion"))
    })
    private Ubicacion destino;

    @Column(nullable = false)
    private Instant cargaDesde;

    @Column(nullable = false)
    private Instant cargaHasta;

    @Column(nullable = false)
    private Instant descargaDesde;

    @Column(nullable = false)
    private Instant descargaHasta;

    @Column(nullable = false)
    private String rubro;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pesoToneladas;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal volumenM3;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "carga_carroceria", joinColumns = @JoinColumn(name = "carga_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "carroceria", nullable = false)
    private Set<TipoCarroceria> carroceriasAceptadas = EnumSet.noneOf(TipoCarroceria.class);

    /** RN-03: lo fija el dador. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precioOfrecido;

    /** RN-06: se compara con la suma asegurada de la póliza de carga de la unidad. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valorDeclarado;

    /** RN-08: declaraciones juradas del dador sobre la carga. */
    @Column(nullable = false)
    private boolean declaraNoPeligrosa;

    @Column(nullable = false)
    private boolean declaraNoSobredimensionada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCarga estado = EstadoCarga.PUBLICADA;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant creadaEn;

    @PrePersist
    void alCrear() {
        creadaEn = Instant.now();
    }
}
