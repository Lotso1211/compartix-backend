package com.compartix.backend.repository;

import com.compartix.backend.entity.CategoriaMovimiento;
import com.compartix.backend.enums.TipoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaMovimientoRepository extends JpaRepository<CategoriaMovimiento, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM categorias_movimiento WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<CategoriaMovimiento> findByGrupoIdAndActivoTrue(Long grupoId);
    List<CategoriaMovimiento> findByGrupoIsNullAndActivoTrue();  // categorías globales del sistema
    List<CategoriaMovimiento> findByGrupoIdAndTipoAndActivoTrue(Long grupoId, TipoCategoria tipo);
}