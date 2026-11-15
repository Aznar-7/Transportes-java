package com.flety.cargas.reloj;

import com.flety.cargas.mercado.EstadoCarga;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Las reglas de vencimiento del mercado (FLY-32), que corre el Reloj de cargas. Reciben la hora, así los tests
 * las prueban con cualquier momento (dentro de 23 h, de 25 h...) sin esperar ni tocar los datos.
 */
@Service
public class VencimientosService {

    /** RN-16: una propuesta espera como mucho un día. Es la misma vigencia que usa mercado al crearla. */
    static final Duration VIGENCIA_PROPUESTA = Duration.ofHours(24);

    /** Una carga en estos estados todavía no tiene viaje: es la que puede quedarse sin nadie que la lleve. */
    static final Set<EstadoCarga> SIN_VIAJE = EnumSet.of(EstadoCarga.PUBLICADA, EstadoCarga.CON_PROPUESTAS);

    private final PropuestasVencibles propuestas;
    private final CargasVencibles cargas;

    public VencimientosService(PropuestasVencibles propuestas, CargasVencibles cargas) {
        this.propuestas = propuestas;
        this.cargas = cargas;
    }

    /** @return cuántas propuestas PENDIENTES vencieron */
    @Transactional
    public int vencerPropuestas(Instant ahora) {
        return propuestas.vencerPendientes(ahora, ahora.minus(VIGENCIA_PROPUESTA));
    }

    /**
     * RN-17. Corre en el mismo Reloj que las propuestas y después de ellas: así una carga vencida no deja propuestas
     * PENDIENTES colgadas (todas vencen como mucho al cerrar la ventana de la carga).
     *
     * @return cuántas cargas sin viaje quedaron VENCIDAS
     */
    @Transactional
    public int vencerCargas(Instant ahora) {
        return cargas.vencerSinViaje(ahora, SIN_VIAJE);
    }
}
