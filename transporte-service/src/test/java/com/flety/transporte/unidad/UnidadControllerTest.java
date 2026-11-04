package com.flety.transporte.unidad;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.transporte.TokensDePrueba;
import com.flety.transporte.transportista.Transportista;
import com.flety.transporte.transportista.TransportistaRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class UnidadControllerTest {

    private static final String URL = "/api/transporte/unidades";
    private static final String CAMION = """
            {"dominio": "AB123CD", "tipoCarroceria": "SIDER", "capacidadToneladas": 28.0, "capacidadM3": 90.0}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransportistaRepository transportistas;

    @Autowired
    UnidadRepository unidades;

    Transportista perez;

    @BeforeEach
    void darDeAltaAlTransportista() {
        perez = transportistas.save(new Transportista("sub-transportista1", "30712345678", "Transportes Pérez SRL", "3514445566"));
    }

    @Test
    void conDominioNuevoDevuelve201() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CAMION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.dominio").value("AB123CD"))
                .andExpect(jsonPath("$.tipoCarroceria").value("SIDER"));
    }

    @Test
    void conDominioRepetidoDevuelve409() throws Exception {
        unidades.save(camion(perez, "AB123CD"));

        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CAMION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una unidad con dominio AB123CD"));
    }

    @Test
    void sinOrganizacionRegistradaDevuelve404() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON).content(CAMION))
                .andExpect(status().isNotFound());
    }

    @Test
    void conDatosInvalidosDevuelve400ConLosCampos() throws Exception {
        mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dominio": "ab-123", "capacidadToneladas": -1, "capacidadM3": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.dominio").exists())
                .andExpect(jsonPath("$.errores.tipoCarroceria").exists())
                .andExpect(jsonPath("$.errores.capacidadToneladas").exists())
                .andExpect(jsonPath("$.errores.capacidadM3").exists());
    }

    @Test
    void listaSoloMisUnidades() throws Exception {
        Transportista otro = transportistas.save(new Transportista("sub-transportista2", "20111111112", "Otro SA", "3514440000"));
        unidades.save(camion(perez, "AB123CD"));
        unidades.save(camion(otro, "ZZ999ZZ"));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dominio").value("AB123CD"));
    }

    private static Unidad camion(Transportista duenio, String dominio) {
        return new Unidad(duenio, dominio, TipoCarroceria.SIDER, new BigDecimal("28.00"), new BigDecimal("90.00"));
    }
}
