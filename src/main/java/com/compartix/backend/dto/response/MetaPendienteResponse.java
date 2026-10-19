package com.compartix.backend.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MetaPendienteResponse {
    private Long usuarioId;
    private String nombreUsuario;
    private String tipo; // CARNAVAL | PEDIDO
    private String nombre;
    private BigDecimal aportado;
    private BigDecimal cupoBs;
    private BigDecimal falta;
    private BigDecimal cuotaSugerida;
    private BigDecimal avance;
}
