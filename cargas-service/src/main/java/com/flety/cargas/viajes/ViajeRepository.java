package com.flety.cargas.viajes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    /** Los viajes donde el usuario es dador o transportista, del más nuevo al más viejo. */
    List<Viaje> findByDadorSubOrTransportistaSubOrderByCreadoEnDesc(String dadorSub, String transportistaSub);

    /**
     * RN-15: los viajes de una unidad en esos estados, ya con su ventana (de cargaDesde a descargaHasta).
     * Se arma el record directo en la consulta: no se cargan los viajes enteros ni sus eventos.
     */
    @Query("""
            select new com.flety.cargas.viajes.ViajeActivo(v.id, c.id, c.cargaDesde, c.descargaHasta)
            from Viaje v join v.carga c
            where v.unidadId = :unidadId and v.estado in :estados
            order by c.cargaDesde
            """)
    List<ViajeActivo> buscarVentanas(Long unidadId, Collection<TipoEstadoViaje> estados);

    /**
     * Para el Reloj de conformidad (FLY-30): los viajes ENTREGADOS cuya ventana de 48 h ya venció. Solo los ids: cada
     * viaje se conforma después en su propia transacción, así no se cargan todos juntos ni se los bloquea en bloque.
     * Un viaje EN_DISPUTA no aparece: el dador ya actuó dentro de la ventana.
     */
    @Query("""
            select v.id from Viaje v
            where v.estado = com.flety.cargas.viajes.TipoEstadoViaje.ENTREGADO and v.ventanaConformidadHasta < :ahora
            order by v.ventanaConformidadHasta
            """)
    List<Long> idsConVentanaVencida(Instant ahora);
}
