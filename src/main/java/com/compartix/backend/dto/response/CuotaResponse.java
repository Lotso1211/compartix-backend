package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CuotaResponse {
    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private Long pagoProgramadoId;
    private String nombrePago;
    private Integer anio;
    private Integer mes;
    private BigDecimal monto;
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;
    private String estado;
    private LocalDate fechaPago;
    private Boolean multaAplicada;
    private BigDecimal montoMulta;
}