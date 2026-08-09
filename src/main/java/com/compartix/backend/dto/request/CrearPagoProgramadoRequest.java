package com.compartix.backend.dto.request;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class CrearPagoProgramadoRequest {
    private String nombre;
    private String descripcion;
    private BigDecimal montoMensual;
    // Desglose opcional: si no se envía, se asume todo a carnaval
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diaLimitePago;
    private String tipoMulta;
    private BigDecimal montoMulta;
    private BigDecimal porcentajeMulta;
}