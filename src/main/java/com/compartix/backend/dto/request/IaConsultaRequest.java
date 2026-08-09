package com.compartix.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IaConsultaRequest {
    @NotBlank(message = "La pregunta no puede estar vacía")
    private String pregunta;
}