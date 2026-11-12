package com.flety.commons.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implementación de {@link CalculadorDesvio} que aplica el patrón Strategy con selección
 * dinámica y caída resiliente a línea recta (FLY-43).
 * <p>
 * Intenta calcular el desvío utilizando una estrategia principal (típicamente {@link RutaOpenRouteService}).
 * Si la estrategia principal falla por caída del servicio, error de red o cualquier fallo en el ruteo,
 * conmuta inmediatamente a la estrategia de respaldo ({@link RutaLineaRecta}) sin propagar excepciones,
 * devolviendo el desvío calculado con {@code aproximado = true}.
 * </p>
 */
public class CalculadorDesvioConFallback implements CalculadorDesvio {

    private static final Logger log = LoggerFactory.getLogger(CalculadorDesvioConFallback.class);

    private final CalculadorDesvio estrategiaPrincipal;
    private final CalculadorDesvio estrategiaFallback;

    /**
     * Construye el calculador con estrategias personalizadas.
     *
     * @param estrategiaPrincipal estrategia primaria (habitualmente ruta real).
     * @param estrategiaFallback  estrategia de contingencia ante fallos (habitualmente línea recta).
     */
    public CalculadorDesvioConFallback(CalculadorDesvio estrategiaPrincipal, CalculadorDesvio estrategiaFallback) {
        this.estrategiaPrincipal = Objects.requireNonNull(estrategiaPrincipal, "La estrategia principal no puede ser nula");
        this.estrategiaFallback = Objects.requireNonNull(estrategiaFallback, "La estrategia de fallback no puede ser nula");
    }

    /**
     * Constructor por conveniencia que inicializa {@link RutaOpenRouteService} como estrategia principal
     * y {@link RutaLineaRecta} como estrategia de contingencia.
     *
     * @param routingClient cliente de ruteo para la estrategia principal.
     */
    public CalculadorDesvioConFallback(RoutingClient routingClient) {
        this(new RutaOpenRouteService(routingClient), new RutaLineaRecta());
    }

    @Override
    public ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        validarCoordenadas(origenRetorno, destinoRetorno, retiro, entrega);

        try {
            return estrategiaPrincipal.calcular(origenRetorno, destinoRetorno, retiro, entrega);
        } catch (Exception ex) {
            log.warn("Fallo en la estrategia principal de cálculo de desvío; cayendo a línea recta: {}", ex.getMessage());
            return estrategiaFallback.calcular(origenRetorno, destinoRetorno, retiro, entrega);
        }
    }

    private void validarCoordenadas(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        if (origenRetorno == null || destinoRetorno == null || retiro == null || entrega == null) {
            throw new IllegalArgumentException("Las coordenadas para calcular el desvío no pueden ser nulas");
        }
    }

    public CalculadorDesvio getEstrategiaPrincipal() {
        return estrategiaPrincipal;
    }

    public CalculadorDesvio getEstrategiaFallback() {
        return estrategiaFallback;
    }
}

