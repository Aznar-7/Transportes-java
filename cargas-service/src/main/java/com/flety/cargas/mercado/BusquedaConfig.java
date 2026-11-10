package com.flety.cargas.mercado;

import com.flety.commons.fake.CalculadorDesvioFake;
import com.flety.commons.geo.CalculadorDesvio;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Por ahora el desvío lo da el fake de flety-commons: siempre el mismo valor, sin la API de rutas.
 * En el hito M2 este bean se cambia por las estrategias reales (FLY-43), sin tocar a quien lo usa: es el patrón Strategy.
 */
@Configuration
@EnableConfigurationProperties(BusquedaProperties.class)
public class BusquedaConfig {

    @Bean
    public CalculadorDesvio calculadorDesvio() {
        return new CalculadorDesvioFake();
    }
}
