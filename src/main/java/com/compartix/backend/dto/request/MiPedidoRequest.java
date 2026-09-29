package com.compartix.backend.dto.request;

import lombok.*;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class MiPedidoRequest {
    /** itemId -> cantidad que el miembro quiere para ese item (0 u omitido = no participa). */
    private Map<Long, Integer> cantidades;
}
