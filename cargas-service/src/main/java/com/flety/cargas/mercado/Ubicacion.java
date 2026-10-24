package com.flety.cargas.mercado;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Origen o destino de una carga. No es una tabla aparte: sus columnas se guardan dentro de la tabla carga.
 * La dirección exacta solo se muestra con el viaje confirmado (RN-13); antes se ve la localidad.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Ubicacion {

    @Column(nullable = false)
    private String provincia;

    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    private String direccion;
}
