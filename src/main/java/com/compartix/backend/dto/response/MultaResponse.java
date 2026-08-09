package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MultaResponse {
    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private String motivo;
    private BigDecimal monto;
    private String estado;
    private LocalDate fecha;
    private LocalDate fechaPago;
    private Integer periodoMes;
    private Integer periodoAnio;
}