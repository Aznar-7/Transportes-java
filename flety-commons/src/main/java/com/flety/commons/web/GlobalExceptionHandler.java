package com.flety.commons.web;

import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte las excepciones de Flety en respuestas ProblemDetail (RFC 9457), con el mismo formato en los dos servicios.
 * Hereda de ResponseEntityExceptionHandler, que ya resuelve los errores propios de Spring MVC (JSON mal formado,
 * método no permitido, etc.) también como ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail recursoNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto con el estado actual", ex.getMessage());
    }

    /**
     * La base rechazó el cambio por una restricción, por ejemplo un índice único (CUIT repetido, RN-14).
     * No se devuelve el mensaje de la base porque trae detalles internos del esquema.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridadDeDatos(DataIntegrityViolationException ex) {
        log.info("Restricción de la base violada: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto con el estado actual",
                "El cambio choca con un dato que ya existe");
    }

    /** Dos pedidos modificaron el mismo registro a la vez y @Version detectó que este llegó segundo. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail bloqueoOptimista(OptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto con el estado actual",
                "Otro pedido modificó este recurso al mismo tiempo; volvé a intentarlo");
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail reglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida", ex.getMessage());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ProblemDetail servicioNoDisponible(ServicioNoDisponibleException ex) {
        log.warn("Servicio interno no disponible: {}", ex.getMessage(), ex);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", ex.getMessage());
    }

    @ExceptionHandler(ServicioExternoException.class)
    public ProblemDetail servicioExterno(ServicioExternoException ex) {
        log.warn("Falla de una API externa: {}", ex.getMessage(), ex);
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio externo no disponible", ex.getMessage());
    }

    /** Validaciones sobre parámetros sueltos (@RequestParam, @PathVariable) con @Validated en el controller. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail validacionDeParametros(ConstraintViolationException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errores.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Hay parámetros con errores");
        problema.setProperty("errores", errores);
        return problema;
    }

    /** Validaciones del cuerpo con @Valid: responde 400 y agrega qué campo falló y por qué. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Hay campos con errores");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
