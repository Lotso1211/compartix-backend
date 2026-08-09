package com.compartix.backend.repository;

import com.compartix.backend.entity.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM pedido_items WHERE pedido_id IN (SELECT id FROM pedidos WHERE grupo_id = :grupoId)", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<PedidoItem> findByPedidoId(Long pedidoId);
}