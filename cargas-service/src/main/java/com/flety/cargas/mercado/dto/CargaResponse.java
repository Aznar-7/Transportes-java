package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.Ubicacion;
import com.flety.commons.contrato.TipoCarroceria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * La carga completa, solo para su dador: es el único que ve la dirección exacta antes de que haya viaje (RN-13).
 */
public record CargaResponse(
        Long id,
        UbicacionResponse origen,
        UbicacionResponse destino,
        Instant cargaDesde,
        Instant cargaHasta,
        Instant descargaDesde,
        Instant descargaHasta,
        String rubro,
        BigDecimal pesoToneladas,
        BigDecimal volumenM3,
        Set<TipoCarroceria> carroceriasAceptadas,
        BigDecimal precioOfrecido,
        BigDecimal valorDeclarado,
        boolean declaraNoPeligrosa,
        boolean declaraNoSobredimensionada,
        EstadoCarga estado,
        Instant creadaEn
) implements CargaVista {

    public record UbicacionResponse(String provincia, String localidad, double lat, double lng, String direccion) {

        static UbicacionResponse desde(Ubicacion u) {
            return new UbicacionResponse(u.getProvincia(), u.getLocalidad(), u.getLat(), u.getLng(), u.getDireccion());
        }
    }

    public static CargaResponse desde(Carga c) {
        return new CargaResponse(c.getId(), UbicacionResponse.desde(c.getOrigen()), UbicacionResponse.desde(c.getDestino()),
                c.getCargaDesde(), c.getCargaHasta(), c.getDescargaDesde(), c.getDescargaHasta(), c.getRubro(),
                c.getPesoToneladas(), c.getVolumenM3(), c.getCarroceriasAceptadas(), c.getPrecioOfrecido(),
                c.getValorDeclarado(), c.isDeclaraNoPeligrosa(), c.isDeclaraNoSobredimensionada(), c.getEstado(),
                c.getCreadaEn());
    }
}
