package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
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
class DadorControllerTest {

    private static final String URL = "/api/dadores";
    private static final String ACME = """
            {"cuit": "30546671166", "razonSocial": "Acme SA", "telefono": "+5493514445566", "declaraEmpresa": true}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DadorRepository repository;

    @Test
    void conCuitValidoYDeclaracionDevuelve201YElDuenoEsMiSub() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cuit").value("30546671166"))
                .andExpect(jsonPath("$.declaraEmpresa").value(true))
                .andExpect(jsonPath("$.ownerSub").doesNotExist());

        assertThat(repository.existsByOwnerSub("sub-dador1")).isTrue();
    }

    @Test
    void elDuenoSaleDelTokenAunqueVengaEnElCuerpo() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerSub": "sub-dador2", "cuit": "30546671166", "razonSocial": "Acme SA",
                                 "telefono": "+5493514445566", "declaraEmpresa": true}
                                """))
                .andExpect(status().isCreated());

        assertThat(repository.existsByOwnerSub("sub-dador1")).isTrue();
        assertThat(repository.existsByOwnerSub("sub-dador2")).isFalse();
    }

    @Test
    void sinDeclaracionDeEmpresaSeRegistraIgualYQuedaGuardado() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuit": "30546671166", "razonSocial": "Acme SA",
                                 "telefono": "+5493514445566", "declaraEmpresa": false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.declaraEmpresa").value(false));
    }

    @Test
    void conCuitExistenteDevuelve409() throws Exception {
        repository.save(new Dador("sub-otro", "30546671166", "Otra SA", "3514440000", true));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un dador con CUIT 30546671166"));
    }

    @Test
    void siMiUsuarioYaTieneOrganizacionDevuelve409() throws Exception {
        repository.save(new Dador("sub-dador1", "33693450239", "Primera SA", "3514440000", true));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR)
                        .contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Tu usuario ya tiene una organización registrada"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conOtroRolDevuelve403() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(ACME))
                .andExpect(status().isForbidden());
    }

    @Test
    void meDevuelveMisDatos() throws Exception {
        repository.save(new Dador("sub-dador1", "30546671166", "Acme SA", "+5493514445566", true));

        mockMvc.perform(get(URL + "/me").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuit").value("30546671166"))
                .andExpect(jsonPath("$.razonSocial").value("Acme SA"))
                .andExpect(jsonPath("$.ownerSub").doesNotExist());
    }

    @Test
    void meNoDevuelveLaOrganizacionDeOtroDador() throws Exception {
        repository.save(new Dador("sub-dador1", "30546671166", "Acme SA", "+5493514445566", true));

        mockMvc.perform(get(URL + "/me").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_DADOR))
                .andExpect(status().isNotFound());
    }

    @Test
    void meSinOrganizacionDevuelve404() throws Exception {
        mockMvc.perform(get(URL + "/me").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isNotFound());
    }
}
