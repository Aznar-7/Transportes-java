package com.flety.commons.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Configuración común de OpenAPI para los microservicios de Flety (FLY-38): el esquema Bearer (JWT de Keycloak)
 * y el requisito de seguridad global, que es lo que hace que Swagger UI mande el token en "Try it out".
 * <p>
 * Se hace con un {@link OpenApiCustomizer} y no con anotaciones ({@code @SecurityScheme} y
 * {@code @OpenAPIDefinition}) a propósito. Springdoc lee un solo {@code @OpenAPIDefinition}: cada servicio ya tiene
 * el suyo (con el título y la descripción) y el de commons quedaba ignorado, así que el esquema aparecía pero el
 * requisito global no, y Swagger probaba todo sin token (401). El customizer corre después de que springdoc arma el
 * documento con las anotaciones del servicio, así que suma la seguridad sin pisar nada y sin depender de cuál
 * anotación gane.
 * <p>
 * springdoc es una dependencia opcional de commons: esta configuración solo se activa en los servicios que lo tienen
 * en el classpath, sin imponérselo a los demás.
 */
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
public class OpenApiAutoConfiguration {

    /** Nombre del esquema de seguridad Bearer JWT en Flety: es el que referencian los requisitos de seguridad. */
    public static final String ESQUEMA_BEARER = "bearer";

    /** Nombre del bean: un servicio que necesite otra cosa define un bean con este nombre y reemplaza a este. */
    public static final String NOMBRE_BEAN = "seguridadBearerOpenApiCustomizer";

    @Bean(NOMBRE_BEAN)
    @ConditionalOnMissingBean(name = NOMBRE_BEAN)
    public OpenApiCustomizer seguridadBearerOpenApiCustomizer() {
        return OpenApiAutoConfiguration::agregarSeguridadBearer;
    }

    /**
     * Agrega el esquema Bearer a los componentes y lo exige en todas las operaciones. Es idempotente: si el servicio
     * ya declaró el esquema o el requisito, no los duplica ni los pisa.
     */
    static void agregarSeguridadBearer(io.swagger.v3.oas.models.OpenAPI api) {
        if (api.getComponents() == null) {
            api.setComponents(new Components());
        }
        if (api.getComponents().getSecuritySchemes() == null
                || !api.getComponents().getSecuritySchemes().containsKey(ESQUEMA_BEARER)) {
            api.getComponents().addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT de Keycloak del usuario autenticado"));
        }
        boolean yaLoExige = api.getSecurity() != null
                && api.getSecurity().stream().anyMatch(requisito -> requisito.containsKey(ESQUEMA_BEARER));
        if (!yaLoExige) {
            api.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER));
        }
    }
}
