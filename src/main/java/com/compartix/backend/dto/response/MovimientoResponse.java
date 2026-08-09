package com.compartix.backend.dto.response;

import com.compartix.backend.enums.TipoMovimiento;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MovimientoResponse {
    private Long id;
    private TipoMovimiento tipo;
    private String descripcion;
    private BigDecimal montoTotal;
    private LocalDate fecha;
    private String comprobanteUrl;
    private Boolean origenIa;
    private String fondo;
    private UsuarioResponse registradoPor;
    private LocalDateTime creadoEn;
    private String usuarioAfectado;
}