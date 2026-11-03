package com.flety.cargas.viajes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    /** Los viajes donde el usuario es dador o transportista, del más nuevo al más viejo. */
    List<Viaje> findByDadorSubOrTransportistaSubOrderByCreadoEnDesc(String dadorSub, String transportistaSub);
}
