package com.flety.cargas.reloj;

import com.flety.cargas.mercado.Propuesta;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;

/**
 * Lo que el Reloj necesita de las propuestas (FLY-32). Es un repositorio propio sobre la entidad del contrato de
 * modelo (C3), aparte del PropuestaRepository de mercado: el Reloj no toca el código de Heron.
 */
public interface PropuestasVencibles extends Repository<Propuesta, Long> {

    /**
     * RN-16: vence toda propuesta PENDIENTE a las 24 h de creada o cuando cierra la ventana de carga, lo que pase
     * primero. La condición se arma con los datos de la entidad (creadaEn y la ventana de su carga), así no depende de
     * que el campo vencimiento se haya calculado bien al crearla; ese campo también cuenta, por si algún día se acorta.
     * <p>
     * Es un UPDATE directo y no "leer, cambiar, guardar" por dos motivos:
     * <ul>
     *     <li>vence todas en una sola sentencia, sin cargar las entidades;</li>
     *     <li>no pisa a una aceptación en curso: la aceptación bloquea la propuesta (FOR UPDATE), este UPDATE espera, y
     *     cuando entra vuelve a mirar el WHERE con la fila ya ACEPTADA, que deja de ser PENDIENTE y no se toca.</li>
     * </ul>
     *
     * @param haceUnDia ahora menos 24 h: las creadas antes de ese momento ya cumplieron un día
     * @return cuántas propuestas vencieron
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Propuesta p set p.estado = com.flety.cargas.mercado.EstadoPropuesta.VENCIDA
            where p.estado = com.flety.cargas.mercado.EstadoPropuesta.PENDIENTE
              and (p.creadaEn <= :haceUnDia
                   or p.vencimiento <= :ahora
                   or p.carga.id in (select c.id from Carga c where c.cargaHasta <= :ahora))
            """)
    int vencerPendientes(Instant ahora, Instant haceUnDia);
}
