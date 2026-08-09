package com.compartix.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactivacionInfoResponse {
    private boolean conCarnaval;
    private BigDecimal ahorroAdeudado;    // todo el ahorro acumulado que le falta (todas las gestiones)
    private BigDecimal carnavalAdeudado;  // carnaval de la gestión actual transcurrida (0 si no participa)
    private BigDecimal total;
}
