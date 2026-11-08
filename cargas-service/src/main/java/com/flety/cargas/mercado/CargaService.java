package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaReservadaResponse;
import com.flety.cargas.mercado.dto.CargaResponse;
import com.flety.cargas.mercado.dto.CargaVista;
import com.flety.cargas.mercado.dto.PublicarCargaRequest;
import com.flety.cargas.mercado.dto.UbicacionRequest;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeorefClient;
import com.flety.commons.seguridad.UsuarioActual;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
public class CargaService {

    private final CargaRepository cargas;
    private final DadorRepository dadores;
    private final GeorefClient georef;
    private final UsuarioActual usuarioActual;

    public CargaService(CargaRepository cargas, DadorRepository dadores, GeorefClient georef,
                        UsuarioActual usuarioActual) {
        this.cargas = cargas;
        this.dadores = dadores;
        this.georef = georef;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Sin @Transactional a propósito: Georef es una llamada HTTP y no conviene tener una conexión a la base tomada
     * mientras se espera a otro servidor. Las reglas van primero para no llamar a Georef si la carga no se puede
     * publicar, y al final save() abre su propia transacción.
     */
    public CargaResponse publicar(PublicarCargaRequest pedido) {
        String dadorSub = usuarioActual.id();
        validarDeclaraciones(dadorSub, pedido);

        Carga carga = new Carga();
        carga.setDadorSub(dadorSub);
        carga.setOrigen(ubicar(pedido.origen()));
        carga.setDestino(ubicar(pedido.destino()));
        carga.setCargaDesde(pedido.cargaDesde());
        carga.setCargaHasta(pedido.cargaHasta());
        carga.setDescargaDesde(pedido.descargaDesde());
        carga.setDescargaHasta(pedido.descargaHasta());
        carga.setRubro(pedido.rubro());
        carga.setPesoToneladas(pedido.pesoToneladas());
        carga.setVolumenM3(pedido.volumenM3());
        carga.setCarroceriasAceptadas(EnumSet.copyOf(pedido.carroceriasAceptadas()));
        carga.setPrecioOfrecido(pedido.precioOfrecido());
        carga.setValorDeclarado(pedido.valorDeclarado());
        carga.setDeclaraNoPeligrosa(true);
        carga.setDeclaraNoSobredimensionada(true);
        return CargaResponse.desde(cargas.save(carga));
    }

    /** Las cargas del dador, de la más nueva a la más vieja. Los filtros son opcionales; el rubro ignora mayúsculas. */
    @Transactional(readOnly = true)
    public List<CargaResponse> listarPropias(EstadoCarga estado, String rubro) {
        String dadorSub = usuarioActual.id();
        Specification<Carga> filtros = (carga, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(carga.get("dadorSub"), dadorSub));
            if (estado != null) {
                condiciones.add(cb.equal(carga.get("estado"), estado));
            }
            if (rubro != null && !rubro.isBlank()) {
                condiciones.add(cb.equal(cb.lower(carga.get("rubro")), rubro.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return cargas.findAll(filtros, Sort.by(Sort.Direction.DESC, "creadaEn", "id")).stream()
                .map(CargaResponse::desde)
                .toList();
    }

    /**
     * RN-13: el dador de la carga la ve completa y el transportista, reservada. Otro dador no tiene nada que hacer
     * con esta carga: 403 (RN-T1). El OPERADOR ni llega acá, lo corta el @PreAuthorize del controller.
     */
    @Transactional(readOnly = true)
    public CargaVista ver(Long id) {
        Carga carga = buscar(id);
        if (carga.getDadorSub().equals(usuarioActual.id())) {
            return CargaResponse.desde(carga);
        }
        if (usuarioActual.tieneRol("TRANSPORTISTA")) {
            return CargaReservadaResponse.desde(carga);
        }
        throw new AccesoDenegadoException("No sos el dador de esta carga");
    }

    private Carga buscar(Long id) {
        return cargas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Carga", id));
    }

    /** RN-07 y RN-08: son 422 porque el pedido está bien formado, pero el negocio no permite publicar esta carga. */
    private void validarDeclaraciones(String dadorSub, PublicarCargaRequest pedido) {
        Dador dador = dadores.findByOwnerSub(dadorSub)
                .orElseThrow(() -> new ReglaNegocioException("Para publicar tenés que registrar tu organización (RN-07)"));
        if (!dador.isDeclaraEmpresa()) {
            throw new ReglaNegocioException("Para publicar tenés que declarar que contratás como empresa (RN-07)");
        }
        if (!Boolean.TRUE.equals(pedido.declaraNoPeligrosa())) {
            throw new ReglaNegocioException("Para publicar tenés que declarar que la carga no es peligrosa (RN-08)");
        }
        if (!Boolean.TRUE.equals(pedido.declaraNoSobredimensionada())) {
            throw new ReglaNegocioException("Para publicar tenés que declarar que la carga no está sobredimensionada (RN-08)");
        }
    }

    /** Si el dador mandó coordenadas se usan tal cual. Si no, las da Georef; si no puede, 503: nunca se inventan. */
    private Ubicacion ubicar(UbicacionRequest pedido) {
        if (pedido.tieneCoordenadas()) {
            return new Ubicacion(pedido.provincia(), pedido.localidad(), pedido.lat(), pedido.lng(), pedido.direccion());
        }
        try {
            Coordenada punto = georef.ubicar(pedido.provincia(), pedido.localidad());
            return new Ubicacion(pedido.provincia(), pedido.localidad(), punto.lat(), punto.lng(), pedido.direccion());
        } catch (ServicioExternoException ex) {
            throw new ServicioExternoException("No pudimos ubicar la localidad " + pedido.localidad() + ", "
                    + pedido.provincia() + "; enviá las coordenadas", ex);
        }
    }
}
