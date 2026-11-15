package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.Ubicacion;
import com.flety.cargas.viajes.dto.DisputaDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO.CancelacionDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO.ContactoDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO.ContactosDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO.EventoViajeDTO;
import com.flety.cargas.viajes.dto.ViajeDetalleDTO.LugarDTO;
import com.flety.cargas.viajes.dto.ViajeResumenDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consultas de viajes para las partes. El rol (DADOR o TRANSPORTISTA) ya lo controló el Gateway y el
 * controller; acá se controla lo que ellos no pueden saber: si quien pregunta es parte de <b>este</b> viaje (RN-T1).
 * <p>
 * readOnly: no hay cambios que guardar, y la transacción abierta permite leer la carga y los eventos
 * (que son LAZY) mientras se arma el DTO. Afuera del service nunca sale una entidad.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaViajesService {

    private final ViajeRepository viajes;
    private final DisputaRepository disputas;
    private final UsuarioActual usuarioActual;

    public ConsultaViajesService(ViajeRepository viajes, DisputaRepository disputas, UsuarioActual usuarioActual) {
        this.viajes = viajes;
        this.disputas = disputas;
        this.usuarioActual = usuarioActual;
    }

    /** Solo los viajes donde el usuario es una de las partes; un tercero recibe una lista vacía. */
    public List<ViajeResumenDTO> misViajes() {
        String sub = usuarioActual.id();
        return viajes.findByDadorSubOrTransportistaSubOrderByCreadoEnDesc(sub, sub).stream()
                .map(viaje -> resumen(viaje, sub))
                .toList();
    }

    /**
     * 404 si no existe y 403 si existe pero quien consulta no es parte. Se distinguen a propósito: el id
     * de un viaje no es un secreto, y un 403 claro ayuda más que un 404 al probar con otro usuario.
     */
    public ViajeDetalleDTO detalle(Long id) {
        Viaje viaje = viajes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Viaje", id));
        String sub = usuarioActual.id();
        if (!viaje.esParte(sub)) {
            // El mensaje lo ve el cliente: dice el motivo sin revelar quiénes son las partes.
            throw new AccesoDenegadoException("No sos parte de este viaje");
        }
        return detalle(viaje, sub);
    }

    private ViajeResumenDTO resumen(Viaje viaje, String sub) {
        Carga carga = viaje.getCarga();
        return new ViajeResumenDTO(viaje.getId(), carga.getId(), viaje.getEstado(), rolDe(viaje, sub),
                carga.getOrigen().getLocalidad(), carga.getDestino().getLocalidad(),
                carga.getCargaDesde(), carga.getDescargaHasta(), viaje.getPrecioAcordado());
    }

    private ViajeDetalleDTO detalle(Viaje viaje, String sub) {
        Carga carga = viaje.getCarga();
        // RN-13: contactos y dirección exacta solo mientras el acuerdo sigue en pie.
        boolean vigente = viaje.getEstado() != TipoEstadoViaje.CANCELADO;
        return new ViajeDetalleDTO(
                viaje.getId(), carga.getId(), viaje.getPropuesta().getId(), viaje.getEstado(), rolDe(viaje, sub),
                lugar(carga.getOrigen(), vigente), lugar(carga.getDestino(), vigente),
                carga.getCargaDesde(), carga.getCargaHasta(), carga.getDescargaDesde(), carga.getDescargaHasta(),
                viaje.getPrecioAcordado(),
                vigente ? contactos(viaje) : null,
                viaje.getEntregadoEn(), viaje.getVentanaConformidadHasta(), viaje.getConformadoPor(),
                cancelacion(viaje),
                // La disputa (FLY-31) la ven las dos partes: el dador que la abrió y el transportista reclamado.
                disputas.findFirstByViajeIdOrderByAbiertaEnDesc(viaje.getId()).map(DisputaDTO::desde).orElse(null),
                viaje.getEventos().stream().map(ConsultaViajesService::evento).toList());
    }

    private static String rolDe(Viaje viaje, String sub) {
        return viaje.getDadorSub().equals(sub) ? "DADOR" : "TRANSPORTISTA";
    }

    private static LugarDTO lugar(Ubicacion ubicacion, boolean conDireccion) {
        return new LugarDTO(ubicacion.getProvincia(), ubicacion.getLocalidad(),
                conDireccion ? ubicacion.getDireccion() : null);
    }

    private static ContactosDTO contactos(Viaje viaje) {
        return new ContactosDTO(contacto(viaje.getContactoDador()), contacto(viaje.getContactoTransportista()),
                viaje.getUnidadDominio());
    }

    /** JPA deja en null un @Embedded con todas sus columnas vacías (por ejemplo, el dador sin datos cargados). */
    private static ContactoDTO contacto(Contacto contacto) {
        return contacto == null ? null : new ContactoDTO(contacto.getNombre(), contacto.getTelefono());
    }

    private static CancelacionDTO cancelacion(Viaje viaje) {
        return viaje.getCanceladoEn() == null ? null : new CancelacionDTO(viaje.getCanceladoPor(),
                viaje.getCanceladoEn(), viaje.getMotivoCancelacion(), Boolean.TRUE.equals(viaje.getConPenalidad()));
    }

    private static EventoViajeDTO evento(EventoViaje evento) {
        return new EventoViajeDTO(evento.getEstadoAnterior(), evento.getEstadoNuevo(), evento.getActorSub(),
                evento.getOcurridoEn(), evento.getDetalle());
    }
}
