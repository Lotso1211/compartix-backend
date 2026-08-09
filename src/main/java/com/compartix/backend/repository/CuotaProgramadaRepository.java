package com.compartix.backend.repository;

import com.compartix.backend.entity.CuotaProgramada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CuotaProgramadaRepository extends JpaRepository<CuotaProgramada, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM cuotas_programadas WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<CuotaProgramada> findByGrupoIdAndAnioAndMes(Long grupoId, Integer anio, Integer mes);
    List<CuotaProgramada> findByUsuarioIdAndGrupoId(Long usuarioId, Long grupoId);
    List<CuotaProgramada> findByPagoProgramadoId(Long pagoProgramadoId);
    List<CuotaProgramada> findByGrupoIdAndEstado(Long grupoId, String estado);
    boolean existsByPagoProgramadoIdAndUsuarioIdAndAnioAndMes(Long pagoProgramadoId, Long usuarioId, Integer anio, Integer mes);
    java.util.Optional<CuotaProgramada> findByMovimientoId(Long movimientoId);
}