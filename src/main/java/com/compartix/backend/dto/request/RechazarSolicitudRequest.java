package com.compartix.backend.dto.request;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RechazarSolicitudRequest {
    private String motivo;
}
