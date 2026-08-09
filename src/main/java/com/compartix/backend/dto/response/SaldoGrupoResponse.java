package com.compartix.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaldoGrupoResponse {
    private Long grupoId;
    private String nombreGrupo;
    private BigDecimal totalIngresos;
    private BigDecimal totalEgresos;
    private BigDecimal totalMultas;
    private BigDecimal saldoDisponible;
    private BigDecimal saldoCarnaval;
    private BigDecimal saldoAhorro;
    private LocalDateTime actualizadoEn;
}