package com.flety.commons.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Los 401 y 403 que corta Spring Security pasan antes de llegar a un controller, así que el GlobalExceptionHandler
 * no los vería. Esta clase se los manda igual, a través del HandlerExceptionResolver de Spring MVC,
 * para que salgan como ProblemDetail con el mismo formato que el resto de los errores.
 */
public class ErroresDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public ErroresDeSeguridad(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    /** Sin token, con un token vencido o con la firma adulterada: 401. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (resolver.resolveException(request, response, null, ex) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /** Token válido pero sin el rol que pide el endpoint: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        if (resolver.resolveException(request, response, null, ex) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
