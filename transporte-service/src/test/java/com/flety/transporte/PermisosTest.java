package com.flety.transporte;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada endpoint público de transporte con un rol que no le corresponde: tiene que dar 403 aunque el Gateway
 * no estuviera (defensa en profundidad). Los 403 por dueño están en el test de cada módulo.
 * Los cuerpos son válidos a propósito: Spring valida el @Valid antes de chequear el @PreAuthorize,
 * así que con un cuerpo inválido el test vería un 400 y no probaría el rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TokensDePrueba.class)
@Transactional
class PermisosTest {

    private static final String DADOR = TokensDePrueba.DADOR_PARA_INTERNO;
    private static final String TRANSPORTISTA = TokensDePrueba.TRANSPORTISTA;

    private static final String TRANSPORTISTA_NUEVO = """
            {"cuit": "30546671166", "razonSocial": "Transportes Pérez SRL", "telefono": "+5493514445566"}""";
    private static final String UNIDAD_NUEVA = """
            {"dominio": "AB123CD", "tipoCarroceria": "SIDER", "capacidadToneladas": 28.0, "capacidadM3": 90.0}""";
    private static final String DOCUMENTO_NUEVO = """
            {"tipoDocumentoId": 1, "numero": "A-1", "vigencia": "2099-01-01T00:00:00Z"}""";
    private static final String RETORNO_NUEVO = """
            {"unidadId": 1,
             "origen": {"provincia": "Santa Fe", "localidad": "Rosario", "lat": -32.9468, "lng": -60.6393},
             "destino": {"provincia": "Córdoba", "localidad": "Córdoba", "lat": -31.4201, "lng": -64.1888},
             "salidaDesde": "2099-01-01T00:00:00Z", "salidaHasta": "2099-01-02T00:00:00Z"}""";

    @Autowired
    MockMvc mockMvc;

    static Stream<Arguments> endpointsConElRolEquivocado() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/transporte/transportistas", TRANSPORTISTA_NUEVO, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/transportistas/me", null, DADOR),
                Arguments.of(HttpMethod.POST, "/api/transporte/unidades", UNIDAD_NUEVA, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/unidades", null, DADOR),
                Arguments.of(HttpMethod.POST, "/api/transporte/unidades/1/documentos", DOCUMENTO_NUEVO, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/unidades/1/habilitacion", null, DADOR),
                Arguments.of(HttpMethod.POST, "/api/transporte/retornos", RETORNO_NUEVO, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/retornos", null, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/retornos/1", null, DADOR),
                Arguments.of(HttpMethod.DELETE, "/api/transporte/retornos/1", null, DADOR),
                Arguments.of(HttpMethod.GET, "/api/transporte/operador/transportistas", null, TRANSPORTISTA),
                Arguments.of(HttpMethod.PATCH, "/api/transporte/operador/transportistas/1/verificacion", null, TRANSPORTISTA),
                Arguments.of(HttpMethod.GET, "/api/transporte/operador/documentos", null, TRANSPORTISTA),
                Arguments.of(HttpMethod.PATCH, "/api/transporte/operador/documentos/1/revision", "{\"aprobado\": true}", TRANSPORTISTA));
    }

    @ParameterizedTest(name = "{0} {1} con el rol equivocado da 403")
    @MethodSource("endpointsConElRolEquivocado")
    void conElRolEquivocadoDevuelve403(HttpMethod metodo, String path, String cuerpo, String token) throws Exception {
        var pedido = request(metodo, path).header(HttpHeaders.AUTHORIZATION, token);
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        mockMvc.perform(pedido).andExpect(status().isForbidden());
    }
}
