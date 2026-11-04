package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
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

    private final PropuestasDeViajes propuestas;
    private final ViajeRepository viajes;

    public ConfirmacionDeViaje(PropuestasDeViajes propuestas, ViajeRepository viajes) {
        this.propuestas = propuestas;
        this.viajes = viajes;
    }

    /** Lo que el orquestador necesita saber de la propuesta antes de llamar a transporte-service. */
    public record DatosAceptacion(Long unidadId, Instant hasta) {
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
        return new DatosAceptacion(propuesta.getUnidadId(), carga.getDescargaHasta());
    }

    /**
     * Crea el viaje CONFIRMADO, acepta la propuesta, rechaza las demás de la carga y deja la carga ASIGNADA.
     * Todo en una sola transacción: o pasan los cuatro cambios o no pasa ninguno.
     * <p>
     * Vuelve a validar el estado porque entre preparar y confirmar pasó la llamada a transporte-service
     * y otro pedido pudo haber aceptado otra propuesta de la misma carga.
     *
     * @param habilitacion lo que respondió transporte-service sobre la unidad: de ahí salen los datos del
     *                     transportista que se copian en el viaje
     * @return el id del viaje creado
     */
    @Transactional
    public Long confirmar(Long propuestaId, String dadorSub, HabilitacionUnidadDTO habilitacion) {
        Propuesta propuesta = buscar(propuestaId);
        Carga carga = propuesta.getCarga();
        validarQueSigueAbierta(propuesta, carga);

        // Copia, no referencia: el transportista vive en otra base y el viaje tiene que mostrar con quién se
        // acordó y con qué camión, aunque después cambie su teléfono o la unidad cambie de patente.
        Contacto transportista = new Contacto(habilitacion.transportistaRazonSocial(),
                habilitacion.transportistaTelefono());
        // El contacto del dador queda vacío hasta que mercado tenga la entidad Dador (FLY-19, Heron).
        Viaje viaje = new Viaje(carga, propuesta, habilitacion.dominio(), transportista, null, dadorSub);

        propuesta.setEstado(EstadoPropuesta.ACEPTADA);
        propuestas.findByCargaIdAndEstado(carga.getId(), EstadoPropuesta.PENDIENTE).stream()
                .filter(otra -> !otra.getId().equals(propuestaId))
                .forEach(otra -> otra.setEstado(EstadoPropuesta.RECHAZADA));
        // Carga tiene @Version: si otro pedido la asignó mientras tanto, este UPDATE falla y se responde 409.
        carga.setEstado(EstadoCarga.ASIGNADA);

        // saveAndFlush manda los INSERT y UPDATE ahora, dentro del método: si hay un choque de versión,
        // la excepción sale de acá y no recién en el commit.
        return viajes.saveAndFlush(viaje).getId();
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
