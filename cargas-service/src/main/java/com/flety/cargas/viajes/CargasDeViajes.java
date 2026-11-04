package com.flety.cargas.viajes;

import com.flety.cargas.mercado.Carga;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * El bloqueo de la carga al aceptar una propuesta (flujo C). Como PropuestasDeViajes, es un repositorio de viajes
 * sobre una entidad de mercado, para no tocar el CargaRepository de Heron.
 */
public interface CargasDeViajes extends Repository<Carga, Long> {

    /**
     * SELECT ... FOR UPDATE de la carga: es el primer bloqueo de toda aceptación, así todas las aceptaciones de una
     * misma carga se ordenan acá, de a una. El @Version de Carga sigue haciendo falta para los cambios que no pasan
     * por acá (por ejemplo, que el dador cancele la carga mientras tanto, FLY-20).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select c from Carga c where c.id = :id")
    Optional<Carga> bloquear(Long id);
}
