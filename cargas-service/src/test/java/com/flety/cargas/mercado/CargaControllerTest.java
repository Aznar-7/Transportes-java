package com.flety.cargas.mercado;

import com.flety.cargas.TokensDePrueba;
import com.flety.cargas.viajes.ViajesDePrueba;
import com.flety.commons.contrato.TipoCarroceria;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TokensDePrueba.class, ViajesDePrueba.class})
@Transactional
class CargaControllerTest {

    private static final String URL = "/api/cargas";
    private static final Instant CARGA_DESDE = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private static final String ORIGEN_CORDOBA =
            "{\"provincia\": \"Córdoba\", \"localidad\": \"Córdoba\", \"direccion\": \"Av. Colón 1000\"}";
    private static final String DESTINO_ROSARIO =
            "{\"provincia\": \"Santa Fe\", \"localidad\": \"Rosario\", \"direccion\": \"Bv. Oroño 500\"}";
    private static final String DECLARA_TODO = "\"declaraNoPeligrosa\": true, \"declaraNoSobredimensionada\": true";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DadorRepository dadores;

    @Autowired
    CargaRepository cargas;

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @BeforeEach
    void dadorRegistrado() {
        dadores.save(new Dador("sub-dador1", "30546671166", "Acme SA", "+5493514445566", true));
    }

    @Test
    void conTodoBienDevuelve201YQuedaPublicadaDelDadorDelToken() throws Exception {
        String respuesta = publicar(TokensDePrueba.DADOR, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.estado").value("PUBLICADA"))
                .andExpect(jsonPath("$.origen.direccion").value("Av. Colón 1000"))
                .andExpect(jsonPath("$.creadaEn").exists())
                .andExpect(jsonPath("$.dadorSub").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Long id = Long.valueOf(respuesta.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        assertThat(cargas.findById(id).orElseThrow().getDadorSub()).isEqualTo("sub-dador1");
    }

    @Test
    void sinCoordenadasLasPideAGeoref() throws Exception {
        publicar(TokensDePrueba.DADOR, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen.lat").value(-31.4201))
                .andExpect(jsonPath("$.destino.lng").value(-60.6393));
    }

    @Test
    void conCoordenadasLasUsaSinPedirleNadaAGeoref() throws Exception {
        String origen = """
                {"provincia": "Córdoba", "localidad": "Pueblo que Georef no conoce", "direccion": "Ruta 9 km 500",
                 "lat": -31.5, "lng": -64.5}
                """;

        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen.lat").value(-31.5))
                .andExpect(jsonPath("$.origen.lng").value(-64.5));
    }

    @Test
    void siGeorefNoUbicaLaLocalidadDevuelve503ConMensajeClaro() throws Exception {
        String origen = "{\"provincia\": \"Córdoba\", \"localidad\": \"Pueblo que Georef no conoce\", \"direccion\": \"Ruta 9\"}";

        long antes = cargas.count();
        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("No pudimos ubicar la localidad Pueblo que Georef no conoce, Córdoba; enviá las coordenadas"));
        assertThat(cargas.count()).isEqualTo(antes);
    }

    @Test
    void conUnaSolaCoordenadaDevuelve400() throws Exception {
        String origen = "{\"provincia\": \"Córdoba\", \"localidad\": \"Córdoba\", \"direccion\": \"Colón 1\", \"lat\": -31.5}";

        publicar(TokensDePrueba.DADOR, cuerpo(origen, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['origen.coordenadasCompletas']").exists());
    }

    @Test
    void conDatosFaltantesDevuelve400ConLosCampos() throws Exception {
        publicar(TokensDePrueba.DADOR, "{" + DECLARA_TODO + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.origen").exists())
                .andExpect(jsonPath("$.errores.cargaDesde").exists())
                .andExpect(jsonPath("$.errores.rubro").exists())
                .andExpect(jsonPath("$.errores.carroceriasAceptadas").exists());
    }

    @Test
    void conVentanasIncoherentesDevuelve400() throws Exception {
        String cuerpo = """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(ORIGEN_CORDOBA, DESTINO_ROSARIO, CARGA_DESDE, CARGA_DESDE.plus(8, ChronoUnit.HOURS),
                CARGA_DESDE.minus(1, ChronoUnit.DAYS), CARGA_DESDE.plus(1, ChronoUnit.DAYS), DECLARA_TODO);

        publicar(TokensDePrueba.DADOR, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.ventanasCoherentes").exists());
    }

    @Test
    void conUnaVentanaDeCargaYaCerradaDevuelve400() throws Exception {
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String cuerpo = """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(ORIGEN_CORDOBA, DESTINO_ROSARIO, ayer.minus(1, ChronoUnit.HOURS), ayer,
                ayer, ayer.plus(5, ChronoUnit.DAYS), DECLARA_TODO);

        publicar(TokensDePrueba.DADOR, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cargaHasta").exists());
    }

    @Test
    void conUnaCarroceriaQueNoExisteDevuelve400() throws Exception {
        String cuerpo = cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO).replace("\"SIDER\"", "\"BICICLETA\"");

        publicar(TokensDePrueba.DADOR, cuerpo).andExpect(status().isBadRequest());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTransportistaNoPuedePublicar() throws Exception {
        publicar(TokensDePrueba.TRANSPORTISTA, cuerpo(ORIGEN_CORDOBA, DESTINO_ROSARIO, DECLARA_TODO))
                .andExpect(status().isForbidden());
    }

    // La base H2 se comparte con otros tests que no hacen rollback: por eso se mira por id y no se cuenta el total.

    @Test
    void listarDevuelveSoloLasMias() throws Exception {
        Long mia = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA).getId();
        Long ajena = cargaGuardada("sub-dador2", "Alimentos secos", EstadoCarga.PUBLICADA).getId();

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(mia.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(ajena.intValue()))));
    }

    @Test
    void listarFiltraPorEstado() throws Exception {
        Long publicada = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA).getId();
        Long cancelada = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.CANCELADA).getId();

        mockMvc.perform(get(URL).param("estado", "CANCELADA")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(cancelada.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(publicada.intValue()))));
    }

    @Test
    void listarFiltraPorRubroSinImportarLasMayusculas() throws Exception {
        Long madera = cargaGuardada("sub-dador1", "Madera", EstadoCarga.PUBLICADA).getId();
        Long acero = cargaGuardada("sub-dador1", "Acero", EstadoCarga.PUBLICADA).getId();

        mockMvc.perform(get(URL).param("rubro", "maDERA")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(madera.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(acero.intValue()))));
    }

    @Test
    void listarConLosDosFiltrosExigeAmbos() throws Exception {
        Long justa = cargaGuardada("sub-dador1", "Vidrio", EstadoCarga.PUBLICADA).getId();
        Long otroEstado = cargaGuardada("sub-dador1", "Vidrio", EstadoCarga.VENCIDA).getId();
        Long otroRubro = cargaGuardada("sub-dador1", "Papel", EstadoCarga.PUBLICADA).getId();

        mockMvc.perform(get(URL).param("rubro", "Vidrio").param("estado", "PUBLICADA")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(justa.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(otroEstado.intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(otroRubro.intValue()))));
    }

    @Test
    void listarConUnEstadoQueNoExisteDevuelve400() throws Exception {
        mockMvc.perform(get(URL).param("estado", "INVENTADO")
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unTransportistaNoPuedeListar() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isForbidden());
    }

    @Test
    void elDadorVeSuCargaCompleta() throws Exception {
        Carga carga = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA);

        mockMvc.perform(get(URL + "/" + carga.getId()).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.DADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.direccion").value("Av. Colón 1000"))
                .andExpect(jsonPath("$.destino.direccion").value("Bv. Oroño 500"))
                .andExpect(jsonPath("$.origen.lat").value(-31.4201))
                .andExpect(jsonPath("$.declaraNoPeligrosa").value(true));
    }

    @Test
    void elTransportistaNoVeDireccionTelefonoNiCoordenadas() throws Exception {
        Carga carga = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA);

        String respuesta = mockMvc.perform(get(URL + "/" + carga.getId())
                        .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen.localidad").value("Córdoba"))
                .andExpect(jsonPath("$.destino.localidad").value("Rosario"))
                .andExpect(jsonPath("$.precioOfrecido").value(850000.00))
                .andExpect(jsonPath("$.origen.direccion").doesNotExist())
                .andExpect(jsonPath("$.destino.direccion").doesNotExist())
                .andExpect(jsonPath("$.origen.lat").doesNotExist())
                .andExpect(jsonPath("$.origen.lng").doesNotExist())
                .andExpect(jsonPath("$.dadorSub").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Por si un campo nuevo la filtra con otro nombre: ni el dato del dador ni la dirección aparecen en ningún lado.
        assertThat(respuesta).doesNotContain("Colón", "Oroño", "sub-dador1", "+549", "Acme");
    }

    @Test
    void otroDadorRecibe403() throws Exception {
        Carga carga = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA);

        mockMvc.perform(get(URL + "/" + carga.getId()).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OTRO_DADOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void elOperadorNoPuedeVerLaCarga() throws Exception {
        Carga carga = cargaGuardada("sub-dador1", "Alimentos secos", EstadoCarga.PUBLICADA);

        mockMvc.perform(get(URL + "/" + carga.getId()).header(HttpHeaders.AUTHORIZATION, TokensDePrueba.OPERADOR))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaCargaQueNoExisteDevuelve404() throws Exception {
        mockMvc.perform(get(URL + "/999999").header(HttpHeaders.AUTHORIZATION, TokensDePrueba.TRANSPORTISTA))
                .andExpect(status().isNotFound());
    }

    @Test
    void darDeBajaLaDejaCanceladaYRechazaLasPropuestasPendientes() throws Exception {
        Carga carga = fixture.cargaAbierta();
        Propuesta pendiente = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        Propuesta retirada = fixture.propuestaPendiente(carga, ViajesDePrueba.unidadNueva());
        retirada.setEstado(EstadoPropuesta.RETIRADA);

        borrar(carga.getId(), TokensDePrueba.DADOR).andExpect(status().isNoContent());

        assertThat(cargas.findById(carga.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCarga.CANCELADA);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoPropuesta.RECHAZADA);
        assertThat(retirada.getEstado()).isEqualTo(EstadoPropuesta.RETIRADA);
    }

    @Test
    void unaCargaConViajeNoSePuedeBorrar() throws Exception {
        Long cargaId = fixture.viajeConfirmado().getCarga().getId();

        borrar(cargaId, TokensDePrueba.DADOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La carga tiene un viaje en curso: cancelá el viaje"));

        assertThat(cargas.findById(cargaId).orElseThrow().getEstado()).isEqualTo(EstadoCarga.ASIGNADA);
    }

    @Test
    void unaCargaYaTerminadaNoSePuedeBorrar() throws Exception {
        for (EstadoCarga terminada : List.of(EstadoCarga.CERRADA, EstadoCarga.CANCELADA, EstadoCarga.VENCIDA)) {
            Carga carga = cargaGuardada("sub-dador1", "Alimentos secos", terminada);

            borrar(carga.getId(), TokensDePrueba.DADOR).andExpect(status().isConflict());
        }
    }

    @Test
    void otroDadorNoPuedeBorrarla() throws Exception {
        Carga carga = fixture.cargaAbierta();

        borrar(carga.getId(), TokensDePrueba.OTRO_DADOR).andExpect(status().isForbidden());

        assertThat(cargas.findById(carga.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCarga.CON_PROPUESTAS);
    }

    @Test
    void unTransportistaNoPuedeBorrarla() throws Exception {
        borrar(fixture.cargaAbierta().getId(), TokensDePrueba.TRANSPORTISTA).andExpect(status().isForbidden());
    }

    @Test
    void borrarUnaCargaQueNoExisteDevuelve404() throws Exception {
        borrar(999_999L, TokensDePrueba.DADOR).andExpect(status().isNotFound());
    }

    private ResultActions borrar(Long id, String token) throws Exception {
        return mockMvc.perform(delete(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token));
    }

    private Carga cargaGuardada(String dadorSub, String rubro, EstadoCarga estado) {
        Carga carga = new Carga();
        carga.setDadorSub(dadorSub);
        carga.setOrigen(new Ubicacion("Córdoba", "Córdoba", -31.4201, -64.1888, "Av. Colón 1000"));
        carga.setDestino(new Ubicacion("Santa Fe", "Rosario", -32.9468, -60.6393, "Bv. Oroño 500"));
        carga.setCargaDesde(CARGA_DESDE);
        carga.setCargaHasta(CARGA_DESDE.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(CARGA_DESDE.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(CARGA_DESDE.plus(2, ChronoUnit.DAYS));
        carga.setRubro(rubro);
        carga.setPesoToneladas(new BigDecimal("12.50"));
        carga.setVolumenM3(new BigDecimal("40.00"));
        carga.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.SIDER));
        carga.setPrecioOfrecido(new BigDecimal("850000.00"));
        carga.setValorDeclarado(new BigDecimal("12000000.00"));
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        carga.setEstado(estado);
        return cargas.save(carga);
    }

    private ResultActions publicar(String token, String cuerpo) throws Exception {
        return mockMvc.perform(post(URL).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /** Una carga válida; cada test cambia lo que quiere probar. */
    private static String cuerpo(String origen, String destino, String declaraciones) {
        return """
                {"origen": %s, "destino": %s, "rubro": "Alimentos secos", "pesoToneladas": 12.5, "volumenM3": 40,
                 "carroceriasAceptadas": ["SIDER"], "precioOfrecido": 850000, "valorDeclarado": 12000000,
                 "cargaDesde": "%s", "cargaHasta": "%s", "descargaDesde": "%s", "descargaHasta": "%s", %s}
                """.formatted(origen, destino, CARGA_DESDE, CARGA_DESDE.plus(8, ChronoUnit.HOURS),
                CARGA_DESDE.plus(1, ChronoUnit.DAYS), CARGA_DESDE.plus(2, ChronoUnit.DAYS), declaraciones);
    }
}
