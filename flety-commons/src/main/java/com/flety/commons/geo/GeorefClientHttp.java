package com.flety.commons.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flety.commons.excepciones.ServicioExternoException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cliente HTTP para consultar coordenadas en la API pública de Georef Argentina (FLY-36).
 * 
 * Reglas de negocio:
 * 
 *     Consulta la localidad y provincia por GET a {@code /localidades}.
 *     Retorna la {@link Coordenada} extraída del centroide del primer resultado.
 *     Si la localidad no existe, lanza {@link ServicioExternoException} (nunca inventa coordenadas).
 *     Si Georef no responde o devuelve un error, lanza {@link ServicioExternoException}.
 *
 */

public class GeorefClientHttp implements GeorefClient {

    public static final String URL_BASE_DEFAULT = "https://apis.datos.gob.ar/georef/api";

    private final RestClient restClient;

    public GeorefClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    public GeorefClientHttp(String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build());
    }

    public GeorefClientHttp() {
        this(URL_BASE_DEFAULT);
    }

    @Override
    public Coordenada ubicar(String provincia, String localidad) {
        if (provincia == null || provincia.isBlank() || localidad == null || localidad.isBlank()) {
            throw new ServicioExternoException("No pudimos ubicar la localidad " + localidad + ", " + provincia);
        }

        GeorefRespuesta respuesta;
        try {
            respuesta = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/localidades")
                            .queryParam("nombre", localidad)
                            .queryParam("provincia", provincia)
                            .queryParam("campos", "centroide")
                            .queryParam("max", 1)
                            .build())
                    .retrieve()
                    .body(GeorefRespuesta.class);
        } catch (RestClientException ex) {
            throw new ServicioExternoException(
                    "Georef no responde al consultar " + localidad + ", " + provincia, ex);
        }

        if (respuesta == null || respuesta.localidades() == null || respuesta.localidades().isEmpty()) {
            throw new ServicioExternoException(
                    "No pudimos ubicar la localidad " + localidad + ", " + provincia);
        }

        LocalidadItem primerItem = respuesta.localidades().get(0);
        if (primerItem == null || primerItem.centroide() == null) {
            throw new ServicioExternoException(
                    "No pudimos ubicar la localidad " + localidad + ", " + provincia);
        }

        return new Coordenada(primerItem.centroide().lat(), primerItem.centroide().lon());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeorefRespuesta(List<LocalidadItem> localidades) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocalidadItem(Centroide centroide) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Centroide(double lat, double lon) {
    }
}

