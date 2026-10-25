package com.flety.cargas.transporte;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;
import com.flety.commons.geo.Coordenada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * El cliente contra transporte-service sin transporte-service: MockRestServiceServer se pone en el lugar del
 * servidor y responde con los JSON de ejemplo del contrato (docs/contrato-interno/ejemplos, FLY-4).
 * Se leen de ahí y no de una copia, así que si el contrato cambia, este test se entera.
 */
class TransporteClientHttpTest {

    private static final Path EJEMPLOS = Path.of("..", "docs", "contrato-interno", "ejemplos");
    private static final String BASE = "http://transporte-service:8091";

    private MockRestServiceServer transporte;
    private TransporteClient cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        transporte = MockRestServiceServer.bindTo(builder).build();
        cliente = new TransporteClientHttp(builder.build());
    }

    @Test
    void mapeaElRetornoDelContrato() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/retornos/7")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ejemplo("retorno.json"), MediaType.APPLICATION_JSON));

        RetornoInternoDTO retorno = cliente.obtenerRetorno(7L);

        assertThat(retorno.id()).isEqualTo(7L);
        assertThat(retorno.unidadId()).isEqualTo(12L);
        assertThat(retorno.estado()).isEqualTo("PUBLICADO");
        assertThat(retorno.origen().localidad()).isEqualTo("Rosario");
        assertThat(retorno.destino().lat()).isEqualTo(-31.4201);
        assertThat(retorno.salidaDesde()).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
        assertThat(retorno.desvioMaximoKm()).isEqualTo(30.0);
        assertThat(retorno.rutaPuntos()).hasSize(3).first().isEqualTo(new Coordenada(-32.9468, -60.6393));
        assertThat(retorno.rutaAproximada()).isFalse();
        assertThat(retorno.tipoCarroceria()).isEqualTo(TipoCarroceria.SIDER);
        assertThat(retorno.capacidadToneladas()).isEqualByComparingTo("28.0");
        transporte.verify();
    }

    @Test
    void mapeaElRetornoConRutaAproximada() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/retornos/8"))
                .andRespond(withSuccess(ejemplo("retorno-ruta-aproximada.json"), MediaType.APPLICATION_JSON));

        RetornoInternoDTO retorno = cliente.obtenerRetorno(8L);

        assertThat(retorno.rutaAproximada()).isTrue();
        assertThat(retorno.rutaPuntos()).hasSize(2);
        assertThat(retorno.rutaDuracionMin()).isZero();
    }

    @Test
    void mapeaLaUnidadHabilitadaYMandaHastaEnIso() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/unidades/12/habilitacion?hasta=2026-11-05T21:00:00Z"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ejemplo("habilitacion-habilitada.json"), MediaType.APPLICATION_JSON));

        HabilitacionUnidadDTO habilitacion = cliente.consultarHabilitacion(12L, Instant.parse("2026-11-05T21:00:00Z"));

        assertThat(habilitacion.habilitada()).isTrue();
        assertThat(habilitacion.motivos()).isEmpty();
        assertThat(habilitacion.dominio()).isEqualTo("AB123CD");
        assertThat(habilitacion.sumaAseguradaCarga()).isEqualByComparingTo(new BigDecimal("50000000.00"));
        assertThat(habilitacion.transportistaRazonSocial()).isEqualTo("Transportes Pérez SRL");
        transporte.verify();
    }

    @Test
    void mapeaLaUnidadNoHabilitadaConSusMotivos() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/unidades/12/habilitacion?hasta=2026-11-05T21:00:00Z"))
                .andRespond(withSuccess(ejemplo("habilitacion-no-habilitada.json"), MediaType.APPLICATION_JSON));

        HabilitacionUnidadDTO habilitacion = cliente.consultarHabilitacion(12L, Instant.parse("2026-11-05T21:00:00Z"));

        assertThat(habilitacion.habilitada()).isFalse();
        assertThat(habilitacion.motivos()).hasSize(3).contains("El transportista no está VERIFICADO");
        assertThat(habilitacion.sumaAseguradaCarga()).isNull();
    }

    @Test
    void retornoInexistenteEsRecursoNoEncontrado() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/retornos/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(ejemplo("retorno-404.json")));

        assertThatThrownBy(() -> cliente.obtenerRetorno(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Retorno 99 no existe");
    }

    @Test
    void unidadInexistenteEsRecursoNoEncontrado() throws IOException {
        transporte.expect(requestTo(BASE + "/internal/unidades/99/habilitacion?hasta=2026-11-05T21:00:00Z"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(ejemplo("habilitacion-404.json")));

        assertThatThrownBy(() -> cliente.consultarHabilitacion(99L, Instant.parse("2026-11-05T21:00:00Z")))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Unidad 99 no existe");
    }

    @Test
    void transporteQueNoRespondeATiempoEs503() {
        transporte.expect(requestTo(BASE + "/internal/retornos/7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.obtenerRetorno(7L))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("no responde");
    }

    @Test
    void unErrorInternoDeTransporteEs503() {
        transporte.expect(requestTo(BASE + "/internal/retornos/7"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> cliente.obtenerRetorno(7L))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("500");
    }

    /** Si transporte rechaza el token, cargas no puede decidir nada: también falla cerrado. */
    @Test
    void unTokenRechazadoPorTransporteEs503() {
        transporte.expect(requestTo(BASE + "/internal/retornos/7"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.obtenerRetorno(7L))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("401");
    }

    /** Un JSON que no respeta el contrato (una carrocería que no existe) no se acepta a medias. */
    @Test
    void unaRespuestaFueraDelContratoEs503() throws IOException {
        String roto = ejemplo("retorno.json").replace("\"SIDER\"", "\"CAMIONETA\"");
        transporte.expect(requestTo(BASE + "/internal/retornos/7"))
                .andRespond(withSuccess(roto, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.obtenerRetorno(7L))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void unaRespuestaVaciaEs503() {
        transporte.expect(requestTo(BASE + "/internal/retornos/7"))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> cliente.obtenerRetorno(7L))
                .isInstanceOf(ServicioNoDisponibleException.class)
                .hasMessageContaining("vacío");
    }

    private static String ejemplo(String archivo) throws IOException {
        return Files.readString(EJEMPLOS.resolve(archivo), StandardCharsets.UTF_8);
    }
}
