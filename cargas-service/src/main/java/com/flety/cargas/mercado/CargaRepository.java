package com.flety.cargas.mercado;

import com.flety.commons.contrato.TipoCarroceria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** JpaSpecificationExecutor es para el listado: arma la consulta según qué filtros vinieron. */
public interface CargaRepository extends JpaRepository<Carga, Long>, JpaSpecificationExecutor<Carga> {

    /**
     * El paso barato de la búsqueda (RN-01): lo que se puede decidir en la base, sin llamar a ninguna API.
     * Las ventanas se pisan si cada una empieza antes de que termine la otra.
     * El join fetch trae las carrocerías en la misma consulta, en vez de un SELECT más por cada carga.
     */
    @Query("""
            select distinct c from Carga c left join fetch c.carroceriasAceptadas
            where c.estado in :estados
              and c.cargaDesde <= :salidaHasta and c.cargaHasta >= :salidaDesde
              and :carroceria member of c.carroceriasAceptadas
              and c.pesoToneladas <= :capacidadToneladas and c.volumenM3 <= :capacidadM3
            """)
    List<Carga> buscarCandidatas(Collection<EstadoCarga> estados, Instant salidaDesde, Instant salidaHasta,
                                 TipoCarroceria carroceria, BigDecimal capacidadToneladas, BigDecimal capacidadM3);
}
