package com.compartix.backend.repository;

import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.enums.RolGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrupoMiembroRepository extends JpaRepository<GrupoMiembro, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM grupo_miembros WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    Optional<GrupoMiembro> findByGrupoIdAndUsuarioId(Long grupoId, Long usuarioId);
    List<GrupoMiembro> findByGrupoId(Long grupoId);
    List<GrupoMiembro> findByGrupoIdAndActivoTrue(Long grupoId);
    List<GrupoMiembro> findByUsuarioIdAndActivoTrue(Long usuarioId);
    Boolean existsByGrupoIdAndUsuarioId(Long grupoId, Long usuarioId);
    List<GrupoMiembro> findByGrupoIdAndRolAndActivoTrue(Long grupoId, RolGrupo rol);
}