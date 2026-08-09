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
public class KardexResponse {
    private Long id;
    private UsuarioResponse usuario;
    private GrupoResponse grupo;
    private BigDecimal totalAportes;
    private BigDecimal totalGastosCompartidos;
    private BigDecimal totalGastosIndividuales;
    private BigDecimal saldoActual;
    private LocalDateTime actualizadoEn;
    private BigDecimal totalMultas;
}