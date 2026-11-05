package com.flety.commons.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fábrica para construir instancias y builders de RestClient con timeouts homogéneos (FLY-35).
 * <p>
 * Aplica por defecto los timeouts cortos definidos en la arquitectura de Flety:
 * <ul>
 *     <li>Timeout de conexión: 2 segundos.</li>
 *     <li>Timeout de lectura: 5 segundos.</li>
 * </ul>
 */
public final class FabricaRestClient {

    /** Timeout de conexión por defecto: 2 segundos. */
    public static final Duration TIMEOUT_CONEXION_DEFAULT = Duration.ofSeconds(2);

    /** Timeout de lectura por defecto: 5 segundos. */
    public static final Duration TIMEOUT_LECTURA_DEFAULT = Duration.ofSeconds(5);

    private FabricaRestClient() {
    }

    /**
     * Crea un {@link SimpleClientHttpRequestFactory} con los timeouts por defecto.
     */
    public static SimpleClientHttpRequestFactory requestFactory() {
        return requestFactory(TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    /**
     * Crea un {@link SimpleClientHttpRequestFactory} con los timeouts especificados.
     */
    public static SimpleClientHttpRequestFactory requestFactory(Duration timeoutConexion, Duration timeoutLectura) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutConexion);
        factory.setReadTimeout(timeoutLectura);
        return factory;
    }

    /**
     * Crea un {@link RestClient.Builder} con los timeouts por defecto configurados.
     */
    public static RestClient.Builder builder() {
        return builder(TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    /**
     * Crea un {@link RestClient.Builder} con los timeouts especificados.
     */
    public static RestClient.Builder builder(Duration timeoutConexion, Duration timeoutLectura) {
        return RestClient.builder()
                .requestFactory(requestFactory(timeoutConexion, timeoutLectura));
    }

    /**
     * Aplica los timeouts por defecto sobre un builder existente.
     */
    public static RestClient.Builder aplicarTimeouts(RestClient.Builder builder) {
        return aplicarTimeouts(builder, TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    /**
     * Aplica timeouts específicos sobre un builder existente.
     */
    public static RestClient.Builder aplicarTimeouts(RestClient.Builder builder,
                                                    Duration timeoutConexion,
                                                    Duration timeoutLectura) {
        return builder.requestFactory(requestFactory(timeoutConexion, timeoutLectura));
    }

    /**
     * Crea un {@link RestClient} para una URL base dada con los timeouts por defecto.
     */
    public static RestClient crear(String baseUrl) {
        return builder().baseUrl(baseUrl).build();
    }

    /**
     * Crea un {@link RestClient} para una URL base y timeouts específicos.
     */
    public static RestClient crear(String baseUrl, Duration timeoutConexion, Duration timeoutLectura) {
        return builder(timeoutConexion, timeoutLectura).baseUrl(baseUrl).build();
    }
}
