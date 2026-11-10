package com.flety.commons.http;

import com.flety.commons.excepciones.ServicioNoDisponibleException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor para llamadas HTTP salientes con RestClient (FLY-35).
 * <p>
 * Reglas de resiliencia:
 * <ul>
 *     <li>Reintento único en peticiones idempotentes: Si un pedido GET falla por timeout/red (IOException)
 *         o recibe un error de servidor (5xx), realiza un único reintento (máximo 2 llamadas en total).</li>
 *     <li>Peticiones no idempotentes (POST, PUT, DELETE, PATCH): No se reintentan para evitar duplicación.</li>
 *     <li>Mapeo de errores: Ante fallas no recuperables de conectividad/timeout o respuestas 5xx,
 *         mapea la excepción a {@link ServicioNoDisponibleException} (falla cerrada con 503).</li>
 * </ul>
 */
public class ReintentoGetInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (request.getMethod() != HttpMethod.GET) {
            return ejecutarSinReintento(request, body, execution);
        }

        // Intento 1
        ClientHttpResponse response = null;
        boolean debeReintentar = false;
        try {
            response = execution.execute(request, body);
            if (response.getStatusCode().is5xxServerError()) {
                response.close();
                debeReintentar = true;
            }
        } catch (IOException ex) {
            debeReintentar = true;
        }

        if (!debeReintentar) {
            return response;
        }

        // Intento 2 (Único reintento permitido).
        // NOTA: Invocar execution.execute(...) dos veces funciona únicamente porque este
        // interceptor se ubica como el último eslabón de la cadena (fijado por FabricaRestClient).
        // Si existiera otro interceptor posterior en la cadena, el reintento lo saltearía.
        try {
            ClientHttpResponse retryResponse = execution.execute(request, body);
            if (retryResponse.getStatusCode().is5xxServerError()) {
                HttpStatusCode status = retryResponse.getStatusCode();
                retryResponse.close();
                throw new ServicioNoDisponibleException(
                        "Servicio remoto respondió " + status.value() + " en " + request.getURI());
            }
            return retryResponse;
        } catch (IOException ex) {
            throw new ServicioNoDisponibleException(
                    "Servicio remoto no responde en " + request.getURI() + " tras reintento", ex);
        }
    }

    private ClientHttpResponse ejecutarSinReintento(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        try {
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().is5xxServerError()) {
                HttpStatusCode status = response.getStatusCode();
                response.close();
                throw new ServicioNoDisponibleException(
                        "Servicio remoto respondió " + status.value() + " en " + request.getURI());
            }
            return response;
        } catch (IOException ex) {
            throw new ServicioNoDisponibleException(
                    "Error de comunicación con el servicio remoto en " + request.getURI(), ex);
        }
    }
}
