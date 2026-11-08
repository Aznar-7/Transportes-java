package com.flety.cargas.mercado;

import com.flety.commons.excepciones.ConflictoException;
import com.flety.commons.excepciones.RecursoNoEncontradoException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * El paso con base de datos de proponer. Va en un bean aparte de PropuestaService porque @Transactional funciona con
 * un proxy: si PropuestaService se llamara a sí mismo, no se abriría ninguna transacción.
 */
@Component
public class AltaDePropuesta {

    private static final Set<EstadoCarga> ABIERTAS = EnumSet.of(EstadoCarga.PUBLICADA, EstadoCarga.CON_PROPUESTAS);

    private final CargaRepository cargas;
    private final PropuestaRepository propuestas;

    public AltaDePropuesta(CargaRepository cargas, PropuestaRepository propuestas) {
        this.cargas = cargas;
        this.propuestas = propuestas;
    }

    /** Las condiciones que pueden cambiar mientras se habla con transporte-service: son 409 y se miran dos veces. */
    public void validarQueAdmitePropuesta(Carga carga, String transportistaSub) {
        if (!ABIERTAS.contains(carga.getEstado())) {
            throw new ConflictoException("La carga ya no admite propuestas (está " + carga.getEstado() + ")");
        }
        if (carga.getCargaHasta().isBefore(Instant.now())) {
            throw new ConflictoException("La ventana de carga ya cerró");
        }
        if (propuestas.existsByCargaIdAndTransportistaSub(carga.getId(), transportistaSub)) {
            throw new ConflictoException("Ya hiciste una propuesta para esta carga");
        }
    }

    /**
     * Crea la propuesta y pasa la carga a CON_PROPUESTAS, juntas o ninguna. Vuelve a validar porque durante las
     * llamadas a transporte-service pudo haber llegado otro pedido; ahora, con la carga bloqueada, ya no puede.
     *
     * @param propuesta los datos ya validados; la carga se le asigna acá, leída con el bloqueo
     */
    @Transactional
    public Propuesta crear(Long cargaId, Propuesta propuesta) {
        Carga carga = cargas.bloquearParaProponer(cargaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carga", cargaId));
        validarQueAdmitePropuesta(carga, propuesta.getTransportistaSub());
        propuesta.setCarga(carga);
        carga.setEstado(EstadoCarga.CON_PROPUESTAS);
        return propuestas.save(propuesta);
    }
}
