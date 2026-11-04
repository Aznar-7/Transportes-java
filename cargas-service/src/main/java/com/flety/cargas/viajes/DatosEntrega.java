package com.flety.cargas.viajes;

/**
 * Lo que el transportista informa al entregar: el remito y quién recibió la carga. Con esto se arma el
 * ComprobanteEntrega, que es la prueba de la entrega si después hay una disputa.
 */
public record DatosEntrega(String numeroRemito, String recibioNombre, String recibioDni) {
}
