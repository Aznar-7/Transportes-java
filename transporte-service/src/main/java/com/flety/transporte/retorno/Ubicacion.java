package com.flety.transporte.retorno;

import com.flety.commons.contrato.UbicacionDTO;
import com.flety.commons.geo.Coordenada;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Origen o destino del retorno. No es una tabla: sus columnas van dentro de la tabla retorno. */
@Embeddable
public class Ubicacion {

    @Column(nullable = false)
    private String provincia;

    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    protected Ubicacion() {
    }

    public Ubicacion(String provincia, String localidad, Coordenada coordenada) {
        this.provincia = provincia;
        this.localidad = localidad;
        this.lat = coordenada.lat();
        this.lng = coordenada.lng();
    }

    public Coordenada coordenada() {
        return new Coordenada(lat, lng);
    }

    public UbicacionDTO comoDTO() {
        return new UbicacionDTO(provincia, localidad, lat, lng);
    }
}
