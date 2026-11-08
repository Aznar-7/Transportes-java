package com.flety.cargas.mercado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DadorRepository extends JpaRepository<Dador, Long> {

    Optional<Dador> findByOwnerSub(String ownerSub);

    boolean existsByOwnerSub(String ownerSub);

    boolean existsByCuit(String cuit);
}
