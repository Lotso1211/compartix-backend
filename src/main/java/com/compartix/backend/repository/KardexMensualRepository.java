package com.compartix.backend.repository;

import com.compartix.backend.entity.KardexMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KardexMensualRepository extends JpaRepository<KardexMensual, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM kardex_mensual WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    Optional<KardexMensual> findByGrupoIdAndUsuarioIdAndAnioAndMes(Long grupoId, Long usuarioId, Short anio, Short mes);
    List<KardexMensual> findByGrupoIdAndUsuarioIdOrderByAnioDescMesDesc(Long grupoId, Long usuarioId);
    List<KardexMensual> findByGrupoIdAndAnioAndMes(Long grupoId, Short anio, Short mes);
}