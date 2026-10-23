package com.flety.commons;

import com.flety.commons.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un servicio que agrega la dependencia recibe el GlobalExceptionHandler sin configurar nada.
 */
class FletyCommonsAutoConfigurationTest {

    private final WebApplicationContextRunner contexto = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FletyCommonsAutoConfiguration.class));

    @Test
    void registraElGlobalExceptionHandler() {
        contexto.run(ctx -> assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void respetaElHandlerPropioDelServicio() {
        contexto.withBean("handlerPropio", GlobalExceptionHandler.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class).hasBean("handlerPropio"));
    }
}
