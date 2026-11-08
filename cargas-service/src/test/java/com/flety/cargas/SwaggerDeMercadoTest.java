package com.flety.cargas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lee el /v3/api-docs que genera springdoc y compara cada endpoint de mercado con los códigos que responde de verdad
 * (los prueban los tests de cada controller). Si alguien cambia un código y no el Swagger, o al revés, falla acá.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerDeMercadoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper mapper;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "post,   /api/dadores,                        201 400 401 403 409",
            "get,    /api/dadores/me,                     200 401 403 404",
            "post,   /api/cargas,                         201 400 401 403 422 503",
            "get,    /api/cargas,                         200 400 401 403",
            "get,    /api/cargas/{id},                    200 401 403 404",
            "delete, /api/cargas/{id},                    204 401 403 404 409",
            "get,    /api/cargas/de-paso,                 200 400 401 403 404 409 503",
            "post,   /api/cargas/{id}/propuestas,         201 400 401 403 404 409 422 503",
            "get,    /api/cargas/{id}/propuestas,         200 401 403 404",
            "delete, /api/cargas/propuestas/{id},         204 401 403 404 409",
            "post,   /api/cargas/propuestas/{id}/rechazo, 200 401 403 404 409"
    })
    void cadaEndpointMuestraLosCodigosReales(String metodo, String ruta, String codigos) throws Exception {
        JsonNode respuestas = apiDocs().path("paths").path(ruta).path(metodo).path("responses");

        Set<String> documentados = new HashSet<>(respuestas.propertyNames());
        assertThat(documentados).containsExactlyInAnyOrderElementsOf(List.of(codigos.split(" ")));
    }

    @Test
    void losErroresSalenComoProblemDetail() throws Exception {
        JsonNode error = apiDocs().path("paths").path("/api/cargas").path("post").path("responses").path("422");

        assertThat(error.path("content").path("application/problem+json").path("schema").path("$ref").asString())
                .isEqualTo("#/components/schemas/ProblemDetail");
    }

    @Test
    void laConsultaDeUnaCargaMuestraLasDosVistas() throws Exception {
        JsonNode vistas = apiDocs().path("paths").path("/api/cargas/{id}").path("get").path("responses").path("200")
                .path("content").findValue("oneOf");

        assertThat(vistas.toString()).contains("CargaResponse", "CargaReservadaResponse");
    }

    /** Son validaciones del pedido, no datos que el cliente tenga que mandar. */
    @Test
    void lasValidacionesNoAparecenComoCampos() throws Exception {
        JsonNode esquemas = apiDocs().path("components").path("schemas");

        assertThat(esquemas.path("PublicarCargaRequest").path("properties").has("ventanasCoherentes")).isFalse();
        assertThat(esquemas.path("UbicacionRequest").path("properties").has("coordenadasCompletas")).isFalse();
        assertThat(esquemas.path("ProponerRequest").path("properties").has("precioCoherente")).isFalse();
    }

    private JsonNode apiDocs() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }
}
