package com.flety.commons.geo;

import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.fake.RoutingClientFake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Patrón Strategy: cálculo de desvío de cargas (FLY-43)")
class CalculadorDesvioTest {

    // Coordenadas reales de referencia en Argentina
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);
    private static final Coordenada VILLA_MARIA = new Coordenada(-32.4075, -63.2402);
    private static final Coordenada MARCOS_JUAREZ = new Coordenada(-32.6978, -62.1066);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);

    @Nested
    @DisplayName("Estrategia RutaOpenRouteService")
    class EstrategiaRutaOpenRouteService {

        @Test
        @DisplayName("Cuando la API de rutas responde, devuelve desvío real y aproximado = false")
        void calculaDesvioRealYMarcaAproximadoFalse() {
            RoutingClient fakeClient = puntos -> {
                if (puntos.size() == 2) {
                    return new Ruta(380.0, 240.0, puntos);
                } else {
                    return new Ruta(435.0, 290.0, puntos);
                }
            };

            CalculadorDesvio calculador = new RutaOpenRouteService(fakeClient);
            ResultadoDesvio resultado = calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ);

            assertThat(resultado).isNotNull();
            assertThat(resultado.desvioKm()).isEqualTo(55.0);
            assertThat(resultado.aproximado()).isFalse();
        }

        @Test
        @DisplayName("Si el cliente de rutas falla, propaga ServicioExternoException")
        void propagaExcepcionCuandoFallaCliente() {
            RoutingClientFake clientCaido = new RoutingClientFake().simularCaida(true);
            CalculadorDesvio calculador = new RutaOpenRouteService(clientCaido);

            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(ServicioExternoException.class)
                    .hasMessageContaining("no responde");
        }

        @Test
        @DisplayName("Rechaza coordenadas nulas con IllegalArgumentException")
        void validaCoordenadasNulas() {
            CalculadorDesvio calculador = new RutaOpenRouteService(new RoutingClientFake());

            assertThatThrownBy(() -> calculador.calcular(null, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, null, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, null, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor rechaza RoutingClient nulo")
        void constructorRechazaClienteNulo() {
            assertThatThrownBy(() -> new RutaOpenRouteService(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Estrategia RutaLineaRecta")
    class EstrategiaRutaLineaRecta {

        @Test
        @DisplayName("Calcula desvío geodésico con GeoUtils y marca aproximado = true")
        void calculaDesvioGeodesicoConGeoUtilsYMarcaAproximadoTrue() {
            CalculadorDesvio calculador = new RutaLineaRecta();
            ResultadoDesvio resultado = calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ);

            double distanciaDirecta = GeoUtils.distanciaEnKm(CORDOBA, ROSARIO);
            double distanciaConDesvio = GeoUtils.distanciaEnKm(List.of(CORDOBA, VILLA_MARIA, MARCOS_JUAREZ, ROSARIO));
            double desvioEsperado = distanciaConDesvio - distanciaDirecta;

            assertThat(resultado).isNotNull();
            assertThat(resultado.desvioKm()).isCloseTo(desvioEsperado, within(0.0001));
            assertThat(resultado.aproximado()).isTrue();
        }

        @Test
        @DisplayName("Rechaza coordenadas nulas con IllegalArgumentException")
        void validaCoordenadasNulas() {
            CalculadorDesvio calculador = new RutaLineaRecta();

            assertThatThrownBy(() -> calculador.calcular(null, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, null, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, null, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Selección de estrategia y fallback ante caídas")
    class SeleccionEstrategiaYFallback {

        @Test
        @DisplayName("Con API activa utiliza la ruta real y marca aproximado = false")
        void usaRutaRealCuandoApiFunciona() {
            RoutingClient fakeClient = puntos -> {
                if (puntos.size() == 2) {
                    return new Ruta(380.0, 240.0, puntos);
                } else {
                    return new Ruta(420.0, 280.0, puntos);
                }
            };

            CalculadorDesvioConFallback calculador = new CalculadorDesvioConFallback(fakeClient);
            ResultadoDesvio resultado = calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ);

            assertThat(resultado).isNotNull();
            assertThat(resultado.desvioKm()).isEqualTo(40.0);
            assertThat(resultado.aproximado()).isFalse();
        }

        @Test
        @DisplayName("Cuando la API de rutas falla, conmuta a línea recta sin lanzar error y marca aproximado = true")
        void conmutaALineaRectaCuandoApiFallaSinLanzarError() {
            RoutingClientFake clientCaido = new RoutingClientFake().simularCaida(true);
            CalculadorDesvioConFallback calculador = new CalculadorDesvioConFallback(clientCaido);

            ResultadoDesvio resultado = calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ);

            double distanciaDirecta = GeoUtils.distanciaEnKm(CORDOBA, ROSARIO);
            double distanciaConDesvio = GeoUtils.distanciaEnKm(List.of(CORDOBA, VILLA_MARIA, MARCOS_JUAREZ, ROSARIO));
            double desvioEsperado = distanciaConDesvio - distanciaDirecta;

            assertThat(resultado).isNotNull();
            assertThat(resultado.desvioKm()).isCloseTo(desvioEsperado, within(0.0001));
            assertThat(resultado.aproximado()).isTrue();
        }

        @Test
        @DisplayName("Conmuta a fallback ante cualquier excepción inesperada en la estrategia principal")
        void conmutaAFallbackAnteCualquierExcepcionInesperada() {
            CalculadorDesvio principalConFallo = (o, d, r, e) -> {
                throw new RuntimeException("Error inesperado en servicio de rutas");
            };
            CalculadorDesvio fallback = new RutaLineaRecta();

            CalculadorDesvioConFallback calculador = new CalculadorDesvioConFallback(principalConFallo, fallback);
            ResultadoDesvio resultado = calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ);

            assertThat(resultado).isNotNull();
            assertThat(resultado.aproximado()).isTrue();
        }

        @Test
        @DisplayName("Validación defensiva de coordenadas nulas antes de invocar estrategias")
        void validaCoordenadasNulas() {
            CalculadorDesvioConFallback calculador = new CalculadorDesvioConFallback(new RoutingClientFake());

            assertThatThrownBy(() -> calculador.calcular(null, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, null, VILLA_MARIA, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, null, MARCOS_JUAREZ))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculador.calcular(CORDOBA, ROSARIO, VILLA_MARIA, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Constructor con estrategias rechaza parámetros nulos")
        void constructorRechazaEstrategiasNulas() {
            assertThatThrownBy(() -> new CalculadorDesvioConFallback(null, new RutaLineaRecta()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new CalculadorDesvioConFallback(new RutaLineaRecta(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Patrón Strategy: estrategias son intercambiables detrás de la interfaz CalculadorDesvio")
        void estrategiasSonIntercambiablesDetrasDeLaInterfaz() {
            CalculadorDesvio estrategia1 = new RutaLineaRecta();
            CalculadorDesvio estrategia2 = new RutaOpenRouteService(new RoutingClientFake());
            CalculadorDesvio estrategia3 = new CalculadorDesvioConFallback(new RoutingClientFake());

            // Todas comparten la misma interfaz y pueden ejecutarse polimórficamente
            assertThat(estrategia1.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ)).isNotNull();
            assertThat(estrategia2.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ)).isNotNull();
            assertThat(estrategia3.calcular(CORDOBA, ROSARIO, VILLA_MARIA, MARCOS_JUAREZ)).isNotNull();
        }
    }
}

