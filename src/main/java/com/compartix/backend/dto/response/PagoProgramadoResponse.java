package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PagoProgramadoResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal montoMensual;
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diaLimitePago;
    private String tipoMulta;
    private BigDecimal montoMulta;
    private BigDecimal porcentajeMulta;
    private Boolean activo;
}