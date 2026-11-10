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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interceptor para llamadas HTTP salientes con RestClient (FLY-35).
 * <p>
 * Si en el hilo actual hay un usuario autenticado con JWT en el SecurityContext, reenvía ese mismo
 * token en la cabecera {@code Authorization: Bearer <token>} hacia servicios internos.
 * <p>
 * Se eligió filtrar por lista de hosts internos configurables en lugar de exigir
 * registro manual del interceptor en cada cliente por las siguientes razones:
 * <ul>
 *     <li>Defensa en profundidad y seguridad por defecto: previene de forma automática y centralizada
 *         la fuga involuntaria de tokens JWT de los usuarios a terceros (como Georef u OpenRouteService),
 *         incluso si un desarrollador crea un RestClient usando el builder estándar inyectado de Spring.</li>
 *     <li>Menor fricción y bajo acoplamiento: los clientes de microservicios internos del sistema
 *         (como TransporteClientConfig) no requieren cambios en su código ni necesitan recordar
 *         registrar el interceptor de forma explícita.</li>
 *     <li>Configurabilidad: la lista de hosts internos puede adaptarse por entorno mediante
 *         la propiedad {@code flety.http.hosts-internos} (Docker, Kubernetes, pruebas locales).</li>
 * </ul>
 * <p>
 * Reglas:
 * <ul>
 *     <li>Solo aplica a peticiones cuyo host figure en la lista de {@code hostsInternos}.</li>
 *     <li>Si la petición ya posee una cabecera {@code Authorization}, no la sobreescribe.</li>
 *     <li>Si no hay usuario autenticado o no posee token JWT, la petición continúa sin cabecera de autorización.</li>
 * </ul>
 */
public class ReenvioTokenInterceptor implements ClientHttpRequestInterceptor {

    public static final Set<String> HOSTS_INTERNOS_DEFAULT = Set.of(
            "transporte-service", "cargas-service", "localhost"
    );

    private final Set<String> hostsInternos;

    public ReenvioTokenInterceptor() {
        this(HOSTS_INTERNOS_DEFAULT);
    }

    public ReenvioTokenInterceptor(Set<String> hostsInternos) {
        if (hostsInternos != null && !hostsInternos.isEmpty()) {
            this.hostsInternos = hostsInternos.stream()
                    .filter(h -> h != null && !h.isBlank())
                    .map(h -> h.toLowerCase(Locale.ROOT).trim())
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            this.hostsInternos = HOSTS_INTERNOS_DEFAULT;
        }
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (esHostInterno(request.getURI().getHost()) && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            String token = extraerTokenActual();
            if (token != null && !token.isBlank()) {
                request.getHeaders().setBearerAuth(token);
            }
        }
        return execution.execute(request, body);
    }

    private boolean esHostInterno(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return hostsInternos.contains(host.toLowerCase(Locale.ROOT).trim());
    }

    private String extraerTokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    public Set<String> getHostsInternos() {
        return hostsInternos;
    }
}
