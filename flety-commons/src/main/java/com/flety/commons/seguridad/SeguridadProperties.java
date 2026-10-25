package com.flety.commons.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * Propiedades flety.seguridad.* de cada servicio.
 *
 * @param habilitada   false solo en el perfil local, para probar sin levantar Keycloak. Por defecto la seguridad está prendida.
 * @param usuarioLocal con quién se hace de cuenta que vienen los pedidos cuando la seguridad está apagada
 */
@ConfigurationProperties("flety.seguridad")
public record SeguridadProperties(@DefaultValue("true") boolean habilitada,
                                  @DefaultValue UsuarioLocal usuarioLocal) {

    public record UsuarioLocal(@DefaultValue("usuario-local") String id,
                               @DefaultValue({"TRANSPORTISTA", "DADOR", "OPERADOR"}) Set<String> roles) {
    }
}
