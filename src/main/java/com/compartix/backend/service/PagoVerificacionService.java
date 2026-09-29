package com.compartix.backend.service;

import com.compartix.backend.dto.response.CuotaResponse;
import com.compartix.backend.dto.response.MultaResponse;

/**
 * Punto único donde se "confirma" que una cuota o una multa quedó pagada — ya sea
 * porque la directiva la marcó directamente (sin comprobante, ej. efectivo en persona)
 * o porque se aprobó una SolicitudPago con comprobante subido por el miembro. Ambos
 * caminos terminan aquí para no duplicar las reglas de negocio (estado, movimiento,
 * kardex, saldo del grupo, notificaciones).
 */
public interface PagoVerificacionService {

    /** comprobanteUrl puede ser null (marcado directo por la directiva). */
    CuotaResponse confirmarPagoCuota(Long grupoId, Long cuotaId, Long ejecutorId, String comprobanteUrl);

    MultaResponse confirmarPagoMulta(Long grupoId, Long multaId, Long ejecutorId);
}
