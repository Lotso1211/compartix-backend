package com.compartix.backend.dto.request;

import com.compartix.backend.enums.TipoFondo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegistrarAporteRequest {

    @NotNull(message = "El miembro es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotNull(message = "Debes indicar a qué fondo pertenece el aporte")
    private TipoFondo fondo;

    private Long categoriaId;
    private String descripcion;
    private String comprobanteUrl;
}