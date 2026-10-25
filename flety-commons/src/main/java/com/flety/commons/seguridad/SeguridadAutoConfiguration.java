package com.flety.commons.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Configuración base del Resource Server: cada servicio valida el JWT de Keycloak por su cuenta, aunque el Gateway
 * ya lo haya validado (defensa en profundidad). La clave pública la toma de
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri, que cada servicio recibe por variable de entorno.
 * <p>
 * Corre antes que la autoconfiguración de seguridad de Spring Boot para que la de Boot, al ver nuestra
 * SecurityFilterChain, no arme la suya. Si un servicio define su propia SecurityFilterChain, esta se hace a un lado.
 */
@AutoConfiguration(before = {ServletWebSecurityAutoConfiguration.class,
        OAuth2ResourceServerWebSecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SeguridadProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadAutoConfiguration {

    /** Lo que se abre sin token: el health para Docker y el Swagger, que el Gateway expone solo en dev. */
    static final String[] RUTAS_PUBLICAS = {"/actuator/health", "/v3/api-docs/**", "/swagger-ui/**",
            "/swagger-ui.html", "/error"};

    @Bean
    @ConditionalOnMissingBean
    public UsuarioActual usuarioActual() {
        return new UsuarioActual();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErroresDeSeguridad erroresDeSeguridad(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new ErroresDeSeguridad(resolver);
    }

    /** Arma la Authentication a partir del JWT: el nombre es el sub y las autoridades salen de realm_access.roles. */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RolesKeycloakConverter());
        return converter;
    }

    /**
     * Seguridad prendida (lo normal): todo pide un JWT válido salvo las rutas públicas.
     * Sin sesión ni CSRF: cada pedido trae su token, no hay cookie que un sitio ajeno pueda aprovechar.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix = "flety.seguridad", name = "habilitada", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain cadenaConToken(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                              ErroresDeSeguridad errores) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }

    /** Seguridad apagada (flety.seguridad.habilitada=false, solo en el perfil local): no se pide token. */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnProperty(prefix = "flety.seguridad", name = "habilitada", havingValue = "false")
    public SecurityFilterChain cadenaSinToken(HttpSecurity http, SeguridadProperties propiedades,
                                              ErroresDeSeguridad errores) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll())
                .addFilterBefore(new UsuarioLocalFilter(propiedades.usuarioLocal()), AnonymousAuthenticationFilter.class)
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }
}
