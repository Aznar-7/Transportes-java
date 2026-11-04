package com.flety.transporte.transportista;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Transportista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ownerSub;

    @Column(nullable = false, unique = true, length = 11)
    private String cuit;

    @Column(nullable = false)
    private String razonSocial;

    @Column(nullable = false)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransportista estado;

    protected Transportista() {
    }

    public Transportista(String ownerSub, String cuit, String razonSocial, String telefono) {
        this.ownerSub = ownerSub;
        this.cuit = cuit;
        this.razonSocial = razonSocial;
        this.telefono = telefono;
        this.estado = EstadoTransportista.PENDIENTE_VERIFICACION;
    }

    public Long getId() { return id; }
    public String getOwnerSub() { return ownerSub; }
    public String getCuit() { return cuit; }
    public String getRazonSocial() { return razonSocial; }
    public String getTelefono() { return telefono; }
    public EstadoTransportista getEstado() { return estado; }
}
