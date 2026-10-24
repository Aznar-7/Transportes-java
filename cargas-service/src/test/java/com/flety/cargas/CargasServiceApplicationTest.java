package com.flety.cargas;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.CargaRepository;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import com.flety.cargas.mercado.PropuestaRepository;
import com.flety.cargas.mercado.TipoPropuesta;
import com.flety.cargas.mercado.Ubicacion;
import com.flety.commons.contrato.TipoCarroceria;
import com.flety.commons.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Levanta todo el servicio contra H2 (perfil test): si las entidades estuvieran mal mapeadas o faltara un bean, falla acá.
 */
@SpringBootTest
@ActiveProfiles("test")
class CargasServiceApplicationTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private CargaRepository cargas;

    @Autowired
    private PropuestaRepository propuestas;

    @Test
    void elContextoLevantaConH2YRecibeElHandlerDeCommons() {
        assertThat(contexto.getBean(GlobalExceptionHandler.class)).isNotNull();
    }

    @Test
    void guardaUnaCargaPublicadaConVersionCero() {
        Carga carga = cargas.save(nuevaCarga());

        assertThat(carga.getId()).isNotNull();
        assertThat(carga.getVersion()).isZero();
        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.PUBLICADA);
        assertThat(cargas.findById(carga.getId()).orElseThrow().getCarroceriasAceptadas())
                .containsExactlyInAnyOrder(TipoCarroceria.SIDER, TipoCarroceria.FURGON);
    }

    @Test
    void versionRechazaUnCambioHechoSobreUnaCopiaVieja() {
        Long id = cargas.save(nuevaCarga()).getId();
        Carga primera = cargas.findById(id).orElseThrow();
        Carga segunda = cargas.findById(id).orElseThrow();

        primera.setEstado(EstadoCarga.CON_PROPUESTAS);
        assertThat(cargas.save(primera).getVersion()).isEqualTo(1L);

        segunda.setEstado(EstadoCarga.CANCELADA);
        assertThatThrownBy(() -> cargas.save(segunda))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void guardaUnaPropuestaPendienteDeUnaCarga() {
        Carga carga = cargas.save(nuevaCarga());

        Propuesta propuesta = new Propuesta();
        propuesta.setCarga(carga);
        propuesta.setTransportistaSub("sub-transportista1");
        propuesta.setTransportistaId(3L);
        propuesta.setUnidadId(10L);
        propuesta.setRetornoId(20L);
        propuesta.setTipo(TipoPropuesta.ACEPTA_PRECIO);
        propuesta.setPrecio(carga.getPrecioOfrecido());
        propuesta.setVencimiento(Instant.now().plus(24, ChronoUnit.HOURS));

        Propuesta guardada = propuestas.save(propuesta);

        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(guardada.getCreadaEn()).isNotNull();
    }

    private static Carga nuevaCarga() {
        Instant manana = Instant.now().plus(1, ChronoUnit.DAYS);
        Carga carga = new Carga();
        carga.setDadorSub("sub-dador1");
        carga.setOrigen(new Ubicacion("Córdoba", "Córdoba", -31.4201, -64.1888, "Av. Colón 1000"));
        carga.setDestino(new Ubicacion("Santa Fe", "Rosario", -32.9468, -60.6393, "Bv. Oroño 500"));
        carga.setCargaDesde(manana);
        carga.setCargaHasta(manana.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(manana.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(manana.plus(2, ChronoUnit.DAYS));
        carga.setRubro("Alimentos secos");
        carga.setPesoToneladas(new BigDecimal("12.50"));
        carga.setVolumenM3(new BigDecimal("40.00"));
        carga.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.SIDER, TipoCarroceria.FURGON));
        carga.setPrecioOfrecido(new BigDecimal("850000.00"));
        carga.setValorDeclarado(new BigDecimal("12000000.00"));
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        return carga;
    }
}
