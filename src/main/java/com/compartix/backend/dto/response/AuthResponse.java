package com.compartix.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private UsuarioResponse usuario;

    // true cuando las credenciales son correctas pero falta ingresar el código
    // de verificación enviado por correo; en ese caso accessToken/refreshToken/
    // usuario vienen nulos y el login todavía no está completo.
    private boolean requiere2fa;
}