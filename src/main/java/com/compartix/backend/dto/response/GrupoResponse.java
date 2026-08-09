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
public class GrupoResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private String codigoInvitacion;
    private BigDecimal cuotaBase;
    private String moneda;
    private Boolean activo;
    private BigDecimal alertaSaldoCarnaval;
    private BigDecimal alertaSaldoAhorro;
    private LocalDateTime creadoEn;
}