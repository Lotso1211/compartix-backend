package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SolicitudPagoResponse {
    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private String tipo;
    private Long cuotaId;
    private Long multaId;
    private String descripcion;
    private BigDecimal monto;
    private String comprobanteUrl;
    private String estado;
    private String motivoRechazo;
    private String revisadoPor;
    private LocalDateTime fechaRevision;
    private LocalDateTime creadoEn;
}
