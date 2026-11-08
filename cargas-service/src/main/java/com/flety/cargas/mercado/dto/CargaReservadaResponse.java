package com.flety.cargas.mercado.dto;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import com.flety.cargas.mercado.Ubicacion;
import com.flety.commons.contrato.TipoCarroceria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * La carga como la ve un transportista (RN-13): sin dirección exacta ni datos del dador.
 * Tampoco lleva lat y lng: el dador puede haber mandado las coordenadas exactas, y mostrarlas sería mostrar la dirección.
 * Con el viaje CONFIRMADO, los contactos y la dirección se ven en GET /api/viajes/{id}.
 */
public record CargaReservadaResponse(
        Long id,
        LugarResponse origen,
        LugarResponse destino,
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
        EstadoCarga estado
) implements CargaVista {

    public record LugarResponse(String provincia, String localidad) {

        static LugarResponse desde(Ubicacion u) {
            return new LugarResponse(u.getProvincia(), u.getLocalidad());
        }
    }

    public static CargaReservadaResponse desde(Carga c) {
        return new CargaReservadaResponse(c.getId(), LugarResponse.desde(c.getOrigen()), LugarResponse.desde(c.getDestino()),
                c.getCargaDesde(), c.getCargaHasta(), c.getDescargaDesde(), c.getDescargaHasta(), c.getRubro(),
                c.getPesoToneladas(), c.getVolumenM3(), c.getCarroceriasAceptadas(), c.getPrecioOfrecido(),
                c.getValorDeclarado(), c.getEstado());
    }
}
