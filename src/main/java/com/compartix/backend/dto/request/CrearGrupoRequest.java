package com.compartix.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearGrupoRequest {

    @NotBlank(message = "El nombre del grupo es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La cuota base es obligatoria")
    @DecimalMin(value = "0.0", message = "La cuota base no puede ser negativa")
    private BigDecimal cuotaBase;

    private String moneda = "BOB";
}