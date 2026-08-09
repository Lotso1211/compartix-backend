package com.compartix.backend.repository;

import com.compartix.backend.entity.Kardex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KardexRepository extends JpaRepository<Kardex, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM kardex WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    Optional<Kardex> findByGrupoIdAndUsuarioId(Long grupoId, Long usuarioId);
    List<Kardex> findByGrupoId(Long grupoId);

}