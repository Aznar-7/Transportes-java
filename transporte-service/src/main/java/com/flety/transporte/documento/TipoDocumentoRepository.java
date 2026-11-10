package com.flety.transporte.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoDocumentoRepository extends JpaRepository<TipoDocumento, Long> {

    List<TipoDocumento> findByAplicaAAndObligatorioTrue(AplicaA aplicaA);
}
