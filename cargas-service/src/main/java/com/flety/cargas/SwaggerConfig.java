package com.flety.cargas;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Todos los errores de la API salen como ProblemDetail (GlobalExceptionHandler de flety-commons). En vez de repetirlo
 * en cada @ApiResponse, se completa una sola vez acá para cada respuesta 4xx y 5xx.
 * El botón Authorize con el esquema Bearer llega con la configuración común de FLY-38.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "cargas-service", version = "1.0",
        description = "Dadores, cargas, búsqueda de cargas de paso, propuestas y viajes"))
public class SwaggerConfig {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String ESQUEMA_ERROR = "ProblemDetail";

    @Bean
    OpenApiCustomizer erroresComoProblemDetail() {
        return api -> {
            Schema<?> problema = new ObjectSchema()
                    .addProperty("type", new StringSchema())
                    .addProperty("title", new StringSchema())
                    .addProperty("status", new IntegerSchema())
                    .addProperty("detail", new StringSchema())
                    .addProperty("instance", new StringSchema())
                    .addProperty("errores", new ObjectSchema().description("Campo y motivo, solo en los 400 de validación"));
            api.getComponents().addSchemas(ESQUEMA_ERROR, problema);
            Content cuerpo = new Content().addMediaType(PROBLEM_JSON,
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ESQUEMA_ERROR)));
            api.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion ->
                    operacion.getResponses().forEach((codigo, respuesta) -> {
                        if (codigo.startsWith("4") || codigo.startsWith("5")) {
                            respuesta.setContent(cuerpo);
                        }
                    })));
        };
    }
}
