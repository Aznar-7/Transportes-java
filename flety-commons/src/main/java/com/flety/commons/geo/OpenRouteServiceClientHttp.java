package com.flety.commons.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flety.commons.excepciones.ServicioExternoException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cliente HTTP para calcular rutas en la API pública de OpenRouteService (FLY-37).
 * <p>
 * Reglas de negocio:
 * </p>
 * <ul>
 *     <li>Consulta la ruta entre dos o más coordenadas por POST a {@code /v2/directions/driving-car/geojson}.</li>
 *     <li>Envía las coordenadas en formato GeoJSON [longitud, latitud].</li>
 *     <li>Convierte la distancia a kilómetros y la duración a minutos en {@link Ruta}.</li>
 *     <li>Si la API no responde, se cae o responde sin ruta, lanza {@link ServicioExternoException}.</li>
 * </ul>
 */
public class OpenRouteServiceClientHttp implements RoutingClient {

    public static final String URL_BASE_DEFAULT = "https://api.openrouteservice.org";

    /** Timeout de conexión por defecto: 2 segundos. */
    public static final Duration TIMEOUT_CONEXION_DEFAULT = Duration.ofSeconds(2);

    /** Timeout de lectura por defecto: 5 segundos. */
    public static final Duration TIMEOUT_LECTURA_DEFAULT = Duration.ofSeconds(5);

    private final RestClient restClient;

    public OpenRouteServiceClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    public OpenRouteServiceClientHttp(String baseUrl) {
        this(baseUrl, TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    public OpenRouteServiceClientHttp(String baseUrl, Duration timeoutConexion, Duration timeoutLectura) {
        this(crearRestClient(baseUrl, timeoutConexion, timeoutLectura));
    }

    public OpenRouteServiceClientHttp() {
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
    public Ruta calcularRuta(List<Coordenada> puntos) {
        if (puntos == null || puntos.size() < 2) {
            throw new IllegalArgumentException("Una ruta necesita al menos dos puntos");
        }
        if (puntos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Los puntos de la ruta no pueden ser nulos");
        }

        List<List<Double>> coordenadasOrs = puntos.stream()
                .map(p -> List.of(p.lng(), p.lat()))
                .toList();

        OrsPeticion peticion = new OrsPeticion(coordenadasOrs);

        OrsRespuesta respuesta;
        try {
            respuesta = restClient.post()
                    .uri("/v2/directions/driving-car/geojson")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .retrieve()
                    .body(OrsRespuesta.class);
        } catch (RestClientException ex) {
            throw new ServicioExternoException("OpenRouteService no responde o devolvió un error", ex);
        }

        if (respuesta == null || respuesta.features() == null || respuesta.features().isEmpty()) {
            throw new ServicioExternoException("OpenRouteService no encontró una ruta válida para los puntos especificados");
        }

        Feature primerFeature = respuesta.features().get(0);
        if (primerFeature == null || primerFeature.properties() == null || primerFeature.properties().summary() == null) {
            throw new ServicioExternoException("OpenRouteService devolvió una respuesta incompleta sin datos de resumen");
        }

        Summary summary = primerFeature.properties().summary();
        if (summary.distance() == null || summary.duration() == null) {
            throw new ServicioExternoException("OpenRouteService devolvió una respuesta incompleta sin distancia o duración");
        }

        double distanciaKm = summary.distance() / 1000.0;
        double duracionMin = summary.duration() / 60.0;

        List<Coordenada> puntosRuta = new ArrayList<>();
        if (primerFeature.geometry() != null && primerFeature.geometry().coordinates() != null) {
            for (List<Double> coord : primerFeature.geometry().coordinates()) {
                if (coord != null && coord.size() >= 2) {
                    // OpenRouteService devuelve GeoJSON [longitud, latitud]
                    puntosRuta.add(new Coordenada(coord.get(1), coord.get(0)));
                }
            }
        }

        if (puntosRuta.isEmpty()) {
            puntosRuta = List.copyOf(puntos);
        }

        return new Ruta(distanciaKm, duracionMin, puntosRuta);
    }

    record OrsPeticion(List<List<Double>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrsRespuesta(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Feature(Geometry geometry, Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(Summary summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Summary(Double distance, Double duration) {
    }
}

