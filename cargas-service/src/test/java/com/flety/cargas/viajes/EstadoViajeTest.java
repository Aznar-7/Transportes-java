package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ReglaNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La interfaz del patrón State, sin Spring ni base: un estado que no sobrescribe nada prohíbe las seis acciones.
 * Los defaults no tocan el viaje, por eso alcanza con pasar null.
 */
class EstadoViajeTest {

    /** EstadoViaje tiene un solo método abstracto (tipo), así que un estado que no permite nada es una lambda. */
    private final EstadoViaje sinAcciones = () -> TipoEstadoViaje.CONFORMADO;

    @Test
    void porDefectoLasSeisAccionesEstanProhibidas() {
        assertNoPermitida(() -> sinAcciones.cargar(null, "sub"), "cargar");
        assertNoPermitida(() -> sinAcciones.iniciarTransito(null, "sub"), "iniciar el tránsito");
        assertNoPermitida(() -> sinAcciones.registrarEntrega(null, new DatosEntrega("R-1", "Ana", "30111222"), "sub"),
                "registrar la entrega");
        assertNoPermitida(() -> sinAcciones.conformar(null, ConformadoPor.DADOR, "sub"), "dar conformidad");
        assertNoPermitida(() -> sinAcciones.disputar(null, "Faltante", "Faltan 2 pallets", "sub"), "abrir una disputa");
        assertNoPermitida(() -> sinAcciones.cancelar(null, "Se rompió el camión", "sub"), "cancelar");
    }

    private static void assertNoPermitida(Runnable accion, String nombre) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(TransicionNoPermitidaException.class)
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El viaje está CONFORMADO: no se puede " + nombre);
    }
}
