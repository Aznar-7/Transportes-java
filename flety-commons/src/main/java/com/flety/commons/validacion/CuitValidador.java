package com.flety.commons.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CuitValidador implements ConstraintValidator<CuitValido, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // delegar presencia a @NotNull / @NotBlank
        }

        // Permite 11 dígitos directos o con guiones (XX-XXXXXXXX-X)
        if (!value.matches("^\\d{11}$") && !value.matches("^\\d{2}-\\d{8}-\\d$")) {
            return false;
        }

        String cuit = value.replace("-", "");

        int[] factores = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int acumulado = 0;
        for (int i = 0; i < 10; i++) {
            acumulado += (cuit.charAt(i) - '0') * factores[i];
        }

        int resto = acumulado % 11;
        int verificador;
        if (resto == 0) {
            verificador = 0; // Caso 11
        } else if (resto == 1) {
            return false; // Caso 10 inválido
        } else {
            verificador = 11 - resto;
        }

        return verificador == (cuit.charAt(10) - '0');
    }
}