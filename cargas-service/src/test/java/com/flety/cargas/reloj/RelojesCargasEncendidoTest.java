package com.flety.cargas.reloj;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Con el reloj prendido, como en Docker. Si la duración estuviera mal escrita, Spring fallaría al arrancar el
 * contexto. La primera vuelta espera un minuto (conformidad-inicio), así que acá no dispara sola: se la llama a mano.
 */
@SpringBootTest(properties = "flety.relojes.habilitados=true")
@ActiveProfiles("test")
class RelojesCargasEncendidoTest {

    @Autowired
    RelojesCargas relojes;

    @Test
    void lasTareasArrancanYCorrenSinErrores() {
        assertThatCode(() -> {
            relojes.conformarVencidas();
            relojes.vencerPropuestas();
        }).doesNotThrowAnyException();
    }
}
