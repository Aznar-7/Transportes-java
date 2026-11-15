package com.flety.cargas.viajes;

/**
 * Patrón State del viaje (FLY-27). Cada estado del viaje tiene una clase que implementa esta interfaz:
 * sabe qué acciones permite y a qué estado pasa con cada una. El Viaje no decide nada: delega la acción en su
 * estado actual. Así no hay un switch gigante por estado en cada acción, y agregar un estado es agregar una clase.
 * <p>
 * Por defecto toda acción está prohibida: cada estado sobrescribe solo las que permite. Si se llama una acción
 * que el estado no sobrescribió, se lanza TransicionNoPermitidaException (409). Un estado nuevo arranca
 * prohibiendo todo, y hay que habilitar a propósito cada transición: no se escapa ninguna por olvido.
 * <p>
 * Los estados no guardan datos (los datos son del Viaje que reciben por parámetro), así que alcanza una sola
 * instancia de cada uno para todos los viajes.
 */
public interface EstadoViaje {

    /** El valor que se guarda en la base para este estado. */
    TipoEstadoViaje tipo();

    /** El transportista cargó la mercadería. */
    default void cargar(Viaje viaje, String actorSub) {
        throw noPermitida("cargar");
    }

    /** El transportista salió hacia el destino. */
    default void iniciarTransito(Viaje viaje, String actorSub) {
        throw noPermitida("iniciar el tránsito");
    }

    /** El transportista llegó y entregó, con el comprobante firmado por quien recibió. */
    default void registrarEntrega(Viaje viaje, DatosEntrega entrega, String actorSub) {
        throw noPermitida("registrar la entrega");
    }

    /** El dador da conformidad, o el sistema cuando vencen las 48 h sin disputa (FLY-30). */
    default void conformar(Viaje viaje, ConformadoPor por, String actorSub) {
        throw noPermitida("dar conformidad");
    }

    /** El dador reclama sobre la entrega dentro de la ventana de conformidad (FLY-30 y FLY-31). */
    default void disputar(Viaje viaje, String motivo, String descripcion, String actorSub) {
        throw noPermitida("abrir una disputa");
    }

    /** Una de las partes cancela el viaje (FLY-28). */
    default void cancelar(Viaje viaje, String motivo, String actorSub) {
        throw noPermitida("cancelar");
    }

    /**
     * Un operador resuelve la disputa abierta (FLY-31): a favor del transportista el viaje queda CONFORMADO; si no,
     * CERRADO_CON_DISPUTA. Es una séptima acción, aparte de las seis del diseño, porque la hace otro actor (el
     * OPERADOR) y desde un estado propio (EN_DISPUTA); por defecto también está prohibida.
     */
    default void resolverDisputa(Viaje viaje, boolean aFavorDelTransportista, String resolucion, String operadorSub) {
        throw noPermitida("resolver una disputa");
    }

    private TransicionNoPermitidaException noPermitida(String accion) {
        return new TransicionNoPermitidaException(tipo(), accion);
    }
}
