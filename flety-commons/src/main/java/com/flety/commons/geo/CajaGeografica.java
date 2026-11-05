package com.flety.commons.geo;

/**
 * Caja delimitadora geográfica (Bounding Box) definida por latitudes y longitudes mínimas y máximas.
 * Se utiliza para prefiltrar consultas en base de datos.
 */

public record CajaGeografica(double latMin, double latMax, double lonMin, double lonMax) {

    public boolean contiene(Coordenada punto) {
        if (punto == null) {
            return false;
        }
        return punto.lat() >= latMin && punto.lat() <= latMax
                && punto.lng() >= lonMin && punto.lng() <= lonMax;
    }
}