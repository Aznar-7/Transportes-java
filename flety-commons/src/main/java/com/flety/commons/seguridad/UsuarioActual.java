package com.flety.commons.seguridad;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quién hizo el pedido: el sub del token y sus roles. Los services lo inyectan para verificar al dueño
 * de un recurso (RN-T1), por ejemplo comparando el id del dador de una carga con usuarioActual.id().
 * Lee el SecurityContext del hilo del pedido, así que un mismo bean sirve para todos los pedidos a la vez.
 */
public class UsuarioActual {

    /** El sub del JWT: el id del usuario en Keycloak, que no cambia aunque cambie su username. */
    public String id() {
        return autenticacion().getName();
    }

    /** Los roles sin el prefijo ROLE_, tal como están en Keycloak: TRANSPORTISTA, DADOR, OPERADOR. */
    public Set<String> roles() {
        return autenticacion().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad != null && autoridad.startsWith(RolesKeycloakConverter.PREFIJO))
                .map(autoridad -> autoridad.substring(RolesKeycloakConverter.PREFIJO.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean tieneRol(String rol) {
        return roles().contains(rol);
    }

    /** Sin usuario no hay a quién atribuirle el pedido: se responde 401 en vez de seguir con un id inventado. */
    private Authentication autenticacion() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                || !autenticacion.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado en este pedido");
        }
        return autenticacion;
    }
}
