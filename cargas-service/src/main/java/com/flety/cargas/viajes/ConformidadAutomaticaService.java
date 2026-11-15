package com.flety.cargas.viajes;

import com.flety.commons.excepciones.ConflictoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * La conformidad automática a las 48 h (FLY-30): la regla que corre el Reloj de cargas. Recibe la hora, así el test
 * la prueba con viajes de ventana vencida sin esperar a que el Reloj dispare.
 * <p>
 * Usa exactamente la misma transición que el dador (viaje.conformar), solo que como SISTEMA: no hay un segundo camino
 * a CONFORMADO que se pueda desfasar del primero.
 */
@Service
public class ConformidadAutomaticaService {

    private static final Logger log = LoggerFactory.getLogger(ConformidadAutomaticaService.class);

    private final ViajeRepository viajes;
    private final TransactionTemplate tx;

    public ConformidadAutomaticaService(ViajeRepository viajes, TransactionTemplate tx) {
        this.viajes = viajes;
        this.tx = tx;
    }

    /**
     * Conforma como SISTEMA cada viaje ENTREGADO con la ventana vencida.
     * <p>
     * Cada viaje va en su propia transacción: si uno falla, los demás igual se conforman, y nunca hay una transacción
     * larga con todos los viajes bloqueados. Un viaje puede fallar porque el dador lo conformó o disputó en el mismo
     * momento: @Version lo detecta (OptimisticLockingFailure) o el estado ya no es ENTREGADO (409). En los dos casos
     * se saltea, porque ya no le toca al sistema, y se vuelve a mirar en la próxima vuelta del Reloj.
     *
     * @return cuántos viajes quedaron conformados en esta vuelta
     */
    public int conformarVencidas(Instant ahora) {
        List<Long> ids = viajes.idsConVentanaVencida(ahora);
        int conformados = 0;
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(estado -> {
                    Viaje viaje = viajes.findById(id).orElseThrow();
                    viaje.conformar(ConformadoPor.SISTEMA, EventoViaje.ACTOR_SISTEMA);
                    viajes.flush();
                });
                conformados++;
            } catch (OptimisticLockingFailureException | ConflictoException | TransicionNoPermitidaException ex) {
                log.info("Viaje {}: no se conforma automáticamente ({})", id, ex.getMessage());
            }
        }
        return conformados;
    }
}
