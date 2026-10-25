package com.flety.commons.seguridad;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Traduce los roles de Keycloak al formato de Spring Security.
 * Keycloak los manda en el claim realm_access.roles (por ejemplo ["DADOR"]) y Spring espera autoridades
 * con prefijo ROLE_ (ROLE_DADOR), que son las que miran hasRole('DADOR') y @PreAuthorize.
 * Spring por defecto lee el claim "scope", que Keycloak usa para otra cosa; por eso hace falta este conversor.
 */
public class RolesKeycloakConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    static final String PREFIJO = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO + rol))
                .toList();
    }
}
