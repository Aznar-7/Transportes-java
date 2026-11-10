package com.flety.commons.validacion;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CuitValidadorTest {

    private final CuitValidador validador = new CuitValidador();
    private final Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();

    private record DtoDePrueba(@CuitValido String cuit) {}

    @Nested
    @DisplayName("Validación de CUITs válidos")
    class CuitsValidos {

        @ParameterizedTest(name = "CUIT válido sin guiones: {0}")
        @ValueSource(strings = {
                "30546671166", // UTN FRC (persona jurídica)
                "33693450239", // AFIP (persona jurídica)
                "20123456786", // Persona física
                "20123456700"  // Caso resto 0 -> dígito verificador 0
        })
        void cuitsValidosSinGuionesDevuelvenTrue(String cuit) {
            assertThat(validador.isValid(cuit, null)).isTrue();
            assertThat(beanValidator.validate(new DtoDePrueba(cuit))).isEmpty();
        }

        @ParameterizedTest(name = "CUIT válido con guiones: {0}")
        @ValueSource(strings = {
                "30-54667116-6",
                "33-69345023-9",
                "20-12345678-6",
                "20-12345670-0"
        })
        void cuitsValidosConGuionesDevuelvenTrue(String cuit) {
            assertThat(validador.isValid(cuit, null)).isTrue();
            assertThat(beanValidator.validate(new DtoDePrueba(cuit))).isEmpty();
        }

        @Test
        @DisplayName("null es válido para delegar la obligatoriedad a @NotNull o @NotBlank")
        void nullDevuelveTrue() {
            assertThat(validador.isValid(null, null)).isTrue();
            assertThat(beanValidator.validate(new DtoDePrueba(null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validación de CUITs inválidos")
    class CuitsInvalidos {

        @ParameterizedTest(name = "Dígito verificador incorrecto: {0}")
        @ValueSource(strings = {
                "30546671160",
                "30546671165",
                "30712345678", // CUIT inventado común con verificador erróneo
                "30-54667116-9"
        })
        void conDigitoVerificadorIncorrectoDevuelveFalse(String cuit) {
            assertThat(validador.isValid(cuit, null)).isFalse();
            assertThat(beanValidator.validate(new DtoDePrueba(cuit))).isNotEmpty();
        }

        @Test
        @DisplayName("Caso resto 1 (cálculo daría 10): inválido según regla AFIP incluso si termina en 9")
        void casoRestoUnoInvalido() {
            // 2000000080 genera una suma ponderada con resto 1 (11 - 1 = 10).
            // Ningún dígito verificador es admisible para esta base.
            String cuitTerminadoEnNueve = "20000000809";
            String cuitTerminadoEnCero = "20000000800";

            assertThat(validador.isValid(cuitTerminadoEnNueve, null)).isFalse();
            assertThat(validador.isValid(cuitTerminadoEnCero, null)).isFalse();
        }

        @ParameterizedTest(name = "Longitud o formato incorrecto: {0}")
        @ValueSource(strings = {
                "",
                "   ",
                "1234567890",      // 10 dígitos
                "123456789012",    // 12 dígitos
                "30-5466711-66",   // guión en posición errónea
                "30/54667116/6",   // separador no permitido
                "30a54667116b"     // caracteres alfanuméricos
        })
        void formatoInvalidoDevuelveFalse(String cuit) {
            assertThat(validador.isValid(cuit, null)).isFalse();
            assertThat(beanValidator.validate(new DtoDePrueba(cuit))).isNotEmpty();
        }
    }
}

