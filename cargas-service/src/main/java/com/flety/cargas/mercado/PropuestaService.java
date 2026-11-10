package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.PropuestaResponse;
import com.flety.cargas.mercado.dto.ProponerRequest;
import com.flety.cargas.transporte.TransporteClient;
import com.flety.cargas.viajes.ViajesActivosPorUnidad;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PropuestaService {

    /** RN-16: una propuesta espera como mucho un día, o menos si antes cierra la ventana de carga. */
    private static final Duration VIGENCIA = Duration.ofHours(24);
    private static final String RETORNO_PUBLICADO = "PUBLICADO";

    private final CargaRepository cargas;
    private final AltaDePropuesta alta;
    private final TransporteClient transporte;
    private final ViajesActivosPorUnidad viajesActivos;
    private final PropuestaRepository propuestas;
    private final UsuarioActual usuarioActual;

    public PropuestaService(CargaRepository cargas, AltaDePropuesta alta, TransporteClient transporte,
                            ViajesActivosPorUnidad viajesActivos, PropuestaRepository propuestas,
                            UsuarioActual usuarioActual) {
        this.viajesActivos = viajesActivos;
        this.propuestas = propuestas;
        this.cargas = cargas;
        this.alta = alta;
        this.transporte = transporte;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Flujo B. Las reglas van en orden: primero lo que se responde sin llamar a nadie, después quién es el dueño
     * (de nada sirve decirle a alguien por qué la unidad de otro no está habilitada) y al final las reglas de negocio.
     * Sin @Transactional a propósito: las llamadas HTTP van afuera. Si transporte no responde, 503 y no se crea nada
     * (falla cerrada): proponer sin saber si la unidad está habilitada rompería RN-04.
     */
    public PropuestaResponse proponer(Long cargaId, ProponerRequest pedido) {
        String transportistaSub = usuarioActual.id();
        Carga carga = cargas.findById(cargaId).orElseThrow(() -> new RecursoNoEncontradoException("Carga", cargaId));
        alta.validarQueAdmitePropuesta(carga, transportistaSub);

        RetornoInternoDTO retorno = transporte.obtenerRetorno(pedido.retornoId());
        // Los documentos de la unidad tienen que estar vigentes hasta el final de la descarga (RN-04).
        HabilitacionUnidadDTO unidad = transporte.consultarHabilitacion(pedido.unidadId(), carga.getDescargaHasta());

        // RN-T1: los endpoints internos no controlan al dueño, así que se controla acá.
        if (!transportistaSub.equals(unidad.transportistaSub())) {
            throw new AccesoDenegadoException("Esta unidad no es tuya");
        }
        if (!transportistaSub.equals(retorno.transportistaSub())) {
            throw new AccesoDenegadoException("Este retorno no es tuyo");
        }
        if (!RETORNO_PUBLICADO.equals(retorno.estado())) {
            throw new ConflictoException("El retorno ya no está publicado (está " + retorno.estado() + ")");
        }
        if (!pedido.unidadId().equals(retorno.unidadId())) {
            throw new ReglaNegocioException("El retorno es con otra unidad");
        }
        validarHabilitacion(unidad);
        validarCompatibilidad(carga, unidad, retorno);
        // RN-15: viajes conoce los viajes de la unidad; mercado le pregunta por la interfaz y no ve la entidad Viaje.
        // Se vuelve a controlar al aceptar, porque hasta entonces se puede confirmar otro viaje con la misma unidad.
        if (viajesActivos.haySuperposicion(pedido.unidadId(), carga.getCargaDesde(), carga.getDescargaHasta())) {
            throw new ConflictoException("La unidad ya tiene un viaje que se superpone con esta carga (RN-15)");
        }

        return PropuestaResponse.desde(alta.crear(cargaId, nueva(carga, pedido, transportistaSub, unidad)));
    }

    /** Lo que recibió el dador por su carga, de la más vieja a la más nueva. Solo su dueño la ve (RN-T1). */
    @Transactional(readOnly = true)
    public List<PropuestaResponse> listarDeLaCarga(Long cargaId) {
        Carga carga = cargas.findById(cargaId).orElseThrow(() -> new RecursoNoEncontradoException("Carga", cargaId));
        if (!carga.getDadorSub().equals(usuarioActual.id())) {
            throw new AccesoDenegadoException("No sos el dador de esta carga");
        }
        return propuestas.findByCargaIdOrderByCreadaEnAscIdAsc(cargaId).stream().map(PropuestaResponse::desde).toList();
    }

    /** El transportista se baja mientras el dador no decidió. La carga sigue CON_PROPUESTAS: ya recibió al menos una. */
    @Transactional
    public void retirar(Long propuestaId) {
        Propuesta propuesta = bloquear(propuestaId);
        if (!propuesta.getTransportistaSub().equals(usuarioActual.id())) {
            throw new AccesoDenegadoException("Esta propuesta no es tuya");
        }
        validarPendiente(propuesta);
        propuesta.setEstado(EstadoPropuesta.RETIRADA);
    }

    /** RN-03: el dador rechaza y no hay renegociación. La carga sigue abierta para las demás propuestas. */
    @Transactional
    public PropuestaResponse rechazar(Long propuestaId) {
        Propuesta propuesta = bloquear(propuestaId);
        if (!propuesta.getCarga().getDadorSub().equals(usuarioActual.id())) {
            throw new AccesoDenegadoException("No sos el dador de esta carga");
        }
        validarPendiente(propuesta);
        propuesta.setEstado(EstadoPropuesta.RECHAZADA);
        return PropuestaResponse.desde(propuesta);
    }

    private Propuesta bloquear(Long propuestaId) {
        return propuestas.bloquearParaCambiarEstado(propuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Propuesta", propuestaId));
    }

    private static void validarPendiente(Propuesta propuesta) {
        if (propuesta.getEstado() != EstadoPropuesta.PENDIENTE) {
            throw new ConflictoException("La propuesta ya no está pendiente (está " + propuesta.getEstado() + ")");
        }
    }

    /** RN-04. Los motivos los da transporte-service, que es quien sabe qué le falta a la unidad. */
    private static void validarHabilitacion(HabilitacionUnidadDTO unidad) {
        if (!unidad.habilitada()) {
            String motivos = unidad.motivos().isEmpty() ? "" : ": " + String.join(", ", unidad.motivos());
            throw new ReglaNegocioException("La unidad no está habilitada" + motivos);
        }
    }

    /** RN-01: lo mismo que filtra la búsqueda, porque nada obliga a proponer solo sobre lo que apareció en ella. */
    private static void validarCompatibilidad(Carga carga, HabilitacionUnidadDTO unidad, RetornoInternoDTO retorno) {
        if (!carga.getCarroceriasAceptadas().contains(unidad.tipoCarroceria())) {
            throw new ReglaNegocioException("La carga no acepta la carrocería " + unidad.tipoCarroceria());
        }
        if (carga.getPesoToneladas().compareTo(unidad.capacidadToneladas()) > 0) {
            throw new ReglaNegocioException("La carga pesa más de lo que soporta la unidad");
        }
        if (carga.getVolumenM3().compareTo(unidad.capacidadM3()) > 0) {
            throw new ReglaNegocioException("La carga ocupa más de lo que entra en la unidad");
        }
        if (carga.getCargaDesde().isAfter(retorno.salidaHasta()) || carga.getCargaHasta().isBefore(retorno.salidaDesde())) {
            throw new ReglaNegocioException("La ventana de carga no coincide con la salida del retorno");
        }
    }

    private static Propuesta nueva(Carga carga, ProponerRequest pedido, String transportistaSub,
                                   HabilitacionUnidadDTO unidad) {
        Propuesta propuesta = new Propuesta();
        propuesta.setTransportistaSub(transportistaSub);
        propuesta.setTransportistaId(unidad.transportistaId());
        propuesta.setUnidadId(pedido.unidadId());
        propuesta.setRetornoId(pedido.retornoId());
        propuesta.setTipo(pedido.tipo());
        propuesta.setPrecio(precio(carga, pedido));
        propuesta.setMensaje(pedido.mensaje());
        propuesta.setRequiereAceptacionRiesgo(requiereAceptarRiesgo(carga, unidad));
        Instant enUnDia = Instant.now().plus(VIGENCIA);
        propuesta.setVencimiento(enUnDia.isBefore(carga.getCargaHasta()) ? enUnDia : carga.getCargaHasta());
        return propuesta;
    }

    /** RN-06: no frena la propuesta, avisa. Decide el dador al aceptar. Sin póliza no hay nada que cubra la carga. */
    private static boolean requiereAceptarRiesgo(Carga carga, HabilitacionUnidadDTO unidad) {
        return unidad.sumaAseguradaCarga() == null
                || carga.getValorDeclarado().compareTo(unidad.sumaAseguradaCarga()) > 0;
    }

    /** RN-03: el precio lo fija el dador. Una contraoferta igual a su precio no es una contraoferta. */
    private static BigDecimal precio(Carga carga, ProponerRequest pedido) {
        if (pedido.tipo() == TipoPropuesta.ACEPTA_PRECIO) {
            return carga.getPrecioOfrecido();
        }
        if (pedido.precio().compareTo(carga.getPrecioOfrecido()) == 0) {
            throw new ReglaNegocioException("La contraoferta es igual al precio del dador: usá ACEPTA_PRECIO");
        }
        return pedido.precio();
    }
}
