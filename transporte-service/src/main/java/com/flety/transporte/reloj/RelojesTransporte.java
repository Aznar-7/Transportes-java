package com.flety.transporte.reloj;

import com.flety.transporte.documento.DocumentoService;
import com.flety.transporte.retorno.RetornoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;

/**
 * El "Reloj" del diseño para transporte: vence documentos (RN-05) y retornos (RN-17).
 * Solo dispara: la regla vive en cada service, que recibe la hora y por eso se prueba con una fecha fija.
 * En los tests está apagado (flety.relojes.habilitados=false) para que no toque los datos de otro test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "flety.relojes", name = "habilitados", havingValue = "true", matchIfMissing = true)
public class RelojesTransporte {

    private static final Logger log = LoggerFactory.getLogger(RelojesTransporte.class);

    private final DocumentoService documentos;
    private final RetornoService retornos;
    private final Clock reloj = Clock.systemUTC();

    public RelojesTransporte(DocumentoService documentos, RetornoService retornos) {
        this.documentos = documentos;
        this.retornos = retornos;
    }

    /** Una vez por día, a las 3 de la mañana de Argentina. */
    @Scheduled(cron = "${flety.relojes.documentos:0 0 3 * * *}", zone = "America/Argentina/Cordoba")
    public void vencerDocumentos() {
        int vencidos = documentos.vencerCumplidos(reloj.instant());
        log.info("Reloj de documentos: {} vencidos", vencidos);
    }

    /** Cada 15 minutos: la ventana de salida de un retorno termina a cualquier hora. */
    @Scheduled(fixedDelayString = "${flety.relojes.retornos:PT15M}")
    public void vencerRetornos() {
        int vencidos = retornos.vencerPasados(reloj.instant());
        log.info("Reloj de retornos: {} vencidos", vencidos);
    }
}
