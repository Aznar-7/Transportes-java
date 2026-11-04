package com.flety.cargas.viajes;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Responde 409 cuando el estado del viaje no permite la acción (TransicionNoPermitidaException).
 * <p>
 * Hace falta porque esa excepción hereda de ReglaNegocioException, y el GlobalExceptionHandler de flety-commons
 * la respondería con 422. Spring consulta los @RestControllerAdvice en orden: con HIGHEST_PRECEDENCE este se mira
 * primero, y como solo atiende TransicionNoPermitidaException, todas las demás excepciones siguen yendo al
 * manejador común. El formato es el mismo ProblemDetail, con el mismo título que el resto de los 409.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ViajesExceptionHandler {

    @ExceptionHandler(TransicionNoPermitidaException.class)
    public ProblemDetail transicionNoPermitida(TransicionNoPermitidaException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problema.setTitle("Conflicto con el estado actual");
        problema.setProperty("estadoActual", ex.getEstado());
        return problema;
    }
}
