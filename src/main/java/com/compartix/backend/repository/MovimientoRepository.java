package com.compartix.backend.repository;

import com.compartix.backend.entity.Movimiento;
import com.compartix.backend.enums.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM movimientos WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);

    List<Movimiento> findByGrupoIdOrderByFechaDesc(Long grupoId);

    List<Movimiento> findByGrupoIdAndTipoOrderByFechaDesc(Long grupoId, TipoMovimiento tipo);

    @Query("SELECT m FROM Movimiento m WHERE m.grupo.id = :grupoId AND m.fecha BETWEEN :inicio AND :fin ORDER BY m.fecha DESC")
    List<Movimiento> findByGrupoIdAndFechaBetween(
            @Param("grupoId") Long grupoId,
            @Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin);
}