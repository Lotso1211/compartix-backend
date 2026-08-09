package com.compartix.backend.dto.request;

import com.compartix.backend.enums.TipoFondo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class RegistrarGastoCompartidoRequest {

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @NotNull(message = "El monto total es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal montoTotal;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotEmpty(message = "Debe seleccionar al menos un miembro")
    private List<Long> usuarioIds;  // miembros que comparten el gasto

    @NotNull(message = "Debes indicar de qué fondo sale el gasto")
    private TipoFondo fondo;

    // Solo se usan (y se validan) cuando fondo == MIXTO
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;

    private Long categoriaId;
}