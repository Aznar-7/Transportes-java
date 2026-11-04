package com.flety.transporte.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Tabla configurable y no código: sumar un tipo es un INSERT. Los iniciales están en data.sql.
 */
@Entity
public class TipoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "aplica_a", nullable = false)
    private AplicaA aplicaA;

    @Column(nullable = false)
    private boolean obligatorio;

    @Column(nullable = false)
    private boolean vence;

    /** Solo la póliza de carga: su suma asegurada se compara con el valor declarado (RN-06). */
    @Column(nullable = false)
    private boolean requiereSumaAsegurada;

    protected TipoDocumento() {
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public AplicaA getAplicaA() { return aplicaA; }
    public boolean isObligatorio() { return obligatorio; }
    public boolean isVence() { return vence; }
    public boolean isRequiereSumaAsegurada() { return requiereSumaAsegurada; }
}
