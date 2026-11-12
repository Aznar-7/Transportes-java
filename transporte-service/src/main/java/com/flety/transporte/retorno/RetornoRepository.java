package com.flety.transporte.retorno;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RetornoRepository extends JpaRepository<Retorno, Long> {

    /** Trae los puntos de la ruta en la misma consulta: la respuesta los muestra y, si no, haría un SELECT por retorno. */
    @EntityGraph(attributePaths = "rutaPuntos")
    List<Retorno> findByUnidadTransportistaOwnerSubOrderBySalidaDesde(String ownerSub);

    @EntityGraph(attributePaths = "rutaPuntos")
    List<Retorno> findByUnidadTransportistaOwnerSubAndEstadoOrderBySalidaDesde(String ownerSub, EstadoRetorno estado);

    List<Retorno> findByEstadoAndSalidaHastaBefore(EstadoRetorno estado, Instant ahora);
}
