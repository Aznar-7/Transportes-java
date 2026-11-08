package com.flety.cargas.mercado;

import com.flety.commons.geo.CalculadorDesvio;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.ResultadoDesvio;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;

/**
 * Reemplaza el CalculadorDesvioFake de commons, que da siempre el mismo valor: acá cada carga tiene su desvío según
 * dónde se retira, y se cuentan las llamadas para ver cuántas veces se habría usado la API de rutas.
 * Se suma con @Import(DesvioDePrueba.class).
 */
@TestConfiguration(proxyBeanMethods = false)
public class DesvioDePrueba {

    @Bean
    @Primary
    Calculador calculadorDePrueba() {
        return new Calculador();
    }

    public static class Calculador implements CalculadorDesvio {

        private final Map<Coordenada, ResultadoDesvio> porRetiro = new HashMap<>();
        private boolean aproximado;
        private int llamadas;

        public void limpiar() {
            porRetiro.clear();
            aproximado = false;
            llamadas = 0;
        }

        public void conDesvio(Coordenada retiro, double desvioKm) {
            porRetiro.put(retiro, new ResultadoDesvio(desvioKm, false));
        }

        /** Como si la API de rutas estuviera caída: todos los resultados salen marcados como aproximados. */
        public void simularCaidaDeLaApi() {
            aproximado = true;
        }

        public int llamadas() {
            return llamadas;
        }

        @Override
        public ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro,
                                        Coordenada entrega) {
            llamadas++;
            double desvioKm = porRetiro.containsKey(retiro) ? porRetiro.get(retiro).desvioKm() : 5.0;
            return new ResultadoDesvio(desvioKm, aproximado);
        }
    }
}
