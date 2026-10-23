package com.flety.commons.fake;

import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeorefClient;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GeorefClient falso con algunas localidades fijas, para desarrollar y testear sin Internet.
 * Una localidad que no conoce se trata como inexistente. Con simularCaida(true) se comporta como Georef caído.
 */
public class GeorefClientFake implements GeorefClient {

    private final Map<String, Coordenada> localidades = new HashMap<>();
    private boolean caido;

    public GeorefClientFake() {
        agregar("Córdoba", "Córdoba", new Coordenada(-31.4201, -64.1888));
        agregar("Córdoba", "Villa María", new Coordenada(-32.4075, -63.2402));
        agregar("Córdoba", "Río Cuarto", new Coordenada(-33.1232, -64.3493));
        agregar("Santa Fe", "Rosario", new Coordenada(-32.9468, -60.6393));
        agregar("Santa Fe", "Santa Fe", new Coordenada(-31.6333, -60.7000));
        agregar("Ciudad Autónoma de Buenos Aires", "Ciudad Autónoma de Buenos Aires", new Coordenada(-34.6037, -58.3816));
        agregar("Mendoza", "Mendoza", new Coordenada(-32.8895, -68.8458));
        agregar("Tucumán", "San Miguel de Tucumán", new Coordenada(-26.8083, -65.2176));
        agregar("Santiago del Estero", "Santiago del Estero", new Coordenada(-27.7951, -64.2615));
    }

    public GeorefClientFake agregar(String provincia, String localidad, Coordenada coordenada) {
        localidades.put(clave(provincia, localidad), coordenada);
        return this;
    }

    public GeorefClientFake simularCaida(boolean caido) {
        this.caido = caido;
        return this;
    }

    @Override
    public Coordenada ubicar(String provincia, String localidad) {
        if (caido) {
            throw new ServicioExternoException("Georef no responde (fake)");
        }
        Coordenada coordenada = localidades.get(clave(provincia, localidad));
        if (coordenada == null) {
            throw new ServicioExternoException("No pudimos ubicar la localidad " + localidad + ", " + provincia);
        }
        return coordenada;
    }

    /** Compara sin mayúsculas ni tildes, así "cordoba" y "Córdoba" son lo mismo. */
    private static String clave(String provincia, String localidad) {
        return normalizar(provincia) + "|" + normalizar(localidad);
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toLowerCase(Locale.ROOT);
    }
}
