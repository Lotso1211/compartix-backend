package com.compartix.backend.repository;

import com.compartix.backend.entity.SaldoGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaldoGrupoRepository extends JpaRepository<SaldoGrupo, Long> {
    Optional<SaldoGrupo> findByGrupoId(Long grupoId);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM saldo_grupo WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
}