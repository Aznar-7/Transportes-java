package com.flety.cargas.mercado;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DadorRepository extends JpaRepository<Dador, Long> {

    boolean existsByOwnerSub(String ownerSub);

    boolean existsByCuit(String cuit);
}
