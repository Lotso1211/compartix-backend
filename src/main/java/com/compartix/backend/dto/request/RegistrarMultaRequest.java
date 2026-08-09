package com.compartix.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegistrarMultaRequest {

    @NotNull(message = "El miembro es obligatorio")
    private Long usuarioId;

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private Integer periodoMes;
    private Integer periodoAnio;
}