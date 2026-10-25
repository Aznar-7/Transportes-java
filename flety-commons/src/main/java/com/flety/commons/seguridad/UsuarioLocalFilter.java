package com.flety.commons.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Solo con la seguridad apagada (perfil local): hace de cuenta que todos los pedidos vienen del usuario local
 * configurado. Así UsuarioActual y @PreAuthorize siguen funcionando sin Keycloak, en vez de romperse.
 */
public class UsuarioLocalFilter extends OncePerRequestFilter {

    private final String id;
    private final List<GrantedAuthority> autoridades;

    public UsuarioLocalFilter(SeguridadProperties.UsuarioLocal usuario) {
        this.id = usuario.id();
        this.autoridades = usuario.roles().stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(RolesKeycloakConverter.PREFIJO + rol))
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id, null, autoridades));
        SecurityContextHolder.setContext(contexto);
        chain.doFilter(request, response);
    }
}
