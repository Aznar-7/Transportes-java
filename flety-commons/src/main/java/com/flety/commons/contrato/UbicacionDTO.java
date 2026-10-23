package com.flety.commons.contrato;

/**
 * Una localidad con sus coordenadas, como viaja en el contrato interno.
 */
public record UbicacionDTO(String provincia, String localidad, double lat, double lng) {
}
