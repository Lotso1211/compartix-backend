package com.compartix.backend.repository;

import com.compartix.backend.entity.Notificacion;
import com.compartix.backend.enums.TipoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM notificaciones WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    List<Notificacion> findByUsuarioIdAndLeidaFalseOrderByCreadoEnDesc(Long usuarioId);
    Long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    // Para deduplicar recordatorios generados on-demand
    boolean existsByUsuarioIdAndTituloAndTipo(Long usuarioId, String titulo, TipoNotificacion tipo);
}