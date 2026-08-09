package com.compartix.backend.dto.request;

import com.compartix.backend.enums.TipoFondo;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RegistrarIngresoDirectoRequest {
    private String descripcion;
    private BigDecimal monto;
    private LocalDate fecha;

    @NotNull(message = "Debes indicar a qué fondo pertenece el ingreso")
    private TipoFondo fondo;
}