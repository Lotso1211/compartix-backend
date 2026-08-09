package com.compartix.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CambiarPasswordRequest {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[^A-Za-z0-9\\s'\"])(?!.*['\"])(.{8,})$",
            message = "La contraseña debe tener mínimo 8 caracteres, al menos una mayúscula, un carácter especial y no puede contener comillas"
    )
    private String passwordNuevo;
}