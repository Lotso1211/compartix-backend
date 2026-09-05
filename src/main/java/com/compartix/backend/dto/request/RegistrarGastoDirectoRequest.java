package com.compartix.backend.dto.request;

import com.compartix.backend.enums.TipoFondo;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RegistrarGastoDirectoRequest {
    private String descripcion;
    private BigDecimal monto;
    private LocalDate fecha;

    @NotNull(message = "Debes indicar a qué fondo pertenece el gasto")
    private TipoFondo fondo;

    // Solo se usan cuando fondo == MIXTO (deben sumar exactamente el monto total).
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;
}
