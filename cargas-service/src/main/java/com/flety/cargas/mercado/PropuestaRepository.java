package com.flety.cargas.mercado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropuestaRepository extends JpaRepository<Propuesta, Long> {

    List<Propuesta> findByCargaIdAndEstado(Long cargaId, EstadoPropuesta estado);
}
