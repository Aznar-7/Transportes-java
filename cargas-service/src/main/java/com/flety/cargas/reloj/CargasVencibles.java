package com.flety.cargas.reloj;

import com.flety.cargas.mercado.Carga;
import com.flety.cargas.mercado.EstadoCarga;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;
import java.util.Collection;

/**
 * Lo que el Reloj necesita de las cargas (FLY-32), aparte del CargaRepository de mercado: un repositorio propio sobre
 * la entidad del contrato de modelo (C3), sin tocar el código de Heron.
 */
public interface CargasVencibles extends Repository<Carga, Long> {

    /**
     * RN-17: una carga que sigue abierta (PUBLICADA o CON_PROPUESTAS, o sea, sin viaje) cuando ya cerró su ventana de
     * carga, queda VENCIDA: nadie puede ir a buscarla a tiempo. Una ASIGNADA tiene viaje y no entra.
     * <p>
     * Suma 1 a la versión a mano: un UPDATE de JPQL no pasa por @Version. Así, si un pedido había leído la carga antes
     * (por ejemplo, el dador cancelándola), su UPDATE choca con la versión nueva y recibe 409 en vez de pisar el
     * VENCIDA. Y una aceptación en curso tiene la carga bloqueada (FOR UPDATE, FLY-26): este UPDATE la espera y,
     * cuando entra, ya la ve ASIGNADA y no la toca.
     *
     * @param abiertas los estados que cuentan como "sin viaje"
     * @return cuántas cargas vencieron
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Carga c set c.estado = com.flety.cargas.mercado.EstadoCarga.VENCIDA, c.version = c.version + 1
            where c.estado in :abiertas and c.cargaHasta <= :ahora
            """)
    int vencerSinViaje(Instant ahora, Collection<EstadoCarga> abiertas);
}
