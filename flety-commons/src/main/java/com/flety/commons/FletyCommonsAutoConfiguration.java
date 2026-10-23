package com.flety.commons;

import com.flety.commons.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguración de la librería: con solo agregar la dependencia, el servicio recibe estos beans sin escanear
 * el paquete com.flety.commons. Spring Boot la encuentra por el archivo
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class FletyCommonsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
