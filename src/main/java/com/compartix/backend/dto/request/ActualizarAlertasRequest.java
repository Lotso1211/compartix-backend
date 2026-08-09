package com.compartix.backend.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ActualizarAlertasRequest {

    // null o <= 0 desactiva la alerta para ese fondo.
    private BigDecimal alertaSaldoCarnaval;
    private BigDecimal alertaSaldoAhorro;
}
