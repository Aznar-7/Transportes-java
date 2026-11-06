package com.flety.commons.http;

import com.flety.commons.seguridad.ReenvioTokenInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fábrica para construir instancias y builders de RestClient con timeouts homogéneos (FLY-35).
 *
 * Aplica por defecto las decisiones de arquitectura de Flety:
 *
 *     Timeout de conexión: 2 segundos.
 *     Timeout de lectura: 5 segundos.
 *     Reenvío transparente del token del usuario con {@link ReenvioTokenInterceptor}.
 *     Un único reintento en GET y mapeo de errores con {@link ReintentoGetInterceptor}.
 *
 */

public final class FabricaRestClient {

    /** Timeout de conexión por defecto: 2 segundos. */
    public static final Duration TIMEOUT_CONEXION_DEFAULT = Duration.ofSeconds(2);

    /** Timeout de lectura por defecto: 5 segundos. */
    public static final Duration TIMEOUT_LECTURA_DEFAULT = Duration.ofSeconds(5);

    private FabricaRestClient() {
    }

    /**
     * Crea un {@link RestClient.Builder} preconfigurado con los timeouts y los interceptores estándar de Flety.
     */
    public static RestClient.Builder builder() {
        return builder(TIMEOUT_CONEXION_DEFAULT, TIMEOUT_LECTURA_DEFAULT);
    }

    /**
     * Crea un {@link RestClient.Builder} con timeouts personalizados y los interceptores estándar de Flety.
     */
    public static RestClient.Builder builder(Duration timeoutConexion, Duration timeoutLectura) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutConexion);
        factory.setReadTimeout(timeoutLectura);

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(new ReenvioTokenInterceptor())
                .requestInterceptor(new ReintentoGetInterceptor());
    }

    /**
     * Crea un {@link RestClient} listo para usar hacia una URL base.
     */
    public static RestClient crear(String baseUrl) {
        return builder().baseUrl(baseUrl).build();
    }
}
