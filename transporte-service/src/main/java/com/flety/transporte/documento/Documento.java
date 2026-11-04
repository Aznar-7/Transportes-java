package com.flety.transporte.documento;

import com.flety.transporte.unidad.Unidad;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_documento_id")
    private TipoDocumento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id")
    private Unidad unidad;

    @Column(nullable = false)
    private String numero;

    /** Null si el tipo no vence. */
    private Instant vigencia;

    @Column(precision = 14, scale = 2)
    private BigDecimal sumaAsegurada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoDocumento estado;

    private String motivoRechazo;

    protected Documento() {
    }

    public Documento(TipoDocumento tipo, Unidad unidad, String numero, Instant vigencia, BigDecimal sumaAsegurada) {
        this.tipo = tipo;
        this.unidad = unidad;
        this.numero = numero;
        this.vigencia = vigencia;
        this.sumaAsegurada = sumaAsegurada;
        this.estado = EstadoDocumento.EN_REVISION;
    }

    public Long getId() { return id; }
    public TipoDocumento getTipo() { return tipo; }
    public Unidad getUnidad() { return unidad; }
    public String getNumero() { return numero; }
    public Instant getVigencia() { return vigencia; }
    public BigDecimal getSumaAsegurada() { return sumaAsegurada; }
    public EstadoDocumento getEstado() { return estado; }
    public String getMotivoRechazo() { return motivoRechazo; }
}
