package com.compartix.backend.repository;

import com.compartix.backend.entity.SolicitudPago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitudPagoRepository extends JpaRepository<SolicitudPago, Long> {

    List<SolicitudPago> findByGrupoIdOrderByCreadoEnDesc(Long grupoId);

    List<SolicitudPago> findByGrupoIdAndEstadoOrderByCreadoEnDesc(Long grupoId, String estado);

    List<SolicitudPago> findByUsuarioIdAndGrupoIdOrderByCreadoEnDesc(Long usuarioId, Long grupoId);

    boolean existsByCuotaIdAndEstado(Long cuotaId, String estado);

    boolean existsByMultaIdAndEstado(Long multaId, String estado);
}
