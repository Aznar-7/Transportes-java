package com.flety.commons.geo;

import java.util.List;

public final class GeoUtils {

    /** Radio medio de la Tierra en kilómetros. */
    public static final double RADIO_TIERRA_KM = 6371.0;

    /** Aproximación de kilómetros por grado de latitud. */
    private static final double KM_POR_GRADO_LATITUD = 111.32;

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

    /**
     * Genera una caja delimitadora (Bounding Box) alrededor de una lista de puntos de una ruta,
     * expandida en todas las direcciones por un margen en kilómetros (por ejemplo, el desvío máximo).
     */
    public static CajaGeografica cajaAlrededor(List<Coordenada> puntos, double margenKm) {
        if (puntos == null || puntos.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un punto para calcular la caja");
        }
        if (margenKm < 0) {
            throw new IllegalArgumentException("El margen en km no puede ser negativo");
        }

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (Coordenada p : puntos) {
            if (p != null) {
                minLat = Math.min(minLat, p.lat());
                maxLat = Math.max(maxLat, p.lat());
                minLon = Math.min(minLon, p.lng());
                maxLon = Math.max(maxLon, p.lng());
            }
        }

        // Conversión del margen en km a grados
        double deltaLat = margenKm / KM_POR_GRADO_LATITUD;

        // Para longitud se toma la latitud máxima en valor absoluto para no subestimar la caja
        double latitudReferencia = Math.max(Math.abs(minLat), Math.abs(maxLat));
        double factorCos = Math.cos(Math.toRadians(latitudReferencia));
        double deltaLon = factorCos > 0.0001 ? margenKm / (KM_POR_GRADO_LATITUD * factorCos) : deltaLat;

        return new CajaGeografica(
                Math.max(-90.0, minLat - deltaLat),
                Math.min(90.0, maxLat + deltaLat),
                Math.max(-180.0, minLon - deltaLon),
                Math.min(180.0, maxLon + deltaLon)
        );
    }
}
