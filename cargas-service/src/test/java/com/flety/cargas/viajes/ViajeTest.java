package com.flety.cargas.viajes;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las entidades de viajes contra H2: el viaje nace con su primer evento, cada cambio de estado deja otro,
 * y los eventos no se pueden modificar ni borrar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ViajesDePrueba.class)
class ViajeTest {

    @Autowired
    ViajesDePrueba.Fixture fixture;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @Test
    void naceConfirmadoConLosDatosDeLaPropuestaYSuPrimerEvento() {
        Long id = fixture.viajeConfirmado().getId();

        tx.executeWithoutResult(estado -> {
            Viaje viaje = em.find(Viaje.class, id);
            assertThat(viaje.getEstado()).isEqualTo(TipoEstadoViaje.CONFIRMADO);
            assertThat(viaje.getDadorSub()).isEqualTo(ViajesDePrueba.SUB_DADOR);
            assertThat(viaje.getTransportistaSub()).isEqualTo(ViajesDePrueba.SUB_TRANSPORTISTA);
            assertThat(viaje.getPrecioAcordado()).isEqualByComparingTo(new BigDecimal("800000.00"));
            assertThat(viaje.getContactoTransportista().getTelefono()).isEqualTo("+54 351 555-1234");
            assertThat(viaje.getVersion()).isZero();

            List<EventoViaje> eventos = viaje.getEventos();
            assertThat(eventos).hasSize(1);
            assertThat(eventos.getFirst().getEstadoAnterior()).isNull();
            assertThat(eventos.getFirst().getEstadoNuevo()).isEqualTo(TipoEstadoViaje.CONFIRMADO);
            assertThat(eventos.getFirst().getActorSub()).isEqualTo(ViajesDePrueba.SUB_DADOR);
        });
    }

    @Test
    void cadaCambioDeEstadoDejaUnEventoEnOrden() {
        Long id = fixture.viajeConfirmado().getId();
        fixture.pasarA(id, TipoEstadoViaje.CARGADO);
        fixture.pasarA(id, TipoEstadoViaje.EN_TRANSITO);

        tx.executeWithoutResult(estado -> {
            Viaje guardado = em.find(Viaje.class, id);
            assertThat(guardado.getEstado()).isEqualTo(TipoEstadoViaje.EN_TRANSITO);
            assertThat(guardado.getEventos())
                    .extracting(EventoViaje::getEstadoAnterior, EventoViaje::getEstadoNuevo)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(null, TipoEstadoViaje.CONFIRMADO),
                            org.assertj.core.groups.Tuple.tuple(TipoEstadoViaje.CONFIRMADO, TipoEstadoViaje.CARGADO),
                            org.assertj.core.groups.Tuple.tuple(TipoEstadoViaje.CARGADO, TipoEstadoViaje.EN_TRANSITO));
        });
    }

    @Test
    void laListaDeEventosNoSePuedeTocarDesdeAfuera() {
        Viaje viaje = fixture.viajeConfirmado();

        assertThatThrownBy(() -> viaje.getEventos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unEventoNoSePuedeBorrar() {
        Long eventoId = primerEventoDe(fixture.viajeConfirmado().getId());

        assertThatThrownBy(() -> tx.executeWithoutResult(estado -> {
            em.remove(em.find(EventoViaje.class, eventoId));
            em.flush();
        })).hasStackTraceContaining("append-only");

        assertThat(contarEventos(eventoId)).isOne();
    }

    @Test
    void unEventoNoSePuedeModificar() {
        Long eventoId = primerEventoDe(fixture.viajeConfirmado().getId());

        // No hay setters: se fuerza el cambio por reflexión, como haría un bug que tocara el campo.
        // Hibernate no arma el UPDATE porque ninguna columna es actualizable, así que el cambio muere en memoria.
        tx.executeWithoutResult(estado -> {
            EventoViaje evento = em.find(EventoViaje.class, eventoId);
            ReflectionTestUtils.setField(evento, "actorSub", "otro-usuario");
            ReflectionTestUtils.setField(evento, "detalle", "editado");
            em.flush();
        });

        // Se relee en otra transacción, directo de la base.
        String actor = tx.execute(estado -> em.find(EventoViaje.class, eventoId).getActorSub());
        assertThat(actor).isEqualTo(ViajesDePrueba.SUB_DADOR);
    }

    @Test
    void lasDemasEntidadesSeGuardanColgadasDelViaje() {
        Long id = fixture.viajeConfirmado().getId();

        tx.executeWithoutResult(estado -> {
            Viaje viaje = em.find(Viaje.class, id);
            ComprobanteEntrega comprobante = new ComprobanteEntrega(viaje, "R-0001-00012345", "Ana Gómez", "30111222");
            Disputa disputa = new Disputa(viaje, "Faltante", "Llegaron 18 pallets de 20", ViajesDePrueba.SUB_DADOR);
            em.persist(comprobante);
            em.persist(disputa);
            em.flush();

            assertThat(comprobante.getId()).isNotNull();
            assertThat(comprobante.getRegistradoEn()).isNotNull();
            assertThat(disputa.getEstado()).isEqualTo(EstadoDisputa.ABIERTA);
        });
    }

    private Long primerEventoDe(Long viajeId) {
        return tx.execute(estado -> em.find(Viaje.class, viajeId).getEventos().getFirst().getId());
    }

    private long contarEventos(Long eventoId) {
        return tx.execute(estado -> em.createQuery("select count(e) from EventoViaje e where e.id = :id", Long.class)
                .setParameter("id", eventoId).getSingleResult());
    }
}
