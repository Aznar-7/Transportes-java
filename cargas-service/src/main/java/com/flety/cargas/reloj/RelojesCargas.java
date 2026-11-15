package com.flety.cargas.reloj;

import com.flety.cargas.viajes.ConformidadAutomaticaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

/**
 * El "Reloj" del diseño para cargas, igual que RelojesTransporte: conforma los viajes a las 48 h (FLY-30).
 * Solo dispara: la regla vive en ConformidadAutomaticaService, que recibe la hora y por eso se prueba sin esperar.
 * En los tests está apagado (flety.relojes.habilitados=false) para que no toque los datos de otro test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "flety.relojes", name = "habilitados", havingValue = "true", matchIfMissing = true)
public class RelojesCargas {

    private static final Logger log = LoggerFactory.getLogger(RelojesCargas.class);

    private final ConformidadAutomaticaService conformidad;
    private final Clock reloj = Clock.systemUTC();

    public RelojesCargas(ConformidadAutomaticaService conformidad) {
        this.conformidad = conformidad;
    }

    /**
     * Cada 15 minutos: la ventana de 48 h de cada viaje vence a cualquier hora, así que un viaje queda a lo sumo
     * 15 minutos de más en ENTREGADO. La primera vuelta espera un minuto para no competir con el arranque.
     */
    @Scheduled(fixedDelayString = "${flety.relojes.conformidad:PT15M}",
            initialDelayString = "${flety.relojes.conformidad-inicio:PT1M}")
    public void conformarVencidas() {
        int conformados = conformidad.conformarVencidas(reloj.instant());
        log.info("Reloj de conformidad: {} viajes conformados por el sistema", conformados);
    }
}
