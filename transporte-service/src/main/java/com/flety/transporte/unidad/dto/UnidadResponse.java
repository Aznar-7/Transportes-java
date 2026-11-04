package com.flety.transporte.unidad.dto;

import com.flety.commons.contrato.TipoCarroceria;
import com.flety.transporte.unidad.Unidad;

import java.math.BigDecimal;

public record UnidadResponse(Long id, String dominio, TipoCarroceria tipoCarroceria,
                             BigDecimal capacidadToneladas, BigDecimal capacidadM3) {

    public static UnidadResponse desde(Unidad u) {
        return new UnidadResponse(u.getId(), u.getDominio(), u.getTipoCarroceria(),
                u.getCapacidadToneladas(), u.getCapacidadM3());
    }
}
