package com.compartix.backend.service;

import com.compartix.backend.dto.request.*;
import com.compartix.backend.dto.response.MovimientoDetalleResponse;
import com.compartix.backend.dto.response.MovimientoResponse;

import java.util.List;

public interface MovimientoService {
    MovimientoResponse registrarAporte(Long grupoId, RegistrarAporteRequest request, Long solicitanteId);
    MovimientoResponse registrarGastoCompartido(Long grupoId, RegistrarGastoCompartidoRequest request, Long solicitanteId);
    MovimientoResponse registrarGastoIndividual(Long grupoId, RegistrarGastoIndividualRequest request, Long solicitanteId);
    MovimientoResponse registrarMulta(Long grupoId, RegistrarMultaRequest request, Long solicitanteId);
    List<MovimientoResponse> obtenerMovimientosGrupo(Long grupoId, Long solicitanteId);
    List<MovimientoResponse> obtenerMovimientosUsuario(Long grupoId, Long usuarioId);
    List<MovimientoDetalleResponse> obtenerDetalleMovimiento(Long grupoId, Long movimientoId, Long usuarioId);
    MovimientoResponse registrarIngresoDirecto(Long grupoId, RegistrarIngresoDirectoRequest request, Long solicitanteId);
    List<MovimientoDetalleResponse> obtenerMiDetalleMovimiento(Long grupoId, Long movimientoId, Long usuarioId);

    /**
     * Registra la multa de una cuota como un Movimiento en el extracto.
     * La multa va SOLO al fondo del grupo (totalMultas + saldoDisponible) y NO afecta
     * el kardex personal del miembro (afectaKardex=false). Uso interno desde el pago de cuotas.
     */
    MovimientoResponse registrarMultaCuota(Long grupoId, Long usuarioId, java.math.BigDecimal monto,
                                           String motivo, Long solicitanteId, java.time.LocalDate fecha);

    /**
     * Registra el pago de una cuota repartiendo el monto entre Carnaval y Ahorro.
     * Ambos suman al kardex del miembro (carnaval→totalAportes, ahorro→totalAhorro) y al
     * saldo del grupo, manteniendo el desglose por fondo (saldoCarnaval / saldoAhorro).
     */
    MovimientoResponse registrarPagoCuota(Long grupoId, Long usuarioId, java.math.BigDecimal montoCarnaval,
                                          java.math.BigDecimal montoAhorro, String descripcion,
                                          Long solicitanteId, java.time.LocalDate fecha);

    /**
     * Elimina un movimiento y revierte por completo su efecto: kardex del/los miembro(s)
     * afectado(s), saldo del grupo y, si el movimiento venía de un pago de cuota programada,
     * la cuota vuelve a su estado anterior (PENDIENTE/VENCIDA) y queda disponible para pagarse de nuevo.
     * Solo la directiva puede eliminar movimientos.
     */
    void eliminarMovimiento(Long grupoId, Long movimientoId, Long solicitanteId);
}