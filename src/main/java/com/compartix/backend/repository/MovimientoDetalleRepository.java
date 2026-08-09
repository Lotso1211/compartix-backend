package com.compartix.backend.repository;

import com.compartix.backend.entity.MovimientoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoDetalleRepository extends JpaRepository<MovimientoDetalle, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM movimiento_detalle WHERE movimiento_id IN (SELECT id FROM movimientos WHERE grupo_id = :grupoId)", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<MovimientoDetalle> findByMovimientoId(Long movimientoId);
    List<MovimientoDetalle> findByUsuarioIdAndAfectaKardexTrue(Long usuarioId);
    List<MovimientoDetalle> findByMovimientoGrupoIdAndUsuarioId(Long grupoId, Long usuarioId);

}