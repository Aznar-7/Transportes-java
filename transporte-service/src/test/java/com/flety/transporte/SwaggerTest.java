package com.flety.transporte;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El documento OpenAPI que arma springdoc: público, con los códigos de cada endpoint y el esquema Bearer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void elDocumentoOpenApiEsPublicoYTraeLosCodigosReales() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("transporte-service"))
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/transporte/transportistas'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/transporte/retornos'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/transporte/retornos/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/internal/unidades/{id}/habilitacion'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/transporte/operador/documentos/{id}/revision'].patch.responses['409']"
                        + ".content['application/problem+json']").exists());
    }

    /**
     * El requisito global es lo que hace que Swagger UI mande el token en "Try it out": con el esquema solo, el botón
     * Authorize aparece pero los pedidos salen sin token y dan 401. Lo agrega flety-commons (FLY-38) sin pisar el
     * título que pone el SwaggerConfig de este servicio.
     */
    @Test
    void todasLasOperacionesExigenElTokenBearer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security.length()").value(1))
                .andExpect(jsonPath("$.security[0].bearer").isArray())
                .andExpect(jsonPath("$.info.title").value("transporte-service"));
    }

    @Test
    void laInterfazDeSwaggerTambienEsPublica() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
