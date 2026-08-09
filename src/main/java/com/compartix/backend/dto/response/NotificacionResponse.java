package com.compartix.backend.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionResponse {
    private Long id;
    private String titulo;
    private String mensaje;
    private String tipo;
    private Boolean leida;
    private Long grupoId;
    private String nombreGrupo;
    private LocalDateTime creadoEn;
}
