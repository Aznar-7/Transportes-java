package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Lo que viajes necesita leer de las propuestas para aceptarlas (flujo C).
 * Es un repositorio aparte del PropuestaRepository de mercado: viajes declara solo las consultas que usa,
 * sin tocar el repositorio de Heron. Spring Data permite varios repositorios sobre la misma entidad.
 */
public interface PropuestasDeViajes extends Repository<Propuesta, Long> {

    Optional<Propuesta> findById(Long id);

    /** Las otras propuestas de la carga que siguen esperando: al aceptar una, se rechazan (flujo C). */
    List<Propuesta> findByCargaIdAndEstado(Long cargaId, EstadoPropuesta estado);
}
