package com.flety.cargas.mercado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * La empresa que publica cargas. Una cuenta de Keycloak representa una sola organización: por eso ownerSub es único.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub del JWT del dueño (RN-T1). Sale del token, nunca del cuerpo del pedido. */
    @Column(nullable = false, unique = true, updatable = false)
    private String ownerSub;

    @Column(nullable = false, unique = true, length = 11)
    private String cuit;

    @Column(nullable = false)
    private String razonSocial;

    @Column(nullable = false)
    private String telefono;

    /** RN-07: declara que contrata como empresa. Se exige al publicar una carga (FLY-20), no al registrarse. */
    @Column(nullable = false)
    private boolean declaraEmpresa;

    public Dador(String ownerSub, String cuit, String razonSocial, String telefono, boolean declaraEmpresa) {
        this.ownerSub = ownerSub;
        this.cuit = cuit;
        this.razonSocial = razonSocial;
        this.telefono = telefono;
        this.declaraEmpresa = declaraEmpresa;
    }
}
