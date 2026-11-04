package com.flety.cargas.viajes;

import com.flety.cargas.mercado.EstadoPropuesta;
import com.flety.cargas.mercado.Propuesta;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lo que viajes necesita leer de las propuestas para aceptarlas (flujo C).
 * Es un repositorio aparte del PropuestaRepository de mercado: viajes declara solo las consultas que usa,
 * sin tocar el repositorio de Heron. Spring Data permite varios repositorios sobre la misma entidad.
 */
public interface PropuestasDeViajes extends Repository<Propuesta, Long> {

    Optional<Propuesta> findById(Long id);

    /** Las otras propuestas de la carga que siguen esperando: al aceptar una, se rechazan (flujo C). */
    List<Propuesta> findByCargaIdAndEstado(Long cargaId, EstadoPropuesta estado);

    /**
     * Bloqueo pesimista (SELECT ... FOR UPDATE), el segundo de toda aceptación después del de la carga.
     * Toma de una vez, ordenadas por id, todas las propuestas que la transacción va a tocar o que compiten por la
     * unidad:
     * <ul>
     *     <li>las pendientes de la carga, que se van a aceptar o rechazar;</li>
     *     <li>las abiertas de la unidad, para RN-15.</li>
     * </ul>
     * Por qué las de la unidad: dos dadores pueden aceptar a la vez dos cargas distintas con la misma unidad en
     * ventanas que se pisan. El @Version de Carga no lo frena (son cargas distintas), y bloquear los viajes de la
     * unidad tampoco alcanza: si la unidad todavía no tiene viajes no hay ninguna fila que bloquear y las dos pasan
     * (el problema de las filas "fantasma"). Sus propuestas sí existen: al bloquearlas, la segunda aceptación espera
     * a la primera y recién entonces controla la superposición, ya viendo el viaje nuevo.
     * <p>
     * Por qué todas juntas y por id: si cada transacción bloqueara primero su unidad y después fuera rechazando las
     * demás propuestas de la carga, dos aceptaciones de la misma carga con unidades distintas se esperarían una a
     * la otra (deadlock; lo encontró AceptacionesSimultaneasTest). Tomando todos los bloqueos al principio y siempre
     * en el mismo orden (carga primero, propuestas por id), ninguna transacción espera teniendo algo que la otra
     * necesita. Si en 5 segundos no consigue el bloqueo, falla en vez de dejar el pedido colgado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
            select p from Propuesta p
            where (p.carga.id = :cargaId and p.estado = com.flety.cargas.mercado.EstadoPropuesta.PENDIENTE)
               or (p.unidadId = :unidadId and p.estado in :abiertas)
            order by p.id
            """)
    List<Propuesta> bloquearDeLaCargaYLaUnidad(Long cargaId, Long unidadId, Collection<EstadoPropuesta> abiertas);
}
