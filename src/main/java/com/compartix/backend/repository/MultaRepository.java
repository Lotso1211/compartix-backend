package com.compartix.backend.repository;

import com.compartix.backend.entity.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MultaRepository extends JpaRepository<Multa, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM multas WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<Multa> findByGrupoIdOrderByFechaDesc(Long grupoId);
    List<Multa> findByGrupoIdAndUsuarioIdOrderByFechaDesc(Long grupoId, Long usuarioId);
    List<Multa> findByGrupoIdAndEstadoOrderByFechaDesc(Long grupoId, String estado);
    java.util.Optional<Multa> findByMovimientoId(Long movimientoId);
}