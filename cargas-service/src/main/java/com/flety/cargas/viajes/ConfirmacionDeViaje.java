package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.DadorRepository;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Los pasos con base de datos de aceptar una propuesta (flujo C), cada uno en su propia transacción.
 * <p>
 * Está separado de AceptarPropuestaService a propósito. @Transactional funciona con un proxy que envuelve al bean:
 * si un método del bean llamara a otro método @Transactional del mismo bean, la llamada no pasaría por el proxy
 * y no se abriría ninguna transacción. Con dos beans, cada llamada de AceptarPropuestaService a este pasa por
 * el proxy y la transacción empieza y termina donde dice la anotación.
 */
@Component
public class ConfirmacionDeViaje {

    /** Una carga recibe aceptaciones mientras está abierta; ASIGNADA, CERRADA, CANCELADA o VENCIDA ya no. */
    private static final Set<EstadoCarga> CARGA_ABIERTA = EnumSet.of(EstadoCarga.PUBLICADA, EstadoCarga.CON_PROPUESTAS);

    /** Las propuestas de la unidad que se bloquean para ordenar sus aceptaciones (ver bloquearDeLaCargaYLaUnidad). */
    private static final Set<EstadoPropuesta> PROPUESTAS_ABIERTAS =
            EnumSet.of(EstadoPropuesta.PENDIENTE, EstadoPropuesta.ACEPTADA);

    private final CargasDeViajes cargas;
    private final PropuestasDeViajes propuestas;
    private final ViajeRepository viajes;
    private final ViajesActivosPorUnidad viajesActivos;
    private final DadorRepository dadores;

    public ConfirmacionDeViaje(CargasDeViajes cargas, PropuestasDeViajes propuestas, ViajeRepository viajes,
                               ViajesActivosPorUnidad viajesActivos, DadorRepository dadores) {
        this.cargas = cargas;
        this.propuestas = propuestas;
        this.viajes = viajes;
        this.viajesActivos = viajesActivos;
        this.dadores = dadores;
    }

    /** Lo que el orquestador necesita saber de la propuesta antes de llamar a transporte-service y confirmar. */
    public record DatosAceptacion(Long cargaId, Long unidadId, Instant hasta) {
    }

    /**
     * Primera lectura, sin cambios: valida dueño, estado, vencimiento y riesgo antes de hacer la llamada HTTP.
     * Si algo falla acá se responde enseguida, sin molestar a transporte-service.
     */
    @Transactional(readOnly = true)
    public DatosAceptacion preparar(Long propuestaId, String dadorSub, boolean aceptoRiesgo) {
        Propuesta propuesta = buscar(propuestaId);
        Carga carga = propuesta.getCarga();
        if (!carga.getDadorSub().equals(dadorSub)) {
            throw new AccesoDenegadoException("No sos el dador de esta carga");
        }
        validarQueSigueAbierta(propuesta, carga);
        if (propuesta.isRequiereAceptacionRiesgo() && !aceptoRiesgo) {
            throw new ReglaNegocioException("El valor declarado supera la suma asegurada de la unidad: "
                    + "para aceptar esta propuesta tenés que aceptar el riesgo (aceptoRiesgo = true)");
        }
        // Los documentos de la unidad tienen que estar vigentes hasta el final de la descarga (RN-04).
        return new DatosAceptacion(carga.getId(), propuesta.getUnidadId(), carga.getDescargaHasta());
    }

    /**
     * Crea el viaje CONFIRMADO, acepta la propuesta, rechaza las demás de la carga y deja la carga ASIGNADA.
     * Todo en una sola transacción: o pasan los cuatro cambios o no pasa ninguno.
     * <p>
     * Vuelve a validar el estado porque entre preparar y confirmar pasó la llamada a transporte-service
     * y otro pedido pudo haber aceptado otra propuesta de la misma carga o de la misma unidad.
     *
     * @param habilitacion lo que respondió transporte-service sobre la unidad: de ahí salen los datos del
     *                     transportista que se copian en el viaje
     * @return el id del viaje creado
     */
    @Transactional
    public Long confirmar(Long propuestaId, DatosAceptacion datos, String dadorSub,
                          HabilitacionUnidadDTO habilitacion) {
        // Primero los bloqueos, siempre en el mismo orden (carga, después propuestas por id), y recién después las
        // lecturas. Así lo que se valida es lo que dejó la aceptación anterior, si había una en curso.
        // Nada se lee antes: una entidad ya cargada en esta transacción no se refresca con el SELECT ... FOR UPDATE.
        Carga carga = cargas.bloquear(datos.cargaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Carga", datos.cargaId()));
        propuestas.bloquearDeLaCargaYLaUnidad(datos.cargaId(), datos.unidadId(), PROPUESTAS_ABIERTAS);
        Propuesta propuesta = buscar(propuestaId);
        validarQueSigueAbierta(propuesta, carga);
        // RN-15: la unidad no puede tener otro viaje activo que se pise con esta carga. Ya lo controló mercado
        // al proponer (FLY-23), pero desde entonces pudo haberse confirmado otro viaje con la misma unidad.
        if (viajesActivos.haySuperposicion(propuesta.getUnidadId(), carga.getCargaDesde(), carga.getDescargaHasta())) {
            throw new ConflictoException("La unidad ya tiene un viaje que se superpone con esta carga (RN-15)");
        }

        // Copia, no referencia: el transportista vive en otra base y el viaje tiene que mostrar con quién se
        // acordó y con qué camión, aunque después cambie su teléfono o la unidad cambie de patente.
        Contacto transportista = new Contacto(habilitacion.transportistaRazonSocial(),
                habilitacion.transportistaTelefono());
        // Para publicar hay que tener la organización registrada (RN-07), así que toda carga nueva la tiene.
        // Una carga publicada antes de esa regla queda sin contacto del dador: eso no frena la aceptación.
        Contacto dador = dadores.findByOwnerSub(dadorSub)
                .map(organizacion -> new Contacto(organizacion.getRazonSocial(), organizacion.getTelefono()))
                .orElse(null);
        Viaje viaje = new Viaje(carga, propuesta, habilitacion.dominio(), transportista, dador, dadorSub);

        propuesta.setEstado(EstadoPropuesta.ACEPTADA);
        propuestas.findByCargaIdAndEstado(carga.getId(), EstadoPropuesta.PENDIENTE).stream()
                .filter(otra -> !otra.getId().equals(propuestaId))
                .forEach(otra -> otra.setEstado(EstadoPropuesta.RECHAZADA));
        // Carga tiene @Version: si otro pedido que no pasó por estos bloqueos la cambió, este UPDATE falla (409).
        carga.setEstado(EstadoCarga.ASIGNADA);

        // saveAndFlush manda los INSERT y UPDATE ahora, dentro del método: si hay un choque de versión,
        // la excepción sale de acá y no recién en el commit.
        return viajes.saveAndFlush(viaje).getId();
    }

    /**
     * La unidad ya no está habilitada: la propuesta pasa a INVALIDADA y nada más (la carga y las demás propuestas
     * siguen igual, el dador puede elegir otra). Es una transacción propia que hace commit antes de que el
     * orquestador responda 422: si el cambio se hiciera dentro de confirmar y después se lanzara la excepción,
     * el rollback se lo llevaría y la propuesta seguiría PENDIENTE.
     * Si otro pedido ya la movió de PENDIENTE, no se toca.
     */
    @Transactional
    public void invalidar(Long propuestaId) {
        Propuesta propuesta = buscar(propuestaId);
        if (propuesta.getEstado() == EstadoPropuesta.PENDIENTE) {
            propuesta.setEstado(EstadoPropuesta.INVALIDADA);
        }
    }

    private Propuesta buscar(Long propuestaId) {
        return propuestas.findById(propuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propuesta", propuestaId));
    }

    /** Las condiciones de estado son 409: el pedido está bien, pero choca con cómo están las cosas ahora. */
    private static void validarQueSigueAbierta(Propuesta propuesta, Carga carga) {
        if (propuesta.getEstado() != EstadoPropuesta.PENDIENTE) {
            throw new ConflictoException("La propuesta ya no está pendiente (está " + propuesta.getEstado() + ")");
        }
        if (!CARGA_ABIERTA.contains(carga.getEstado())) {
            throw new ConflictoException("La carga ya no admite aceptar propuestas (está " + carga.getEstado() + ")");
        }
        if (propuesta.getVencimiento().isBefore(Instant.now())) {
            throw new ConflictoException("La propuesta venció");
        }
    }
}
