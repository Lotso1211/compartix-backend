package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PedidoResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private String estado;
    private LocalDate fecha;
    private List<ItemResponse> items;
    private BigDecimal totalGeneral;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class ItemResponse {
        private Long id;
        private String nombre;
        private BigDecimal precioUnitario;
        private String descripcion;
        private List<DetalleResponse> detalles;
        private BigDecimal totalItem;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class DetalleResponse {
        private Long usuarioId;
        private String nombreUsuario;
        private Integer cantidad;
        private BigDecimal subtotal;
    }
}