package com.flety.cargas.mercado;

import com.flety.cargas.mercado.dto.CargaReservadaResponse;
import com.flety.cargas.transporte.TransporteClient;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.ConflictoException;
import org.springframework.stereotype.Service;

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
        return cargas.buscarCandidatas(ABIERTAS, retorno.salidaDesde(), retorno.salidaHasta(), retorno.tipoCarroceria(),
                        retorno.capacidadToneladas(), retorno.capacidadM3()).stream()
                .map(CargaReservadaResponse::desde)
                .toList();
    }
}
