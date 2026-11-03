package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.CargaRepository;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.Propuesta;
import com.flety.cargas.mercado.PropuestaRepository;
import com.flety.cargas.mercado.TipoPropuesta;
import com.flety.cargas.mercado.Ubicacion;
import com.flety.commons.contrato.TipoCarroceria;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

/**
 * Fixture de viajes para los tests: arma la carga y la propuesta aceptada, y crea el viaje como lo hará FLY-26.
 * Así los tests de viajes no esperan a que exista la aceptación de propuestas.
 * Se suma a un test con @Import(ViajesDePrueba.class).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ViajesDePrueba {

    public static final String SUB_DADOR = "sub-dador1";
    public static final String SUB_TRANSPORTISTA = "sub-transportista1";
    /** Otro transportista con rol válido, pero que no es parte de los viajes del fixture. */
    public static final String SUB_TERCERO = "sub-transportista2";

    @Bean
    Fixture fixtureDeViajes(CargaRepository cargas, PropuestaRepository propuestas, ViajeRepository viajes,
                            TransactionTemplate tx) {
        return new Fixture(cargas, propuestas, viajes, tx);
    }

    public record Fixture(CargaRepository cargas, PropuestaRepository propuestas, ViajeRepository viajes,
                          TransactionTemplate tx) {

        /** Viaje CONFIRMADO entre dador1 y transportista1, con la unidad 10 y la carga mañana. */
        public Viaje viajeConfirmado() {
            return viajeConfirmado(10L, Instant.now().plus(1, ChronoUnit.DAYS));
        }

        /** Carga entre cargaDesde y cargaDesde + 2 días (fin de la ventana de descarga). */
        public Viaje viajeConfirmado(Long unidadId, Instant cargaDesde) {
            Carga carga = cargas.save(nuevaCarga(cargaDesde));
            Propuesta propuesta = propuestas.save(nuevaPropuesta(carga, unidadId));
            Viaje viaje = new Viaje(carga, propuesta, "AB123CD",
                    new Contacto("Transportes Pérez SRL", "+54 351 555-1234"),
                    new Contacto("Molinos del Centro SA", "+54 351 555-9876"),
                    SUB_DADOR);
            return viajes.save(viaje);
        }

        /**
         * Avanza el viaje como lo harán las clases de estado de FLY-27: dentro de una transacción y sobre el viaje
         * recién leído (managed), así el evento nuevo se guarda por el cascade PERSIST al terminar.
         */
        public void pasarA(Long viajeId, TipoEstadoViaje estado) {
            tx.executeWithoutResult(t -> viajes.findById(viajeId).orElseThrow()
                    .cambiarEstado(estado, SUB_TRANSPORTISTA, null));
        }
    }

    static Carga nuevaCarga(Instant cargaDesde) {
        Carga carga = new Carga();
        carga.setDadorSub(SUB_DADOR);
        carga.setOrigen(new Ubicacion("Córdoba", "Córdoba", -31.4201, -64.1888, "Av. Colón 1000"));
        carga.setDestino(new Ubicacion("Santa Fe", "Rosario", -32.9468, -60.6393, "Bv. Oroño 500"));
        carga.setCargaDesde(cargaDesde);
        carga.setCargaHasta(cargaDesde.plus(8, ChronoUnit.HOURS));
        carga.setDescargaDesde(cargaDesde.plus(1, ChronoUnit.DAYS));
        carga.setDescargaHasta(cargaDesde.plus(2, ChronoUnit.DAYS));
        carga.setRubro("Alimentos secos");
        carga.setPesoToneladas(new BigDecimal("12.50"));
        carga.setVolumenM3(new BigDecimal("40.00"));
        carga.setCarroceriasAceptadas(EnumSet.of(TipoCarroceria.SIDER));
        carga.setPrecioOfrecido(new BigDecimal("850000.00"));
        carga.setValorDeclarado(new BigDecimal("12000000.00"));
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        carga.setEstado(EstadoCarga.ASIGNADA);
        return carga;
    }

    static Propuesta nuevaPropuesta(Carga carga, Long unidadId) {
        Propuesta propuesta = new Propuesta();
        propuesta.setCarga(carga);
        propuesta.setTransportistaSub(SUB_TRANSPORTISTA);
        propuesta.setTransportistaId(3L);
        propuesta.setUnidadId(unidadId);
        propuesta.setRetornoId(20L);
        propuesta.setTipo(TipoPropuesta.CONTRAOFERTA);
        propuesta.setPrecio(new BigDecimal("800000.00"));
        propuesta.setVencimiento(carga.getCargaHasta());
        return propuesta;
    }
}
