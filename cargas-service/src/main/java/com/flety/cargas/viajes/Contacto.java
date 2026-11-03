package com.flety.cargas.viajes;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Nombre y teléfono de una de las partes, copiados al viaje cuando se confirma.
 * Es una copia a propósito: el transportista vive en otra base (transporte-service) y el viaje tiene que mostrar
 * con quién se acordó, aunque después alguien cambie su teléfono. No es una tabla: va dentro de la tabla viaje.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Contacto {

    @Column(updatable = false)
    private String nombre;

    @Column(updatable = false)
    private String telefono;
}
