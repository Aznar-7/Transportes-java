package com.flety.commons.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flety.commons.excepciones.ServicioExternoException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Cliente HTTP para consultar coordenadas en la API pública de Georef Argentina (FLY-36).
 * <p>
 * Reglas de negocio:
 * </p>
 * <ul>
 *     <li>Consulta la localidad y provincia por GET a {@code /localidades}.</li>
 *     <li>Retorna la {@link Coordenada} extraída del centroide del primer resultado.</li>
 *     <li>Si la localidad no existe, lanza {@link ServicioExternoException} (nunca inventa coordenadas).</li>
 *     <li>Si Georef no responde o devuelve un error, lanza {@link ServicioExternoException}.</li>
 * </ul>
 */

public class GeorefClientHttp implements GeorefClient {

    public static final String URL_BASE_DEFAULT = "https://apis.datos.gob.ar/georef/api";

    /** Timeout de conexión por defecto: 2 segundos. */
    public static final Duration TIMEOUT_CONEXION_DEFAULT = Duration.ofSeconds(2);

    /** Timeout de lectura por defecto: 5 segundos. */
    public static final Duration TIMEOUT_LECTURA_DEFAULT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public GeorefClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    public GeorefClientHttp(String baseUrl) {
        this(baseUrl, TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    public GeorefClientHttp(String baseUrl, Duration timeoutConexion, Duration timeoutLectura) {
        this(crearRestClient(baseUrl, timeoutConexion, timeoutLectura));
    }

    public GeorefClientHttp() {
        this(URL_BASE_DEFAULT);
    }

    private static RestClient crearRestClient(String baseUrl, Duration timeoutConexion, Duration timeoutLectura) {
        // Configuramos SimpleClientHttpRequestFactory con los timeouts estándar (2s conexión, 5s lectura).
        // Se utiliza SimpleClientHttpRequestFactory directamente para mantener la independencia de las ramas
        // y aplicar el principio de menor acoplamiento, evitando acoplar un cliente externo público a la fábrica
        // o a los interceptores de seguridad de la comunicación interna entre microservicios.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutConexion);
        factory.setReadTimeout(timeoutLectura);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
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

