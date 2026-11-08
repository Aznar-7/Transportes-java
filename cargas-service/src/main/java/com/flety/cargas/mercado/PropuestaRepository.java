package com.flety.cargas.mercado;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface PropuestaRepository extends JpaRepository<Propuesta, Long> {

    List<Propuesta> findByCargaIdAndEstado(Long cargaId, EstadoPropuesta estado);

    List<Propuesta> findByCargaIdOrderByCreadaEnAscIdAsc(Long cargaId);

    /** Cuenta en cualquier estado: una por transportista y por carga, aunque la anterior se haya retirado (RN-03). */
    boolean existsByCargaIdAndTransportistaSub(Long cargaId, String transportistaSub);

    /**
     * SELECT ... FOR UPDATE para retirar o rechazar. Propuesta no tiene @Version: sin el bloqueo, un retiro podría leer
     * PENDIENTE mientras se acepta esa misma propuesta y pisar el ACEPTADA con un RETIRADA, y el viaje quedaría confirmado.
     * La aceptación también bloquea estas filas, así que el segundo espera y ve el estado real.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select p from Propuesta p where p.id = :id")
    Optional<Propuesta> bloquearParaCambiarEstado(Long id);
}
