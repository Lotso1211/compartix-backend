package com.compartix.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String fotoUrl;
    private String rol;
    // Estado en el grupo (solo se rellena en listados de miembros)
    private Boolean activo;
    private Boolean participaCarnaval;
}