package com.compartix.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IaConsultaResponse {
    private String  respuesta;
    private String  intencion;
    private Double  confianza;
    private Boolean esAccion;
    private String  accionRealizada;
    private Long    movimientoRegistradoId;
}
