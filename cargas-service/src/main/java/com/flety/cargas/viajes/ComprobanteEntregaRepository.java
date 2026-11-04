package com.flety.cargas.viajes;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Solo guardar y leer: el comprobante no se edita ni se borra (es la prueba si hay una disputa), así que el
 * repositorio no ofrece esas operaciones. Por eso extiende Repository y no JpaRepository.
 */
public interface ComprobanteEntregaRepository extends Repository<ComprobanteEntrega, Long> {

    ComprobanteEntrega save(ComprobanteEntrega comprobante);

    Optional<ComprobanteEntrega> findByViajeId(Long viajeId);
}
