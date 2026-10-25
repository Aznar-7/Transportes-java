package com.flety.cargas.transporte;

import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.RetornoInternoDTO;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

/**
 * TransporteClient de verdad: le pega por HTTP a los endpoints /internal de transporte-service
 * (contrato en docs/contrato-interno/transporte-interno.yml). Los JSON se mapean directo a los records de flety-commons.
 * <p>
 * Falla cerrada: el único error que se traduce a algo de negocio es el 404. Cualquier otra cosa (transporte apagado,
 * timeout, un 5xx, un 401 porque no llegó el token, un JSON que no respeta el contrato) es ServicioNoDisponibleException,
 * que sale como 503. Nunca se sigue con un dato inventado ni con "habilitada" por defecto.
 */
public class TransporteClientHttp implements TransporteClient {

    private final RestClient restClient;

    /** Recibe el RestClient ya armado con la URL base y los timeouts (ver TransporteClientConfig). */
    public TransporteClientHttp(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RetornoInternoDTO obtenerRetorno(Long retornoId) {
        return pedir(restClient.get().uri("/internal/retornos/{id}", retornoId),
                RetornoInternoDTO.class, "Retorno", retornoId);
    }

    /** "hasta" viaja en ISO-8601 UTC (2026-11-05T21:00:00Z), como pide el contrato. */
    @Override
    public HabilitacionUnidadDTO consultarHabilitacion(Long unidadId, Instant hasta) {
        return pedir(restClient.get().uri(uri -> uri.path("/internal/unidades/{id}/habilitacion")
                        .queryParam("hasta", hasta.toString())
                        .build(unidadId)),
                HabilitacionUnidadDTO.class, "Unidad", unidadId);
    }

    private <T> T pedir(RestClient.RequestHeadersSpec<?> pedido, Class<T> tipo, String recurso, Long id) {
        T cuerpo;
        try {
            cuerpo = pedido.retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (req, res) -> {
                        throw new RecursoNoEncontradoException(recurso, id);
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ServicioNoDisponibleException(
                                "transporte-service respondió " + res.getStatusCode().value() + " al pedir " + recurso + " " + id);
                    })
                    .body(tipo);
        } catch (RestClientException ex) {
            // Conexión rechazada, timeout o un JSON que no se pudo leer: transporte no está respondiendo como debe.
            throw new ServicioNoDisponibleException("transporte-service no responde al pedir " + recurso + " " + id, ex);
        }
        if (cuerpo == null) {
            throw new ServicioNoDisponibleException("transporte-service respondió vacío al pedir " + recurso + " " + id);
        }
        return cuerpo;
    }
}
