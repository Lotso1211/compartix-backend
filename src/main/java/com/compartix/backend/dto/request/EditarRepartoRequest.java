package com.compartix.backend.dto.request;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class EditarRepartoRequest {
    private BigDecimal montoCarnaval;
    private BigDecimal montoAhorro;
    // Meta de carnaval (opcional, solo si se quiere configurar/actualizar)
    private BigDecimal cupoCarnavalUsd;
    private BigDecimal tipoCambio;
    private Integer mesesRestantesCarnaval;
}
