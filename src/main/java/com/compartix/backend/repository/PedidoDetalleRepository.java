package com.compartix.backend.repository;

import com.compartix.backend.entity.PedidoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM pedido_detalle WHERE pedido_id IN (SELECT id FROM pedidos WHERE grupo_id = :grupoId)", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<PedidoDetalle> findByPedidoId(Long pedidoId);
    List<PedidoDetalle> findByPedidoIdAndUsuarioId(Long pedidoId, Long usuarioId);
}