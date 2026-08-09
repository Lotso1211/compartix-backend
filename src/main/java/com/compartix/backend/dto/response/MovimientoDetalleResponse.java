package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MovimientoDetalleResponse {
    private Long usuarioId;
    private String nombreUsuario;
    private BigDecimal cantidad;
    private BigDecimal monto;
    private Boolean afectaKardex;
}