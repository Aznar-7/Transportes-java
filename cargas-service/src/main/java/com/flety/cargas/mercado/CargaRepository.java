package com.flety.cargas.mercado;

import com.flety.commons.contrato.TipoCarroceria;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** JpaSpecificationExecutor es para el listado: arma la consulta según qué filtros vinieron. */
public interface CargaRepository extends JpaRepository<Carga, Long>, JpaSpecificationExecutor<Carga> {

    /**
     * SELECT ... FOR UPDATE para crear una propuesta: dos pedidos iguales se ordenan acá, de a uno, y el segundo ya ve
     * la propuesta del primero. También se ordena con la aceptación, que bloquea esta misma fila primero.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select c from Carga c where c.id = :id")
    Optional<Carga> bloquearParaProponer(Long id);

    /**
     * El paso barato de la búsqueda (RN-01): lo que se puede decidir en la base, sin llamar a ninguna API.
     * Las ventanas se pisan si cada una empieza antes de que termine la otra.
     * El retiro y la entrega tienen que caer dentro de la caja alrededor de la ruta: si uno queda afuera, el desvío
     * ya supera el máximo y no vale la pena gastar una llamada a la API de rutas.
     * El join fetch trae las carrocerías en la misma consulta, en vez de un SELECT más por cada carga.
     */
    @Query("""
            select distinct c from Carga c left join fetch c.carroceriasAceptadas
            where c.estado in :estados
              and c.cargaDesde <= :salidaHasta and c.cargaHasta >= :salidaDesde
              and :carroceria member of c.carroceriasAceptadas
              and c.pesoToneladas <= :capacidadToneladas and c.volumenM3 <= :capacidadM3
              and c.origen.lat between :latMin and :latMax and c.origen.lng between :lonMin and :lonMax
              and c.destino.lat between :latMin and :latMax and c.destino.lng between :lonMin and :lonMax
            """)
    List<Carga> buscarCandidatas(Collection<EstadoCarga> estados, Instant salidaDesde, Instant salidaHasta,
                                 TipoCarroceria carroceria, BigDecimal capacidadToneladas, BigDecimal capacidadM3,
                                 double latMin, double latMax, double lonMin, double lonMax);
}
