package com.compartix.backend.repository;

import com.compartix.backend.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM pedidos WHERE grupo_id = :grupoId", nativeQuery = true)
    void deleteByGrupoId(@Param("grupoId") Long grupoId);
    List<Pedido> findByGrupoIdOrderByCreadoEnDesc(Long grupoId);

}