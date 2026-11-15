package com.flety.commons.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Autoconfiguración de OpenAPI con esquema Bearer y seguridad global (FLY-38)")
class OpenApiAutoConfigurationTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class));

    @Test
    @DisplayName("Agrega el esquema Bearer JWT y el requisito de seguridad global")
    void agregaElEsquemaYElRequisitoGlobal() {
        OpenAPI api = new OpenAPI().info(new Info().title("cargas-service"));

        OpenApiAutoConfiguration.agregarSeguridadBearer(api);

        SecurityScheme esquema = api.getComponents().getSecuritySchemes().get(OpenApiAutoConfiguration.ESQUEMA_BEARER);
        assertThat(esquema.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(esquema.getScheme()).isEqualTo("bearer");
        assertThat(esquema.getBearerFormat()).isEqualTo("JWT");
        // Sin este requisito, Swagger UI muestra el botón Authorize pero no manda el token en "Try it out".
        assertThat(api.getSecurity()).containsExactly(new SecurityRequirement().addList("bearer"));
    }

    @Test
    @DisplayName("No pisa lo que el servicio ya declaró: título, otros esquemas y requisitos")
    void noPisaLoDelServicio() {
        OpenAPI api = new OpenAPI()
                .info(new Info().title("transporte-service"))
                .components(new Components().addSecuritySchemes("apiKey", new SecurityScheme().type(SecurityScheme.Type.APIKEY)))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"));

        OpenApiAutoConfiguration.agregarSeguridadBearer(api);

        assertThat(api.getInfo().getTitle()).isEqualTo("transporte-service");
        assertThat(api.getComponents().getSecuritySchemes()).containsKeys("apiKey", "bearer");
        assertThat(api.getSecurity()).hasSize(2);
    }

    @Test
    @DisplayName("Es idempotente: aplicarlo dos veces no duplica el requisito")
    void esIdempotente() {
        OpenAPI api = new OpenAPI();

        OpenApiAutoConfiguration.agregarSeguridadBearer(api);
        OpenApiAutoConfiguration.agregarSeguridadBearer(api);

        assertThat(api.getSecurity()).hasSize(1);
        assertThat(api.getComponents().getSecuritySchemes()).hasSize(1);
    }

    @Test
    @DisplayName("Con springdoc en el classpath registra el customizer")
    void conSpringdocRegistraElCustomizer() {
        contexto.run(ctx -> assertThat(ctx).hasBean(OpenApiAutoConfiguration.NOMBRE_BEAN)
                .getBean(OpenApiAutoConfiguration.NOMBRE_BEAN).isInstanceOf(OpenApiCustomizer.class));
    }

    @Test
    @DisplayName("Sin springdoc en el classpath no se activa (es una dependencia opcional)")
    void sinSpringdocNoSeActiva() {
        contexto.withClassLoader(new FilteredClassLoader(OpenApiCustomizer.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OpenApiAutoConfiguration.NOMBRE_BEAN));
    }

    @Test
    @DisplayName("Un servicio lo reemplaza definiendo un bean con el mismo nombre")
    void unServicioLoPuedeReemplazar() {
        contexto.withUserConfiguration(CustomizerPropio.class)
                .run(ctx -> assertThat(ctx.getBean(OpenApiAutoConfiguration.NOMBRE_BEAN))
                        .isSameAs(CustomizerPropio.PROPIO));
    }

    @Test
    @DisplayName("Está registrada en AutoConfiguration.imports")
    void estaRegistradaEnAutoConfigurationImports() throws Exception {
        String recurso = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(recurso);
        assertThat(stream).as("El archivo AutoConfiguration.imports debe existir en el classpath").isNotNull();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            List<String> lineas = reader.lines().map(String::trim).toList();
            assertThat(lineas).contains("com.flety.commons.openapi.OpenApiAutoConfiguration");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerPropio {
        static final OpenApiCustomizer PROPIO = api -> { };

        @Bean(OpenApiAutoConfiguration.NOMBRE_BEAN)
        OpenApiCustomizer seguridadBearerOpenApiCustomizer() {
            return PROPIO;
        }
    }
}
