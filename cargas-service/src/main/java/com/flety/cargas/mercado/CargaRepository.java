package com.flety.cargas.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** JpaSpecificationExecutor es para el listado: arma la consulta según qué filtros vinieron. */
public interface CargaRepository extends JpaRepository<Carga, Long>, JpaSpecificationExecutor<Carga> {
}
