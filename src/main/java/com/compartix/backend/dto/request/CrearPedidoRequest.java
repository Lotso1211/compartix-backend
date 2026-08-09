package com.compartix.backend.dto.request;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CrearPedidoRequest {
    private String nombre;
    private String descripcion;
    private LocalDate fecha;
    private List<ItemRequest> items;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    public static class ItemRequest {
        private String nombre;
        private BigDecimal precioUnitario;
        private String descripcion;
        private Map<Long, Integer> cantidadesPorUsuario;
    }
}