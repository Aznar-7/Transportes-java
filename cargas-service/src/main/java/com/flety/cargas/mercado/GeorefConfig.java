package com.flety.cargas.mercado;

import com.flety.commons.fake.GeorefClientFake;
import com.flety.commons.geo.GeorefClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Por ahora Georef es el fake de flety-commons: conoce algunas localidades y las demás responden 503.
 * En el hito M2 este bean se cambia por GeorefClientHttp (FLY-36), sin tocar a quien lo usa.
 */
@Configuration
public class GeorefConfig {

    @Bean
    public GeorefClient georefClient() {
        return new GeorefClientFake();
    }
}
