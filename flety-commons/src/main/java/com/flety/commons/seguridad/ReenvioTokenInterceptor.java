package com.flety.commons.seguridad;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Interceptor para llamadas HTTP salientes con RestClient (FLY-35).
 *
 * Si en el hilo actual hay un usuario autenticado con JWT en el SecurityContext, reenvía ese mismo
 * token en la cabecera {@code Authorization: Bearer <token>} hacia el servicio destino.
 *
 * Reglas:
 *     - Si la petición ya posee una cabecera {@code Authorization}, no la sobreescribe.
 *     - Si no hay usuario autenticado o no posee token, la petición continúa sin cabecera de autorización.
 *
 */
public class ReenvioTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            String token = extraerTokenActual();
            if (token != null && !token.isBlank()) {
                request.getHeaders().setBearerAuth(token);
            }
        }
        return execution.execute(request, body);
    }

    private String extraerTokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }
}