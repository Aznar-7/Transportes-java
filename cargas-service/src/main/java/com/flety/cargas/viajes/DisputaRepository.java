package com.flety.cargas.viajes;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Las disputas de los viajes (FLY-31). Solo guardar y leer: una disputa no se borra, y su resolución la completa la
 * propia entidad (Disputa.resolver). Por eso extiende Repository y no JpaRepository.
 */
public interface DisputaRepository extends Repository<Disputa, Long> {

    Disputa save(Disputa disputa);

    Optional<Disputa> findById(Long id);

    /** La disputa más reciente del viaje, para mostrarla en su detalle (un viaje tiene a lo sumo una). */
    Optional<Disputa> findFirstByViajeIdOrderByAbiertaEnDesc(Long viajeId);
}
