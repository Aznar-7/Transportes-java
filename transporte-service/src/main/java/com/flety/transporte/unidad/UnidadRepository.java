package com.flety.transporte.unidad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadRepository extends JpaRepository<Unidad, Long> {

    boolean existsByDominio(String dominio);

    List<Unidad> findByTransportistaOwnerSubOrderByDominio(String ownerSub);
}
