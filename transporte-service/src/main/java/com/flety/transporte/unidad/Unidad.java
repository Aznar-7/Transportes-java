package com.flety.transporte.unidad;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.transporte.transportista.Transportista;
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

@Entity
public class Unidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transportista_id")
    private Transportista transportista;

    @Column(nullable = false, unique = true, length = 7)
    private String dominio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCarroceria tipoCarroceria;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal capacidadToneladas;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal capacidadM3;

    protected Unidad() {
    }

    public Unidad(Transportista transportista, String dominio, TipoCarroceria tipoCarroceria,
                  BigDecimal capacidadToneladas, BigDecimal capacidadM3) {
        this.transportista = transportista;
        this.dominio = dominio;
        this.tipoCarroceria = tipoCarroceria;
        this.capacidadToneladas = capacidadToneladas;
        this.capacidadM3 = capacidadM3;
    }

    public Long getId() { return id; }
    public Transportista getTransportista() { return transportista; }
    public String getDominio() { return dominio; }
    public TipoCarroceria getTipoCarroceria() { return tipoCarroceria; }
    public BigDecimal getCapacidadToneladas() { return capacidadToneladas; }
    public BigDecimal getCapacidadM3() { return capacidadM3; }
}
