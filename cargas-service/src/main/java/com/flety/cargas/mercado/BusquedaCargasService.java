package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaDePasoResponse;
import com.flety.cargas.transporte.TransporteClient;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.contrato.UbicacionDTO;
import com.flety.commons.excepciones.AccesoDenegadoException;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.geo.CajaGeografica;
import com.flety.commons.geo.CalculadorDesvio;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeoUtils;
import com.flety.commons.geo.ResultadoDesvio;
import com.flety.commons.seguridad.UsuarioActual;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Flujo A: las cargas que le quedan de paso a un retorno (RN-01 y RN-02). Va de lo barato a lo caro: primero lo que
 * resuelve la base, después una cuenta en línea recta y recién al final la API de rutas, solo para las mejores N.
 * Sin @Transactional a propósito: hace llamadas HTTP y no conviene tener una conexión a la base tomada mientras tanto.
 */
@Service
public class BusquedaCargasService {

    /** Las únicas que todavía pueden recibir propuestas. */
    private static final Set<EstadoCarga> ABIERTAS = EnumSet.of(EstadoCarga.PUBLICADA, EstadoCarga.CON_PROPUESTAS);
    private static final String RETORNO_PUBLICADO = "PUBLICADO";
    private static final double FACTOR_CORREDOR_APROXIMADO = 2;

    /**
     * RN-02: menos desvío primero y, si empatan, la que mejor paga por km. Sin ranking opaco: todo lo que decide el
     * orden está en la respuesta. El id solo desempata para que la misma búsqueda salga siempre igual.
     */
    private static final Comparator<CargaDePasoResponse> ORDEN_RN_02 = Comparator
            .comparingDouble(CargaDePasoResponse::desvioKm)
            .thenComparing(CargaDePasoResponse::precioPorKm, Comparator.reverseOrder())
            .thenComparing(resultado -> resultado.carga().id());

    private final CargaRepository cargas;
    private final TransporteClient transporte;
    private final CalculadorDesvio calculadorDesvio;
    private final BusquedaProperties propiedades;
    private final UsuarioActual usuarioActual;

    public BusquedaCargasService(CargaRepository cargas, TransporteClient transporte, CalculadorDesvio calculadorDesvio,
                                 BusquedaProperties propiedades, UsuarioActual usuarioActual) {
        this.usuarioActual = usuarioActual;
        this.cargas = cargas;
        this.transporte = transporte;
        this.calculadorDesvio = calculadorDesvio;
        this.propiedades = propiedades;
    }

    public List<CargaDePasoResponse> buscar(Long retornoId) {
        RetornoInternoDTO retorno = transporte.obtenerRetorno(retornoId);
        // RN-T1: el endpoint interno no controla al dueño, así que se controla acá.
        if (!usuarioActual.id().equals(retorno.transportistaSub())) {
            throw new AccesoDenegadoException("Este retorno no es tuyo");
        }
        // El contrato interno devuelve el retorno en cualquier estado y deja que decida quien lo pide.
        if (!RETORNO_PUBLICADO.equals(retorno.estado())) {
            throw new ConflictoException("El retorno ya no está publicado (está " + retorno.estado() + ")");
        }
        CajaGeografica caja = cajaAlrededorDeLaRuta(retorno);
        Coordenada origen = punto(retorno.origen());
        Coordenada destino = punto(retorno.destino());
        return cargas.buscarCandidatas(ABIERTAS, retorno.salidaDesde(), retorno.salidaHasta(), retorno.tipoCarroceria(),
                        retorno.capacidadToneladas(), retorno.capacidadM3(),
                        caja.latMin(), caja.latMax(), caja.lonMin(), caja.lonMax()).stream()
                .sorted(Comparator.comparingDouble(carga -> desvioEnLineaRecta(carga, origen, destino)))
                .limit(propiedades.maxDesviosReales())
                .map(carga -> conDesvio(carga, origen, destino))
                // RN-01: el desvío no puede pasar el máximo que aceptó el transportista al publicar el retorno.
                .filter(resultado -> resultado.desvioKm() <= retorno.desvioMaximoKm())
                .sorted(ORDEN_RN_02)
                .toList();
    }

    private CargaDePasoResponse conDesvio(Carga carga, Coordenada origen, Coordenada destino) {
        ResultadoDesvio desvio = calculadorDesvio.calcular(origen, destino, retiro(carga), entrega(carga));
        return CargaDePasoResponse.desde(carga, desvio.desvioKm(), desvio.aproximado());
    }

    /** Una cuenta sin llamar a nadie, solo para elegir a cuáles les vale la pena pedir el desvío real. */
    private static double desvioEnLineaRecta(Carga carga, Coordenada origen, Coordenada destino) {
        return GeoUtils.distanciaEnKm(List.of(origen, retiro(carga), entrega(carga), destino))
                - GeoUtils.distanciaEnKm(origen, destino);
    }

    /**
     * Quien se desvía más lejos que el máximo ya no cumple RN-01, así que alcanza con mirar esa distancia a la ruta.
     * Una ruta aproximada es la recta origen-destino: el camino real puede ir bastante por afuera, por eso el corredor
     * es más ancho. Se suman origen y destino por si la ruta llega sin puntos.
     */
    private static CajaGeografica cajaAlrededorDeLaRuta(RetornoInternoDTO retorno) {
        List<Coordenada> puntos = new ArrayList<>(retorno.rutaPuntos());
        puntos.add(punto(retorno.origen()));
        puntos.add(punto(retorno.destino()));
        double margenKm = retorno.desvioMaximoKm() * (retorno.rutaAproximada() ? FACTOR_CORREDOR_APROXIMADO : 1);
        return GeoUtils.cajaAlrededor(puntos, margenKm);
    }

    private static Coordenada punto(UbicacionDTO ubicacion) {
        return new Coordenada(ubicacion.lat(), ubicacion.lng());
    }

    private static Coordenada retiro(Carga carga) {
        return new Coordenada(carga.getOrigen().getLat(), carga.getOrigen().getLng());
    }

    private static Coordenada entrega(Carga carga) {
        return new Coordenada(carga.getDestino().getLat(), carga.getDestino().getLng());
    }
}
