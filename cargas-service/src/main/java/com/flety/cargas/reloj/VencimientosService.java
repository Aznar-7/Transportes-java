package com.flety.cargas.reloj;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Las reglas de vencimiento del mercado (FLY-32), que corre el Reloj de cargas. Reciben la hora, así los tests
 * las prueban con cualquier momento (dentro de 23 h, de 25 h...) sin esperar ni tocar los datos.
 */
@Service
public class VencimientosService {

    /** RN-16: una propuesta espera como mucho un día. Es la misma vigencia que usa mercado al crearla. */
    static final Duration VIGENCIA_PROPUESTA = Duration.ofHours(24);

    private final PropuestasVencibles propuestas;

    public VencimientosService(PropuestasVencibles propuestas) {
        this.propuestas = propuestas;
    }

    /** @return cuántas propuestas PENDIENTES vencieron */
    @Transactional
    public int vencerPropuestas(Instant ahora) {
        return propuestas.vencerPendientes(ahora, ahora.minus(VIGENCIA_PROPUESTA));
    }
}
