package com.flety.commons.web;

import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import com.flety.commons.excepciones.ReglaNegocioException;
import com.flety.commons.excepciones.ServicioExternoException;
import com.flety.commons.excepciones.ServicioNoDisponibleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada excepción de Flety, lanzada desde un controller, tiene que salir como ProblemDetail con su código HTTP.
 * Se usa MockMvc en modo standalone: un controller de prueba más el handler, sin levantar todo Spring.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ControllerDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reglaDeNegocioDevuelve422() throws Exception {
        mockMvc.perform(get("/regla"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                .andExpect(jsonPath("$.detail").value("El dador no declaró que contrata como empresa"));
    }

    @Test
    void recursoNoEncontradoDevuelve404() throws Exception {
        mockMvc.perform(get("/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Carga 7 no existe"));
    }

    @Test
    void conflictoDevuelve409() throws Exception {
        mockMvc.perform(get("/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void servicioNoDisponibleDevuelve503() throws Exception {
        mockMvc.perform(get("/no-disponible"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Servicio no disponible"));
    }

    @Test
    void servicioExternoDevuelve503() throws Exception {
        mockMvc.perform(get("/externo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Servicio externo no disponible"));
    }

    @Test
    void validacionDelCuerpoDevuelve400ConLosCampos() throws Exception {
        mockMvc.perform(post("/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"pesoToneladas\": -3}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.pesoToneladas").exists());
    }

    record PedidoDePrueba(@NotBlank String nombre, @Positive double pesoToneladas) {
    }

    @RestController
    static class ControllerDePrueba {

        @GetMapping("/regla")
        void regla() {
            throw new ReglaNegocioException("El dador no declaró que contrata como empresa");
        }

        @GetMapping("/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("Carga", 7);
        }

        @GetMapping("/conflicto")
        void conflicto() {
            throw new ConflictoException("Ya tenés una propuesta para esta carga");
        }

        @GetMapping("/no-disponible")
        void noDisponible() {
            throw new ServicioNoDisponibleException("transporte-service no responde");
        }

        @GetMapping("/externo")
        void externo() {
            throw new ServicioExternoException("Georef no responde");
        }

        @PostMapping("/validar")
        void validar(@Valid @RequestBody PedidoDePrueba pedido) {
        }
    }
}
