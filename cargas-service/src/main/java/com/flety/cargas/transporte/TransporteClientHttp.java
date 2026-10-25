package com.flety.cargas.transporte;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * TransporteClient de verdad: le pega por HTTP a los endpoints /internal de transporte-service
 * (contrato en docs/contrato-interno/transporte-interno.yml). Los JSON se mapean directo a los records de flety-commons.
 */
public class TransporteClientHttp implements TransporteClient {

    private final RestClient restClient;

    /** Recibe el RestClient ya armado con la URL base y los timeouts (ver TransporteClientConfig). */
    public TransporteClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RetornoInternoDTO obtenerRetorno(Long retornoId) {
        return restClient.get()
                .uri("/internal/retornos/{id}", retornoId)
                .retrieve()
                .body(RetornoInternoDTO.class);
    }

    /** "hasta" viaja en ISO-8601 UTC (2026-11-05T21:00:00Z), como pide el contrato. */
    @Override
    public HabilitacionUnidadDTO consultarHabilitacion(Long unidadId, Instant hasta) {
        return restClient.get()
                .uri(uri -> uri.path("/internal/unidades/{id}/habilitacion")
                        .queryParam("hasta", hasta.toString())
                        .build(unidadId))
                .retrieve()
                .body(HabilitacionUnidadDTO.class);
    }
}
