package com.flety.commons.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Autoconfiguración común de OpenAPI / Swagger para los microservicios de Flety (FLY-38).
 * <p>
 * Mediante las anotaciones declarativas {@link SecurityScheme} y {@link OpenAPIDefinition},
 * define el esquema de seguridad {@code bearer} (formato JWT de Keycloak) y su requerimiento
 * global. Cualquier microservicio que consuma {@code flety-commons} y tenga Springdoc OpenAPI
 * en su classpath expone automáticamente el esquema Bearer y el botón "Authorize" en Swagger UI.
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(SecurityScheme.class)
@SecurityScheme(
        name = OpenApiAutoConfiguration.ESQUEMA_BEARER,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT de Keycloak del usuario autenticado"
)
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiAutoConfiguration.ESQUEMA_BEARER))
public class OpenApiAutoConfiguration {

    /** Nombre estándar del esquema de seguridad Bearer JWT en Flety. */
    public static final String ESQUEMA_BEARER = "bearer";
}
