package com.flety.transporte.transportista;

import com.flety.transporte.TokensDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class TransportistaControllerTest {

    private static final String URL = "/api/transporte/transportistas";
    private static final String PEREZ = """
            {"cuit": "30712345678", "razonSocial": "Transportes Pérez SRL", "telefono": "+5493514445566"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository repository;

    @Test
    void conCuitNuevoDevuelve201YElDuenoEsMiSub() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(PEREZ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_VERIFICACION"))
                .andExpect(jsonPath("$.ownerSub").doesNotExist());

        assertThat(repository.findByOwnerSub("sub-transportista1")).isPresent();
    }

    @Test
    void conCuitExistenteDevuelve409() throws Exception {
        repository.save(new Transportista("sub-otro", "30712345678", "Otra SRL", "3514440000"));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(PEREZ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un transportista con CUIT 30712345678"));
    }

    @Test
    void siMiUsuarioYaTieneOrganizacionDevuelve409() throws Exception {
        repository.save(new Transportista("sub-transportista1", "20111111112", "Shrek SA", "3514440000"));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(PEREZ))
                .andExpect(status().isConflict());
    }

    @Test
    void conDatosInvalidosDevuelve400ConLosCampos() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuit\": \"30-71234567-8\", \"razonSocial\": \"\", \"telefono\": \"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cuit").exists())
                .andExpect(jsonPath("$.errores.razonSocial").exists())
                .andExpect(jsonPath("$.errores.telefono").exists());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(PEREZ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevuelveMisDatos() throws Exception {
        repository.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "+5493514445566"));

        mockMvc.perform(get(URL + "/me").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuit").value("30712345678"))
                .andExpect(jsonPath("$.razonSocial").value("Transportes Pérez SRL"));
    }

    @Test
    void meSinOrganizacionDevuelve404() throws Exception {
        mockMvc.perform(get(URL + "/me").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA))
                .andExpect(status().isNotFound());
    }
}
