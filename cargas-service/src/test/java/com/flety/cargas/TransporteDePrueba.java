package com.flety.cargas;

import com.flety.cargas.transporte.TransporteClientFake;
import com.flety.commons.contrato.HabilitacionUnidadDTO;
import com.flety.commons.contrato.TipoCarroceria;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reemplaza el TransporteClient HTTP por el fake de FLY-5: los tests de viajes no necesitan transporte-service
 * levantado. Es @Primary, así lo inyecta Spring en vez del real. Se suma con @Import(TransporteDePrueba.class)
 * y el test le carga las habilitaciones que necesite.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TransporteDePrueba {

    @Bean
    @Primary
    TransporteClientFake transporteFake() {
        return new TransporteClientFake();
    }

    /** Una unidad habilitada de Transportes Pérez SRL, con la patente AB123CD. */
    public static HabilitacionUnidadDTO habilitada(Long unidadId) {
        return new HabilitacionUnidadDTO(unidadId, true, List.of(), "AB123CD", TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00"), new BigDecimal("50000000.00"),
                3L, "Transportes Pérez SRL", "+54 351 555-1234", "sub-transportista1");
    }

    /** La misma unidad, pero con documentos que no llegan a la fecha del viaje. */
    public static HabilitacionUnidadDTO noHabilitada(Long unidadId, String... motivos) {
        return new HabilitacionUnidadDTO(unidadId, false, List.of(motivos), "AB123CD", TipoCarroceria.SIDER,
                new BigDecimal("28.00"), new BigDecimal("90.00"), new BigDecimal("50000000.00"),
                3L, "Transportes Pérez SRL", "+54 351 555-1234", "sub-transportista1");
    }
}
