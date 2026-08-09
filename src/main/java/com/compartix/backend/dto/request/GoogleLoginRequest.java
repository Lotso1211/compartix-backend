package com.compartix.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "El idToken de Google es obligatorio")
    private String idToken;
}
