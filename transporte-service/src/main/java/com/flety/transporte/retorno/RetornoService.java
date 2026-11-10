package com.flety.transporte.retorno;

import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeoUtils;
import com.flety.commons.geo.GeorefClient;
import com.flety.commons.geo.RoutingClient;
import com.flety.commons.geo.Ruta;
import com.flety.commons.seguridad.UsuarioActual;
import com.flety.transporte.retorno.dto.LugarRequest;
import com.flety.transporte.retorno.dto.PublicarRetornoRequest;
import com.flety.transporte.retorno.dto.RetornoResponse;
import com.flety.transporte.unidad.Unidad;
import com.flety.transporte.unidad.UnidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Los clientes de Georef y de rutas se piden con ObjectProvider: si el servicio todavía no tiene un bean
 * (los reales son FLY-36 y FLY-37), se comporta igual que cuando la API está caída.
 */
@Service
public class RetornoService {

    private static final Logger log = LoggerFactory.getLogger(RetornoService.class);

    private final RetornoRepository repository;
    private final UnidadService unidades;
    private final UsuarioActual usuarioActual;
    private final ObjectProvider<GeorefClient> georef;
    private final ObjectProvider<RoutingClient> routing;

    public RetornoService(RetornoRepository repository, UnidadService unidades, UsuarioActual usuarioActual,
                          ObjectProvider<GeorefClient> georef, ObjectProvider<RoutingClient> routing) {
        this.repository = repository;
        this.unidades = unidades;
        this.usuarioActual = usuarioActual;
        this.georef = georef;
        this.routing = routing;
    }

    @Transactional
    public RetornoResponse publicar(PublicarRetornoRequest pedido) {
        Unidad unidad = unidades.propia(pedido.unidadId());
        Ubicacion origen = ubicar(pedido.origen());
        Ubicacion destino = ubicar(pedido.destino());

        List<Coordenada> extremos = List.of(origen.coordenada(), destino.coordenada());
        Ruta ruta = calcularRuta(extremos);
        boolean aproximada = ruta == null;
        if (aproximada) {
            // Sin la API de rutas: línea recta origen–destino. La duración no se puede saber, queda en 0.
            ruta = new Ruta(GeoUtils.distanciaEnKm(extremos), 0.0, extremos);
        }

        Retorno retorno = new Retorno(unidad, origen, destino, pedido.salidaDesde(), pedido.salidaHasta(),
                pedido.desvioMaximoKmOPorDefecto(), ruta, aproximada);
        return RetornoResponse.desde(repository.save(retorno));
    }

    /** Los retornos del usuario actual, por fecha de salida. Con estado null trae todos. */
    @Transactional(readOnly = true)
    public List<RetornoResponse> listarPropios(EstadoRetorno estado) {
        String sub = usuarioActual.id();
        List<Retorno> retornos = estado == null
                ? repository.findByUnidadTransportistaOwnerSubOrderBySalidaDesde(sub)
                : repository.findByUnidadTransportistaOwnerSubAndEstadoOrderBySalidaDesde(sub, estado);
        return retornos.stream().map(RetornoResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public RetornoResponse ver(Long id) {
        return RetornoResponse.desde(propio(id));
    }

    @Transactional
    public void cancelar(Long id) {
        propio(id).cancelar();
    }

    /** Para cargas-service (flujo A): no verifica dueño, devuelve el transportistaSub y lo controla cargas. */
    @Transactional(readOnly = true)
    public RetornoInternoDTO interno(Long id) {
        Retorno r = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Retorno", id));
        Unidad unidad = r.getUnidad();
        return new RetornoInternoDTO(r.getId(), unidad.getTransportista().getId(), unidad.getId(), r.getEstado().name(),
                r.getOrigen().comoDTO(), r.getDestino().comoDTO(), r.getSalidaDesde(), r.getSalidaHasta(),
                r.getDesvioMaximoKm(), r.getRutaDistanciaKm(), r.getRutaDuracionMin(), r.getRutaPuntos(),
                r.isRutaAproximada(), unidad.getTipoCarroceria(), unidad.getCapacidadToneladas(),
                unidad.getCapacidadM3(), unidad.getTransportista().getOwnerSub());
    }

    /** El retorno si es del usuario actual (RN-T1): 404 si no existe, 403 si es de otro transportista. */
    private Retorno propio(Long id) {
        Retorno retorno = repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Retorno", id));
        if (!retorno.getUnidad().getTransportista().getOwnerSub().equals(usuarioActual.id())) {
            throw new AccesoDenegadoException("El retorno no es tuyo");
        }
        return retorno;
    }

    /** Si el cliente mandó coordenadas se usan; si no, Georef. Nunca se inventa un valor (503 si no se puede). */
    private Ubicacion ubicar(LugarRequest lugar) {
        if (lugar.tieneCoordenadas()) {
            return new Ubicacion(lugar.provincia(), lugar.localidad(), new Coordenada(lugar.lat(), lugar.lng()));
        }
        String noUbicada = "No pudimos ubicar la localidad " + lugar.localidad() + ", " + lugar.provincia()
                + ": enviá las coordenadas";
        GeorefClient cliente = georef.getIfAvailable();
        if (cliente == null) {
            throw new ServicioExternoException(noUbicada);
        }
        try {
            return new Ubicacion(lugar.provincia(), lugar.localidad(), cliente.ubicar(lugar.provincia(), lugar.localidad()));
        } catch (ServicioExternoException ex) {
            throw new ServicioExternoException(noUbicada, ex);
        }
    }

    /** null si no hay cliente de rutas o si falló: el que llama arma la ruta aproximada. */
    private Ruta calcularRuta(List<Coordenada> extremos) {
        RoutingClient cliente = routing.getIfAvailable();
        if (cliente == null) {
            log.warn("Sin cliente de rutas configurado: el retorno queda con ruta aproximada");
            return null;
        }
        try {
            return cliente.calcularRuta(extremos);
        } catch (ServicioExternoException ex) {
            log.warn("La API de rutas falló, el retorno queda con ruta aproximada: {}", ex.getMessage());
            return null;
        }
    }
}
