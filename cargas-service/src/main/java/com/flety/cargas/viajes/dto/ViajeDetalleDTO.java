package com.flety.cargas.viajes.dto;

import com.flety.cargas.viajes.ConformadoPor;
import com.flety.cargas.viajes.ParteDelViaje;
import com.flety.cargas.viajes.TipoEstadoViaje;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/viajes/{id}: el viaje con sus contactos y su línea de tiempo. Solo lo reciben las dos partes.
 *
 * @param contactos null si el viaje está CANCELADO: ya no hay acuerdo entre las partes (RN-13)
 */
public record ViajeDetalleDTO(
        Long id,
        Long cargaId,
        Long propuestaId,
        TipoEstadoViaje estado,
        String miRol,
        LugarDTO origen,
        LugarDTO destino,
        Instant cargaDesde,
        Instant cargaHasta,
        Instant descargaDesde,
        Instant descargaHasta,
        BigDecimal precioAcordado,
        ContactosDTO contactos,
        Instant entregadoEn,
        Instant ventanaConformidadHasta,
        ConformadoPor conformadoPor,
        CancelacionDTO cancelacion,
        DisputaDTO disputa,
        List<EventoViajeDTO> lineaDeTiempo
) {

    /**
     * Quién canceló, cuándo, por qué y si quedó con penalidad (FLY-28). El campo cancelacion es null mientras el
     * viaje siga en pie.
     *
     * @param conPenalidad se canceló con menos de 24 h para el inicio de la carga
     */
    public record CancelacionDTO(ParteDelViaje canceladoPor, Instant canceladoEn, String motivo,
                                 boolean conPenalidad) {
    }

    /**
     * @param direccion la dirección exacta: con el viaje confirmado las partes la ven (RN-13); null si está cancelado
     */
    public record LugarDTO(String provincia, String localidad, String direccion) {
    }

    public record ContactosDTO(ContactoDTO dador, ContactoDTO transportista, String unidadDominio) {
    }

    public record ContactoDTO(String nombre, String telefono) {
    }

    /** Un cambio de estado: estadoAnterior es null en el primero, cuando el viaje nace CONFIRMADO. */
    public record EventoViajeDTO(TipoEstadoViaje estadoAnterior, TipoEstadoViaje estadoNuevo, String actorSub,
                                 Instant ocurridoEn, String detalle) {
    }
}
