package com.flety.transporte;

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
 * Igual que en cargas-service: todos los errores salen como ProblemDetail (GlobalExceptionHandler de flety-commons),
 * así que se completa una sola vez acá para cada respuesta 4xx y 5xx en vez de repetirlo en cada @ApiResponse.
 * El esquema Bearer y el botón Authorize vienen configurados de forma común desde flety-commons (FLY-38).
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "transporte-service", version = "1.0",
        description = "Transportistas, unidades, documentos, habilitación y retornos"))
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
