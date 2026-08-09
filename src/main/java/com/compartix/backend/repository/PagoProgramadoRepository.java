package com.compartix.backend.repository;

import com.compartix.backend.entity.PagoProgramado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PagoProgramadoRepository extends JpaRepository<PagoProgramado, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM pagos_programados WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<PagoProgramado> findByGrupoIdAndActivoTrue(Long grupoId);
    List<PagoProgramado> findByGrupoId(Long grupoId);
}