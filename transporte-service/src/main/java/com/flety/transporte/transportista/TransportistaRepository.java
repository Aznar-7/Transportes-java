package com.flety.transporte.transportista;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransportistaRepository extends JpaRepository<Transportista, Long> {

    Optional<Transportista> findByOwnerSub(String ownerSub);

    boolean existsByOwnerSub(String ownerSub);

    boolean existsByCuit(String cuit);

    List<Transportista> findByEstado(EstadoTransportista estado);
}
