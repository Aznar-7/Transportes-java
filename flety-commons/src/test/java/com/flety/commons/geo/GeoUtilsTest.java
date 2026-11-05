package com.flety.commons.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    // Coordenadas reales de referencia en Argentina
    private static final Coordenada CORDOBA = new Coordenada(-31.4201, -64.1888);
    private static final Coordenada VILLA_MARIA = new Coordenada(-32.4075, -63.2402);
    private static final Coordenada ROSARIO = new Coordenada(-32.9468, -60.6393);
    private static final Coordenada BUENOS_AIRES = new Coordenada(-34.6037, -58.3816);
    private static final Coordenada MENDOZA = new Coordenada(-32.8895, -68.8458);

    @Nested
    @DisplayName("Cálculo de distancia en línea recta (Haversine)")
    class DistanciaHaversine {

        @Test
        @DisplayName("Distancia entre un punto y sí mismo es 0.0")
        void puntoASiMismoEsCero() {
            assertThat(GeoUtils.distanciaEnKm(CORDOBA, CORDOBA)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("La distancia es simétrica: d(A, B) == d(B, A)")
        void distanciaEsSimetrica() {
            double ida = GeoUtils.distanciaEnKm(CORDOBA, ROSARIO);
            double vuelta = GeoUtils.distanciaEnKm(ROSARIO, CORDOBA);

            assertThat(ida).isEqualTo(vuelta);
        }

        @Test
        @DisplayName("Distancia geodésica conocida entre Córdoba y Rosario (~374 km)")
        void distanciaConocidaCordobaRosario() {
            double distancia = GeoUtils.distanciaEnKm(CORDOBA, ROSARIO);

            // Haversine da aprox 374.67 km
            assertThat(distancia).isCloseTo(374.67, within(1.0));
        }

        @Test
        @DisplayName("Distancia geodésica conocida entre Córdoba y Buenos Aires (~646 km)")
        void distanciaConocidaCordobaBuenosAires() {
            double distancia = GeoUtils.distanciaEnKm(CORDOBA, BUENOS_AIRES);

            assertThat(distancia).isCloseTo(646.74, within(1.0));
        }

        @Test
        @DisplayName("Lanza excepción si alguna coordenada es nula")
        void coordenadasNulasLanzanExcepcion() {
            assertThatThrownBy(() -> GeoUtils.distanciaEnKm(null, ROSARIO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no pueden ser nulas");

            assertThatThrownBy(() -> GeoUtils.distanciaEnKm(CORDOBA, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no pueden ser nulas");
        }
    }

    @Nested
    @DisplayName("Distancia acumulada de una ruta (secuencia de puntos)")
    class DistanciaAcumulada {

        @Test
        @DisplayName("Lista nula, vacía o con un solo punto devuelve 0.0")
        void listaVaciaOUnPuntoDevuelveCero() {
            assertThat(GeoUtils.distanciaEnKm((List<Coordenada>) null)).isEqualTo(0.0);
            assertThat(GeoUtils.distanciaEnKm(List.of())).isEqualTo(0.0);
            assertThat(GeoUtils.distanciaEnKm(List.of(CORDOBA))).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Suma correctamente los tramos consecutivos de una ruta")
        void sumaTramosConsecutivos() {
            double tramo1 = GeoUtils.distanciaEnKm(CORDOBA, VILLA_MARIA);
            double tramo2 = GeoUtils.distanciaEnKm(VILLA_MARIA, ROSARIO);

            double distanciaRuta = GeoUtils.distanciaEnKm(List.of(CORDOBA, VILLA_MARIA, ROSARIO));

            assertThat(distanciaRuta).isCloseTo(tramo1 + tramo2, within(0.001));
        }
    }

    @Nested
    @DisplayName("Caja geográfica alrededor de una ruta (Bounding Box)")
    class CajaAlrededorRuta {

        @Test
        @DisplayName("Contiene a todos los puntos de la ruta original")
        void contienePuntosDeLaRuta() {
            List<Coordenada> ruta = List.of(CORDOBA, VILLA_MARIA, ROSARIO);
            CajaGeografica caja = GeoUtils.cajaAlrededor(ruta, 30.0);

            for (Coordenada p : ruta) {
                assertThat(caja.contiene(p)).isTrue();
            }
        }

        @Test
        @DisplayName("Contiene puntos cercanos a la ruta dentro del margen")
        void contienePuntosCercanosDentroDelMargen() {
            // Ruta Córdoba -> Rosario con 50 km de margen
            List<Coordenada> ruta = List.of(CORDOBA, ROSARIO);
            CajaGeografica caja = GeoUtils.cajaAlrededor(ruta, 50.0);

            // Villa María está a mitad de camino y cerca de la traza
            assertThat(caja.contiene(VILLA_MARIA)).isTrue();
        }

        @Test
        @DisplayName("Excluye puntos lejanos fuera de la ruta y de su margen")
        void excluyePuntosLejanos() {
            List<Coordenada> ruta = List.of(CORDOBA, ROSARIO);
            CajaGeografica caja = GeoUtils.cajaAlrededor(ruta, 30.0);

            // Mendoza está muy al oeste, fuera de la caja Córdoba - Rosario
            assertThat(caja.contiene(MENDOZA)).isFalse();
        }

        @Test
        @DisplayName("CajaGeografica.contiene(null) devuelve false sin lanzar excepción")
        void puntoNuloNoEstaContenido() {
            CajaGeografica caja = new CajaGeografica(-35, -30, -65, -60);
            assertThat(caja.contiene(null)).isFalse();
        }

        @Test
        @DisplayName("Los límites de la caja son mayores a los extremos de los puntos por el margen")
        void limitesExpandidosPorMargen() {
            List<Coordenada> ruta = List.of(CORDOBA, ROSARIO);
            CajaGeografica caja = GeoUtils.cajaAlrededor(ruta, 30.0);

            double minLat = Math.min(CORDOBA.lat(), ROSARIO.lat());
            double maxLat = Math.max(CORDOBA.lat(), ROSARIO.lat());
            double minLon = Math.min(CORDOBA.lng(), ROSARIO.lng());
            double maxLon = Math.max(CORDOBA.lng(), ROSARIO.lng());

            assertThat(caja.latMin()).isLessThan(minLat);
            assertThat(caja.latMax()).isGreaterThan(maxLat);
            assertThat(caja.lonMin()).isLessThan(minLon);
            assertThat(caja.lonMax()).isGreaterThan(maxLon);
        }

        @Test
        @DisplayName("Lanza excepción con lista nula, vacía o margen negativo")
        void argumentosInvalidosLanzanExcepcion() {
            assertThatThrownBy(() -> GeoUtils.cajaAlrededor(null, 30.0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> GeoUtils.cajaAlrededor(List.of(), 30.0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> GeoUtils.cajaAlrededor(List.of(CORDOBA), -10.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no puede ser negativo");
        }
    }
}

