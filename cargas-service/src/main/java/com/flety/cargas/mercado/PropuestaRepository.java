package com.flety.cargas.mercado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropuestaRepository extends JpaRepository<Propuesta, Long> {

    List<Propuesta> findByCargaIdAndEstado(Long cargaId, EstadoPropuesta estado);

    /** Cuenta en cualquier estado: una por transportista y por carga, aunque la anterior se haya retirado (RN-03). */
    boolean existsByCargaIdAndTransportistaSub(Long cargaId, String transportistaSub);
}
