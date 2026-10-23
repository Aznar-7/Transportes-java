package com.flety.commons.fake;

import com.flety.commons.geo.CalculadorDesvio;
import com.flety.commons.geo.Coordenada;
import com.flety.commons.geo.ResultadoDesvio;

/**
 * CalculadorDesvio falso: devuelve siempre el mismo desvío, configurable.
 * Sirve para probar el orden de resultados (RN-02) y el modo aproximado sin la API de rutas.
 */
public class CalculadorDesvioFake implements CalculadorDesvio {

    private double desvioKm = 10.0;
    private boolean aproximado;

    public CalculadorDesvioFake conDesvio(double desvioKm, boolean aproximado) {
        this.desvioKm = desvioKm;
        this.aproximado = aproximado;
        return this;
    }

    @Override
    public ResultadoDesvio calcular(Coordenada origenRetorno, Coordenada destinoRetorno, Coordenada retiro, Coordenada entrega) {
        return new ResultadoDesvio(desvioKm, aproximado);
    }
}
