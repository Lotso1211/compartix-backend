package com.compartix.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IaEscaneoResponse {
    private Double  monto;
    private String  fecha;
    private String  descripcion;
    private String  textoExtraido;
    private String  tipoDocumento;
    private Double  confianza;
    private String  resumen;
    private Boolean exitoso;
}
