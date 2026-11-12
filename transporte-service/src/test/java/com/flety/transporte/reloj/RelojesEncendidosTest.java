package com.flety.transporte.reloj;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Con el reloj prendido, como en Docker. Si el cron o la duración estuvieran mal escritos, Spring fallaría
 * al arrancar el contexto. Con la base vacía de H2, correr cada tarea no vence nada y no rompe.
 */
@SpringBootTest(properties = "flety.relojes.habilitados=true")
@ActiveProfiles("test")
class RelojesEncendidosTest {

    @Autowired
    RelojesTransporte relojes;

    @Test
    void lasTareasArrancanYCorrenSinErrores() {
        assertThatCode(() -> {
            relojes.vencerDocumentos();
            relojes.vencerRetornos();
        }).doesNotThrowAnyException();
    }
}
