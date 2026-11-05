package com.flety.commons.geo;

import java.util.List;

public final class GeoUtils {

    /** Radio medio de la Tierra en kilómetros. */
    public static final double RADIO_TIERRA_KM = 6371.0;

    private GeoUtils() {
    }

    /**
     * Calcula la distancia en kilómetros entre dos coordenadas usando la fórmula de Haversine.
     */
    public static double distanciaEnKm(Coordenada c1, Coordenada c2) {
        if (c1 == null || c2 == null) {
            throw new IllegalArgumentException("Las coordenadas no pueden ser nulas");
        }

        double lat1Rad = Math.toRadians(c1.lat());
        double lon1Rad = Math.toRadians(c1.lng());
        double lat2Rad = Math.toRadians(c2.lat());
        double lon2Rad = Math.toRadians(c2.lng());

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return RADIO_TIERRA_KM * c;
    }

    /**
     * Calcula la distancia acumulada en kilómetros a lo largo de una secuencia de coordenadas.
     */
    public static double distanciaEnKm(List<Coordenada> puntos) {
        if (puntos == null || puntos.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < puntos.size() - 1; i++) {
            total += distanciaEnKm(puntos.get(i), puntos.get(i + 1));
        }
        return total;
    }
}
