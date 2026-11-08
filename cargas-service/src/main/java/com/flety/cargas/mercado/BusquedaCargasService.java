package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaReservadaResponse;
import com.flety.cargas.transporte.TransporteClient;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.geo.CajaGeografica;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Flujo A: las cargas que le quedan de paso a un retorno (RN-01 y RN-02).
 * Sin @Transactional a propósito: hace llamadas HTTP y no conviene tener una conexión a la base tomada mientras tanto.
 */
@Service
public class BusquedaCargasService {

    /** Las únicas que todavía pueden recibir propuestas. */
    private static final Set<EstadoCarga> ABIERTAS = EnumSet.of(EstadoCarga.PUBLICADA, EstadoCarga.CON_PROPUESTAS);
    private static final String RETORNO_PUBLICADO = "PUBLICADO";
    private static final double FACTOR_CORREDOR_APROXIMADO = 2;

    private final CargaRepository cargas;
    private final TransporteClient transporte;

    public BusquedaCargasService(CargaRepository cargas, TransporteClient transporte) {
        this.cargas = cargas;
        this.transporte = transporte;
    }

    public List<CargaReservadaResponse> buscar(Long retornoId) {
        RetornoInternoDTO retorno = transporte.obtenerRetorno(retornoId);
        // El contrato interno devuelve el retorno en cualquier estado y deja que decida quien lo pide.
        if (!RETORNO_PUBLICADO.equals(retorno.estado())) {
            throw new ConflictoException("El retorno ya no está publicado (está " + retorno.estado() + ")");
        }
        CajaGeografica caja = cajaAlrededorDeLaRuta(retorno);
        return cargas.buscarCandidatas(ABIERTAS, retorno.salidaDesde(), retorno.salidaHasta(), retorno.tipoCarroceria(),
                        retorno.capacidadToneladas(), retorno.capacidadM3(),
                        caja.latMin(), caja.latMax(), caja.lonMin(), caja.lonMax()).stream()
                .map(CargaReservadaResponse::desde)
                .toList();
    }

    /**
     * Quien se desvía más lejos que el máximo ya no cumple RN-01, así que alcanza con mirar esa distancia a la ruta.
     * Una ruta aproximada es la recta origen-destino: el camino real puede ir bastante por afuera, por eso el corredor
     * es más ancho. Se suman origen y destino por si la ruta llega sin puntos.
     */
    private static CajaGeografica cajaAlrededorDeLaRuta(RetornoInternoDTO retorno) {
        List<Coordenada> puntos = new ArrayList<>(retorno.rutaPuntos());
        puntos.add(new Coordenada(retorno.origen().lat(), retorno.origen().lng()));
        puntos.add(new Coordenada(retorno.destino().lat(), retorno.destino().lng()));
        double margenKm = retorno.desvioMaximoKm() * (retorno.rutaAproximada() ? FACTOR_CORREDOR_APROXIMADO : 1);
        return GeoUtils.cajaAlrededor(puntos, margenKm);
    }
}
