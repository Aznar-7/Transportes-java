package com.flety.commons.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Autoconfiguración de OpenAPI con esquema Bearer (FLY-38)")
class OpenApiAutoConfigurationTest {

    @Test
    @DisplayName("La clase está anotada con @AutoConfiguration y condicionada a la presencia de SecurityScheme")
    void estaConfiguradaComoAutoConfiguration() {
        assertThat(OpenApiAutoConfiguration.class.isAnnotationPresent(AutoConfiguration.class)).isTrue();
        assertThat(OpenApiAutoConfiguration.class.isAnnotationPresent(ConditionalOnClass.class)).isTrue();

        ConditionalOnClass conditional = OpenApiAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);
        assertThat(conditional.value()).contains(SecurityScheme.class);
    }

    @Test
    @DisplayName("La anotación @SecurityScheme define el esquema HTTP Bearer con formato JWT")
    void tieneAnotacionSecuritySchemeConEsquemaBearerJwt() {
        assertThat(OpenApiAutoConfiguration.class.isAnnotationPresent(SecurityScheme.class)).isTrue();

        SecurityScheme scheme = OpenApiAutoConfiguration.class.getAnnotation(SecurityScheme.class);
        assertThat(scheme.name()).isEqualTo(OpenApiAutoConfiguration.ESQUEMA_BEARER);
        assertThat(scheme.type()).isEqualTo(SecuritySchemeType.HTTP);
        assertThat(scheme.scheme()).isEqualTo("bearer");
        assertThat(scheme.bearerFormat()).isEqualTo("JWT");
        assertThat(scheme.description()).contains("JWT de Keycloak");
    }

    @Test
    @DisplayName("La anotación @OpenAPIDefinition incluye el requerimiento de seguridad global bearer")
    void tieneAnotacionOpenAPIDefinitionConRequerimientoBearer() {
        assertThat(OpenApiAutoConfiguration.class.isAnnotationPresent(OpenAPIDefinition.class)).isTrue();

        OpenAPIDefinition definition = OpenApiAutoConfiguration.class.getAnnotation(OpenAPIDefinition.class);
        SecurityRequirement[] requirements = definition.security();
        assertThat(requirements).isNotEmpty();

        boolean contieneBearer = Arrays.stream(requirements)
                .anyMatch(r -> OpenApiAutoConfiguration.ESQUEMA_BEARER.equals(r.name()));
        assertThat(contieneBearer).isTrue();
    }

    @Test
    @DisplayName("Está registrada en META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
    void estaRegistradaEnAutoConfigurationImports() throws Exception {
        String resourcePath = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertThat(stream).as("El archivo AutoConfiguration.imports debe existir en el classpath").isNotNull();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            List<String> lineas = reader.lines().map(String::trim).toList();
            assertThat(lineas).contains("com.flety.commons.openapi.OpenApiAutoConfiguration");
        }
    }
}
