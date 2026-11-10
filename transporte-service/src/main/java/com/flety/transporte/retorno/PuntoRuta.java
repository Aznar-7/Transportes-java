package com.flety.transporte.retorno;

import com.flety.commons.geo.Coordenada;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Un punto de la ruta. Se guarda en la tabla retorno_punto, una fila por punto y en orden. */
@Embeddable
public class PuntoRuta {

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    protected PuntoRuta() {
    }

    PuntoRuta(Coordenada coordenada) {
        this.lat = coordenada.lat();
        this.lng = coordenada.lng();
    }

    Coordenada coordenada() {
        return new Coordenada(lat, lng);
    }
}
