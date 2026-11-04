package com.flety.transporte.documento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    /** Trae el tipo en la misma consulta: el listado muestra su nombre y, si no, haría un SELECT por documento. */
    @Override
    @EntityGraph(attributePaths = "tipo")
    List<Documento> findAll();

    @EntityGraph(attributePaths = "tipo")
    List<Documento> findByEstado(EstadoDocumento estado);
}
