package com.flety.cargas.transporte;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Arma el TransporteClient real. Parte del RestClient.Builder que autoconfigura Spring Boot, así cualquier
 * RestClientCustomizer del equipo (por ejemplo el interceptor que reenvía el token, FLY-35) se aplica solo.
 * Los tests que no quieren HTTP siguen usando TransporteClientFake.
 */
@Configuration
@EnableConfigurationProperties(TransporteProperties.class)
public class TransporteClientConfig {

    @Bean
    public TransporteClient transporteClient(RestClient.Builder builder, TransporteProperties propiedades) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(propiedades.timeoutConexion());
        fabrica.setReadTimeout(propiedades.timeoutLectura());
        RestClient restClient = builder
                .baseUrl(propiedades.url())
                .requestFactory(fabrica)
                .build();
        return new TransporteClientHttp(restClient);
    }
}
