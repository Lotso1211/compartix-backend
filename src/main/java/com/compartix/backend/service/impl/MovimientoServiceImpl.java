package com.compartix.backend.service.impl;

import com.compartix.backend.dto.request.*;
import com.compartix.backend.dto.response.MovimientoDetalleResponse;
import com.compartix.backend.dto.response.MovimientoResponse;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.entity.*;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.enums.TipoFondo;
import com.compartix.backend.enums.TipoMovimiento;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.*;
import com.compartix.backend.service.MovimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovimientoServiceImpl implements MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final MovimientoDetalleRepository movimientoDetalleRepository;
    private final GrupoRepository grupoRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final UsuarioRepository usuarioRepository;
    private final KardexRepository kardexRepository;
    private final KardexMensualRepository kardexMensualRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final CategoriaMovimientoRepository categoriaMovimientoRepository;
    private final MultaRepository multaRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final com.compartix.backend.service.NotificacionService notificacionService;

    // ============================================================
    // REGISTRAR APORTE
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarAporte(Long grupoId, RegistrarAporteRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);
        validarFondoSimple(request.getFondo());
        BigDecimal[] montosFondo = calcularMontosPorFondo(request.getFondo(), request.getMonto(), null, null);

        Grupo grupo = obtenerGrupo(grupoId);
        Usuario usuario = obtenerUsuario(request.getUsuarioId());
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.APORTE)
                .descripcion(request.getDescripcion())
                .montoTotal(request.getMonto())
                .fecha(request.getFecha())
                .comprobanteUrl(request.getComprobanteUrl())
                .origenIa(false)
                .fondo(request.getFondo())
                .montoCarnaval(montosFondo[0])
                .montoAhorro(montosFondo[1])
                .build();

        if (request.getCategoriaId() != null) {
            categoriaMovimientoRepository.findById(request.getCategoriaId())
                    .ifPresent(movimiento::setCategoria);
        }

        movimientoRepository.save(movimiento);

        // Detalle del aporte
        MovimientoDetalle detalle = MovimientoDetalle.builder()
                .movimiento(movimiento)
                .usuario(usuario)
                .cantidad(BigDecimal.ONE)
                .monto(request.getMonto())
                .afectaKardex(true)
                .build();

        movimientoDetalleRepository.save(detalle);

        // Actualizar kardex del miembro
        actualizarKardexAporte(grupo, usuario, request.getMonto(), request.getFecha());

        // Actualizar saldo del grupo (y el fondo correspondiente)
        actualizarSaldoGrupoIngreso(grupoId, montosFondo[0], montosFondo[1]);

        notificacionService.notificarAporte(usuario, grupo, request.getMonto(), request.getDescripcion());

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // REGISTRAR GASTO COMPARTIDO
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarGastoCompartido(Long grupoId, RegistrarGastoCompartidoRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);
        BigDecimal[] montosFondo = calcularMontosPorFondo(request.getFondo(), request.getMontoTotal(),
                request.getMontoCarnaval(), request.getMontoAhorro());

        Grupo grupo = obtenerGrupo(grupoId);
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        int cantidadMiembros = request.getUsuarioIds().size();
        BigDecimal montoPorMiembro = request.getMontoTotal()
                .divide(BigDecimal.valueOf(cantidadMiembros), 2, BigDecimal.ROUND_HALF_UP);

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.GASTO_COMPARTIDO)
                .descripcion(request.getDescripcion())
                .montoTotal(request.getMontoTotal())
                .fecha(request.getFecha())
                .origenIa(false)
                .fondo(request.getFondo())
                .montoCarnaval(montosFondo[0])
                .montoAhorro(montosFondo[1])
                .build();

        if (request.getCategoriaId() != null) {
            categoriaMovimientoRepository.findById(request.getCategoriaId())
                    .ifPresent(movimiento::setCategoria);
        }

        movimientoRepository.save(movimiento);

        // Crear detalle y actualizar kardex por cada miembro
        for (Long usuarioId : request.getUsuarioIds()) {
            Usuario usuario = obtenerUsuario(usuarioId);

            MovimientoDetalle detalle = MovimientoDetalle.builder()
                    .movimiento(movimiento)
                    .usuario(usuario)
                    .cantidad(BigDecimal.ONE)
                    .monto(montoPorMiembro)
                    .afectaKardex(true)
                    .build();

            movimientoDetalleRepository.save(detalle);
            actualizarKardexGastoCompartido(grupo, usuario, montoPorMiembro, request.getFecha());
            notificacionService.notificarGasto(usuario, grupo, request.getDescripcion(), montoPorMiembro);
        }

        // Actualizar saldo del grupo (y descontar del fondo correspondiente)
        actualizarSaldoGrupoEgreso(grupoId, montosFondo[0], montosFondo[1]);

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // REGISTRAR GASTO INDIVIDUAL
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarGastoIndividual(Long grupoId, RegistrarGastoIndividualRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        Grupo grupo = obtenerGrupo(grupoId);
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        // Calcular monto total sumando (cantidad * precioUnitario) por cada miembro
        BigDecimal montoTotal = request.getCantidadesPorUsuario().values().stream()
                .map(cantidad -> cantidad.multiply(request.getPrecioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal[] montosFondo = calcularMontosPorFondo(request.getFondo(), montoTotal,
                request.getMontoCarnaval(), request.getMontoAhorro());

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.GASTO_INDIVIDUAL)
                .descripcion(request.getDescripcion())
                .montoTotal(montoTotal)
                .fecha(request.getFecha())
                .origenIa(false)
                .fondo(request.getFondo())
                .montoCarnaval(montosFondo[0])
                .montoAhorro(montosFondo[1])
                .build();

        if (request.getCategoriaId() != null) {
            categoriaMovimientoRepository.findById(request.getCategoriaId())
                    .ifPresent(movimiento::setCategoria);
        }

        movimientoRepository.save(movimiento);

        // Crear detalle por cada miembro con su cantidad asignada
        for (Map.Entry<Long, BigDecimal> entry : request.getCantidadesPorUsuario().entrySet()) {
            Usuario usuario = obtenerUsuario(entry.getKey());
            BigDecimal cantidad = entry.getValue();
            BigDecimal montoUsuario = cantidad.multiply(request.getPrecioUnitario());

            MovimientoDetalle detalle = MovimientoDetalle.builder()
                    .movimiento(movimiento)
                    .usuario(usuario)
                    .cantidad(cantidad)
                    .monto(montoUsuario)
                    .afectaKardex(true)
                    .build();

            movimientoDetalleRepository.save(detalle);
            actualizarKardexGastoIndividual(grupo, usuario, montoUsuario, request.getFecha());
            notificacionService.notificarGasto(usuario, grupo, request.getDescripcion(), montoUsuario);
        }

        actualizarSaldoGrupoEgreso(grupoId, montosFondo[0], montosFondo[1]);

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // REGISTRAR MULTA
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarMulta(Long grupoId, RegistrarMultaRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        Grupo grupo = obtenerGrupo(grupoId);
        Usuario usuario = obtenerUsuario(request.getUsuarioId());
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.MULTA)
                .descripcion(request.getMotivo())
                .montoTotal(request.getMonto())
                .fecha(request.getFecha())
                .origenIa(false)
                .fondo(TipoFondo.AHORRO)
                .montoAhorro(request.getMonto())
                .build();

        movimientoRepository.save(movimiento);

        // afectaKardex = FALSE porque las multas van al fondo general
        MovimientoDetalle detalle = MovimientoDetalle.builder()
                .movimiento(movimiento)
                .usuario(usuario)
                .cantidad(BigDecimal.ONE)
                .monto(request.getMonto())
                .afectaKardex(false)
                .build();

        movimientoDetalleRepository.save(detalle);

        Multa multa = Multa.builder()
                .grupo(grupo)
                .usuario(usuario)
                .movimiento(movimiento)
                .motivo(request.getMotivo())
                .monto(request.getMonto())
                .estado("PENDIENTE")
                .fecha(request.getFecha())
                .periodoMes(request.getPeriodoMes())
                .periodoAnio(request.getPeriodoAnio())
                .build();

        multaRepository.save(multa);

        // La multa solo queda como pendiente en el kardex personal (indicador de lo que
        // debe). El fondo del grupo se acredita recién cuando el miembro la paga
        // (MultaController.marcarComoPagada), no al registrarla.
        actualizarKardexMulta(grupoId, request.getUsuarioId(), request.getMonto());

        // Notificar al miembro afectado
        notificacionService.notificarMulta(usuario, grupo, request.getMonto(), request.getMotivo());

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // REGISTRAR PAGO DE CUOTA (reparte entre Carnaval y Ahorro)
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarPagoCuota(Long grupoId, Long usuarioId, BigDecimal montoCarnaval,
                                                 BigDecimal montoAhorro, String descripcion,
                                                 Long solicitanteId, java.time.LocalDate fecha) {
        Grupo grupo = obtenerGrupo(grupoId);
        Usuario usuario = obtenerUsuario(usuarioId);
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        if (montoCarnaval == null) montoCarnaval = BigDecimal.ZERO;
        if (montoAhorro == null)   montoAhorro   = BigDecimal.ZERO;
        BigDecimal total = montoCarnaval.add(montoAhorro);

        TipoFondo fondo;
        if (montoCarnaval.signum() > 0 && montoAhorro.signum() > 0) fondo = TipoFondo.MIXTO;
        else if (montoAhorro.signum() > 0) fondo = TipoFondo.AHORRO;
        else fondo = TipoFondo.CARNAVAL;

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.APORTE)
                .descripcion(descripcion)
                .montoTotal(total)
                .fecha(fecha)
                .origenIa(false)
                .fondo(fondo)
                .montoCarnaval(montoCarnaval)
                .montoAhorro(montoAhorro)
                .build();
        movimientoRepository.save(movimiento);

        MovimientoDetalle detalle = MovimientoDetalle.builder()
                .movimiento(movimiento)
                .usuario(usuario)
                .cantidad(BigDecimal.ONE)
                .monto(total)
                .afectaKardex(true)
                .build();
        movimientoDetalleRepository.save(detalle);

        // Kardex del miembro: totalAportes = TOTAL aportado (carnaval + ahorro, lo que ve el miembro);
        // totalAhorro guarda solo la parte de ahorro (visibilidad directiva + cálculo de ponerse al día).
        Kardex kardex = obtenerOCrearKardex(grupo, usuario);
        kardex.setTotalAportes(kardex.getTotalAportes().add(total));
        kardex.setTotalAhorro(kardex.getTotalAhorro().add(montoAhorro));
        kardex.setSaldoActual(kardex.getSaldoActual().add(total));
        kardexRepository.save(kardex);

        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        mensual.setTotalAportesMes(mensual.getTotalAportesMes().add(total));
        mensual.setSaldoMes(mensual.getSaldoMes().add(total));
        kardexMensualRepository.save(mensual);

        // Saldo del grupo: total al disponible + desglose por fondo
        actualizarSaldoGrupoIngreso(grupoId, montoCarnaval, montoAhorro);

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // REGISTRAR MULTA DE CUOTA (al pagar una cuota vencida)
    // ============================================================
    @Override
    @Transactional
    public MovimientoResponse registrarMultaCuota(Long grupoId, Long usuarioId, BigDecimal monto,
                                                  String motivo, Long solicitanteId, java.time.LocalDate fecha) {
        Grupo grupo = obtenerGrupo(grupoId);
        Usuario usuario = obtenerUsuario(usuarioId);
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.MULTA)
                .descripcion(motivo)
                .montoTotal(monto)
                .fecha(fecha)
                .origenIa(false)
                .build();
        movimientoRepository.save(movimiento);

        // afectaKardex = FALSE: la multa va al fondo general, no suma al aporte del miembro
        MovimientoDetalle detalle = MovimientoDetalle.builder()
                .movimiento(movimiento)
                .usuario(usuario)
                .cantidad(BigDecimal.ONE)
                .monto(monto)
                .afectaKardex(false)
                .build();
        movimientoDetalleRepository.save(detalle);

        // La multa entra al fondo del grupo (no toca el kardex personal)
        actualizarSaldoGrupoMulta(grupoId, monto);

        return toMovimientoResponse(movimiento, registradoPor);
    }

    // ============================================================
    // ELIMINAR MOVIMIENTO (revierte kardex, saldo y cuota vinculada)
    // ============================================================
    @Override
    @Transactional
    public void eliminarMovimiento(Long grupoId, Long movimientoId, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        Movimiento movimiento = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado"));
        if (!movimiento.getGrupo().getId().equals(grupoId)) {
            throw new ResourceNotFoundException("Movimiento no encontrado");
        }

        Grupo grupo = movimiento.getGrupo();
        BigDecimal montoTotal = movimiento.getMontoTotal();
        List<MovimientoDetalle> detalles = movimientoDetalleRepository.findByMovimientoId(movimientoId);
        SaldoGrupo saldo = saldoGrupoRepository.findByGrupoId(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo del grupo no encontrado"));

        switch (movimiento.getTipo()) {
            case APORTE -> {
                // Si el aporte viene de un pago de cuota programada, revertimos también el
                // desglose Carnaval/Ahorro y devolvemos la cuota a su estado anterior.
                CuotaProgramada cuota = cuotaProgramadaRepository.findByMovimientoId(movimientoId).orElse(null);
                for (MovimientoDetalle d : detalles) {
                    Kardex kardex = obtenerOCrearKardex(grupo, d.getUsuario());
                    kardex.setTotalAportes(kardex.getTotalAportes().subtract(d.getMonto()));
                    kardex.setSaldoActual(kardex.getSaldoActual().subtract(d.getMonto()));
                    if (cuota != null) {
                        kardex.setTotalAhorro(kardex.getTotalAhorro().subtract(cuota.getMontoAhorro()));
                    }
                    kardexRepository.save(kardex);
                    revertirKardexMensualAporte(grupo, d.getUsuario(), movimiento.getFecha(), d.getMonto());
                }
                saldo.setTotalIngresos(saldo.getTotalIngresos().subtract(montoTotal));
                saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(montoTotal));
                if (cuota != null) {
                    // Cuota programada: el desglose vive en la cuota (compatible con aportes
                    // antiguos que nunca guardaron el fondo en el propio movimiento).
                    saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().subtract(cuota.getMontoCarnaval()));
                    saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(cuota.getMontoAhorro()));
                } else {
                    // Aporte suelto: el desglose vive en el propio movimiento (0/0 en aportes
                    // registrados antes de esta funcionalidad, no se tocan retroactivamente).
                    saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().subtract(movimiento.getMontoCarnaval()));
                    saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(movimiento.getMontoAhorro()));
                }
                saldoGrupoRepository.save(saldo);

                if (cuota != null) {
                    boolean planActivo = Boolean.TRUE.equals(cuota.getPagoProgramado().getActivo());
                    if (planActivo) {
                        // El plan sigue vigente: la cuota vuelve a quedar disponible para pagarse.
                        java.time.LocalDate hoy = java.time.LocalDate.now();
                        boolean vencida = (cuota.getAnio() * 12 + cuota.getMes()) < (hoy.getYear() * 12 + hoy.getMonthValue());
                        cuota.setEstado(vencida ? "VENCIDA" : "PENDIENTE");
                        cuota.setFechaPago(null);
                        cuota.setMovimiento(null);
                        cuotaProgramadaRepository.save(cuota);
                    } else {
                        // El plan ya está cerrado: no tiene caso dejarla "pendiente" (no se puede
                        // volver a pagar y quedaría invisible), así que se elimina directamente —
                        // igual que al cerrar un pago programado se eliminan las cuotas no pagadas.
                        cuotaProgramadaRepository.delete(cuota);
                    }
                }
            }
            case GASTO_COMPARTIDO, GASTO_INDIVIDUAL -> {
                boolean esCompartido = movimiento.getTipo() == TipoMovimiento.GASTO_COMPARTIDO;
                for (MovimientoDetalle d : detalles) {
                    Kardex kardex = obtenerOCrearKardex(grupo, d.getUsuario());
                    if (esCompartido) {
                        kardex.setTotalGastosCompartidos(kardex.getTotalGastosCompartidos().subtract(d.getMonto()));
                    } else {
                        kardex.setTotalGastosIndividuales(kardex.getTotalGastosIndividuales().subtract(d.getMonto()));
                    }
                    kardex.setSaldoActual(kardex.getSaldoActual().add(d.getMonto()));
                    kardexRepository.save(kardex);
                    revertirKardexMensualGasto(grupo, d.getUsuario(), movimiento.getFecha(), esCompartido, d.getMonto());
                }
                saldo.setTotalEgresos(saldo.getTotalEgresos().subtract(montoTotal));
                saldo.setSaldoDisponible(saldo.getSaldoDisponible().add(montoTotal));
                saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().add(movimiento.getMontoCarnaval()));
                saldo.setSaldoAhorro(saldo.getSaldoAhorro().add(movimiento.getMontoAhorro()));
                saldoGrupoRepository.save(saldo);
            }
            case MULTA -> {
                // El fondo (saldoAhorro) solo se acredita cuando la multa se PAGA, no al
                // registrarla. Por eso la reversión depende de en qué momento estamos:
                Multa multaManual = multaRepository.findByMovimientoId(movimientoId).orElse(null);
                if (multaManual != null) {
                    if ("PAGADA".equals(multaManual.getEstado())) {
                        // Ya se acreditó al fondo al pagarla: se revierte el fondo.
                        saldo.setTotalMultas(saldo.getTotalMultas().subtract(montoTotal));
                        saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(montoTotal));
                        saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(montoTotal));
                        saldoGrupoRepository.save(saldo);
                    } else {
                        // Aún pendiente: el fondo nunca se tocó, solo baja el indicador del kardex.
                        for (MovimientoDetalle d : detalles) {
                            Kardex kardex = obtenerOCrearKardex(grupo, d.getUsuario());
                            kardex.setTotalMultas(kardex.getTotalMultas().subtract(d.getMonto()).max(BigDecimal.ZERO));
                            kardexRepository.save(kardex);
                        }
                    }
                    multaRepository.delete(multaManual);
                } else {
                    // Multa de mora de una cuota programada: este movimiento solo se crea al
                    // pagar la cuota, momento en el que ya se acreditó al fondo; el indicador
                    // del kardex ya se puso en 0 en ese momento (PagoProgramadoController) y
                    // no se toca aquí.
                    saldo.setTotalMultas(saldo.getTotalMultas().subtract(montoTotal));
                    saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(montoTotal));
                    saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(montoTotal));
                    saldoGrupoRepository.save(saldo);
                }
            }
            case INGRESO_DIRECTO -> {
                saldo.setTotalIngresos(saldo.getTotalIngresos().subtract(montoTotal));
                saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(montoTotal));
                saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().subtract(movimiento.getMontoCarnaval()));
                saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(movimiento.getMontoAhorro()));
                saldoGrupoRepository.save(saldo);
            }
        }

        movimientoDetalleRepository.deleteAll(detalles);
        movimientoRepository.delete(movimiento);
    }

    private void revertirKardexMensualAporte(Grupo grupo, Usuario usuario, java.time.LocalDate fecha, BigDecimal monto) {
        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        mensual.setTotalAportesMes(mensual.getTotalAportesMes().subtract(monto));
        mensual.setSaldoMes(mensual.getSaldoMes().subtract(monto));
        kardexMensualRepository.save(mensual);
    }

    private void revertirKardexMensualGasto(Grupo grupo, Usuario usuario, java.time.LocalDate fecha, boolean compartido, BigDecimal monto) {
        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        if (compartido) {
            mensual.setTotalGastosCompartidosMes(mensual.getTotalGastosCompartidosMes().subtract(monto));
        } else {
            mensual.setTotalGastosIndividualesMes(mensual.getTotalGastosIndividualesMes().subtract(monto));
        }
        mensual.setSaldoMes(mensual.getSaldoMes().add(monto));
        kardexMensualRepository.save(mensual);
    }

    // ============================================================
    // OBTENER MOVIMIENTOS DEL GRUPO
    // ============================================================
    @Override
    public List<MovimientoResponse> obtenerMovimientosGrupo(Long grupoId, Long solicitanteId) {
        validarMiembro(grupoId, solicitanteId);

        return movimientoRepository.findByGrupoIdOrderByFechaDesc(grupoId)
                .stream()
                .map(m -> toMovimientoResponse(m, m.getRegistradoPor()))
                .collect(Collectors.toList());
    }

    // ============================================================
    // MÉTODOS PRIVADOS - Kardex
    // ============================================================

    private void actualizarKardexAporte(Grupo grupo, Usuario usuario, BigDecimal monto, java.time.LocalDate fecha) {
        Kardex kardex = obtenerOCrearKardex(grupo, usuario);
        kardex.setTotalAportes(kardex.getTotalAportes().add(monto));
        kardex.setSaldoActual(kardex.getSaldoActual().add(monto));
        kardexRepository.save(kardex);

        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        mensual.setTotalAportesMes(mensual.getTotalAportesMes().add(monto));
        mensual.setSaldoMes(mensual.getSaldoMes().add(monto));
        kardexMensualRepository.save(mensual);
    }

    private void actualizarKardexGastoCompartido(Grupo grupo, Usuario usuario, BigDecimal monto, java.time.LocalDate fecha) {
        Kardex kardex = obtenerOCrearKardex(grupo, usuario);
        kardex.setTotalGastosCompartidos(kardex.getTotalGastosCompartidos().add(monto));
        kardex.setSaldoActual(kardex.getSaldoActual().subtract(monto));
        kardexRepository.save(kardex);

        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        mensual.setTotalGastosCompartidosMes(mensual.getTotalGastosCompartidosMes().add(monto));
        mensual.setSaldoMes(mensual.getSaldoMes().subtract(monto));
        kardexMensualRepository.save(mensual);
    }

    private void actualizarKardexGastoIndividual(Grupo grupo, Usuario usuario, BigDecimal monto, java.time.LocalDate fecha) {
        Kardex kardex = obtenerOCrearKardex(grupo, usuario);
        kardex.setTotalGastosIndividuales(kardex.getTotalGastosIndividuales().add(monto));
        kardex.setSaldoActual(kardex.getSaldoActual().subtract(monto));
        kardexRepository.save(kardex);

        KardexMensual mensual = obtenerOCrearKardexMensual(grupo, usuario, fecha);
        mensual.setTotalGastosIndividualesMes(mensual.getTotalGastosIndividualesMes().add(monto));
        mensual.setSaldoMes(mensual.getSaldoMes().subtract(monto));
        kardexMensualRepository.save(mensual);
    }

    // ============================================================
    // MÉTODOS PRIVADOS - Saldo Grupo
    // ============================================================

    private void actualizarSaldoGrupoIngreso(Long grupoId, BigDecimal montoCarnaval, BigDecimal montoAhorro) {
        SaldoGrupo saldo = saldoGrupoRepository.findByGrupoId(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo del grupo no encontrado"));
        BigDecimal total = montoCarnaval.add(montoAhorro);
        saldo.setTotalIngresos(saldo.getTotalIngresos().add(total));
        saldo.setSaldoDisponible(saldo.getSaldoDisponible().add(total));
        saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().add(montoCarnaval));
        saldo.setSaldoAhorro(saldo.getSaldoAhorro().add(montoAhorro));
        saldoGrupoRepository.save(saldo);
    }

    private void actualizarSaldoGrupoEgreso(Long grupoId, BigDecimal montoCarnaval, BigDecimal montoAhorro) {
        SaldoGrupo saldo = saldoGrupoRepository.findByGrupoId(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo del grupo no encontrado"));
        if (saldo.getSaldoCarnaval().compareTo(montoCarnaval) < 0) {
            throw new BadRequestException("El Fondo Carnaval no tiene saldo suficiente (disponible: "
                    + saldo.getSaldoCarnaval() + ", solicitado: " + montoCarnaval + ")");
        }
        if (saldo.getSaldoAhorro().compareTo(montoAhorro) < 0) {
            throw new BadRequestException("El Fondo Ahorro no tiene saldo suficiente (disponible: "
                    + saldo.getSaldoAhorro() + ", solicitado: " + montoAhorro + ")");
        }
        BigDecimal carnavalAntes = saldo.getSaldoCarnaval();
        BigDecimal ahorroAntes = saldo.getSaldoAhorro();

        BigDecimal total = montoCarnaval.add(montoAhorro);
        saldo.setTotalEgresos(saldo.getTotalEgresos().add(total));
        saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(total));
        saldo.setSaldoCarnaval(saldo.getSaldoCarnaval().subtract(montoCarnaval));
        saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(montoAhorro));
        saldoGrupoRepository.save(saldo);

        Grupo grupo = saldo.getGrupo();
        avisarSiCruzaFondoBajo(grupo, "Carnaval", carnavalAntes, saldo.getSaldoCarnaval(), grupo.getAlertaSaldoCarnaval());
        avisarSiCruzaFondoBajo(grupo, "Ahorro", ahorroAntes, saldo.getSaldoAhorro(), grupo.getAlertaSaldoAhorro());
    }

    // Solo avisa la primera vez que el saldo cruza por DEBAJO del umbral (no en cada
    // gasto subsiguiente que lo mantiene bajo).
    private void avisarSiCruzaFondoBajo(Grupo grupo, String nombreFondo, BigDecimal antes,
                                         BigDecimal despues, BigDecimal umbral) {
        if (umbral == null || umbral.compareTo(BigDecimal.ZERO) <= 0) return;
        if (antes.compareTo(umbral) >= 0 && despues.compareTo(umbral) < 0) {
            notificacionService.notificarFondoBajo(grupo, nombreFondo, despues, umbral);
        }
    }

    // Determina cuánto de un monto va a Carnaval y cuánto a Ahorro según el fondo elegido.
    // Para MIXTO exige que los montos indicados sumen exactamente el total.
    private BigDecimal[] calcularMontosPorFondo(TipoFondo fondo, BigDecimal montoTotal,
                                                 BigDecimal montoCarnavalInput, BigDecimal montoAhorroInput) {
        if (fondo == TipoFondo.MIXTO) {
            BigDecimal mc = montoCarnavalInput != null ? montoCarnavalInput : BigDecimal.ZERO;
            BigDecimal ma = montoAhorroInput != null ? montoAhorroInput : BigDecimal.ZERO;
            BigDecimal suma = mc.add(ma).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalRedondeado = montoTotal.setScale(2, RoundingMode.HALF_UP);
            if (suma.compareTo(totalRedondeado) != 0) {
                throw new BadRequestException("La suma de Carnaval (" + mc + ") y Ahorro (" + ma
                        + ") debe ser igual al monto total (" + montoTotal + ")");
            }
            return new BigDecimal[]{mc, ma};
        }
        if (fondo == TipoFondo.CARNAVAL) return new BigDecimal[]{montoTotal, BigDecimal.ZERO};
        return new BigDecimal[]{BigDecimal.ZERO, montoTotal}; // AHORRO
    }

    // Aportes e ingresos directos van a un solo fondo, nunca mixto.
    private void validarFondoSimple(TipoFondo fondo) {
        if (fondo == TipoFondo.MIXTO) {
            throw new BadRequestException("Este movimiento no admite fondo mixto: elige Carnaval o Ahorro");
        }
    }

    private void actualizarSaldoGrupoMulta(Long grupoId, BigDecimal monto) {
        SaldoGrupo saldo = saldoGrupoRepository.findByGrupoId(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo del grupo no encontrado"));
        saldo.setTotalMultas(saldo.getTotalMultas().add(monto));
        saldo.setSaldoDisponible(saldo.getSaldoDisponible().add(monto));
        // Las multas engrosan el fondo de ahorro/comparsa
        saldo.setSaldoAhorro(saldo.getSaldoAhorro().add(monto));
        saldoGrupoRepository.save(saldo);
    }

    // ============================================================
    // MÉTODOS PRIVADOS - Helpers
    // ============================================================

    private Kardex obtenerOCrearKardex(Grupo grupo, Usuario usuario) {
        return kardexRepository.findByGrupoIdAndUsuarioId(grupo.getId(), usuario.getId())
                .orElseGet(() -> {
                    Kardex nuevo = Kardex.builder()
                            .grupo(grupo)
                            .usuario(usuario)
                            .totalAportes(BigDecimal.ZERO)
                            .totalGastosCompartidos(BigDecimal.ZERO)
                            .totalGastosIndividuales(BigDecimal.ZERO)
                            .saldoActual(BigDecimal.ZERO)
                            .build();
                    return kardexRepository.save(nuevo);
                });
    }

    private KardexMensual obtenerOCrearKardexMensual(Grupo grupo, Usuario usuario, java.time.LocalDate fecha) {
        short anio = (short) fecha.getYear();
        short mes = (short) fecha.getMonthValue();

        return kardexMensualRepository
                .findByGrupoIdAndUsuarioIdAndAnioAndMes(grupo.getId(), usuario.getId(), anio, mes)
                .orElseGet(() -> {
                    KardexMensual nuevo = KardexMensual.builder()
                            .grupo(grupo)
                            .usuario(usuario)
                            .anio(anio)
                            .mes(mes)
                            .totalAportesMes(BigDecimal.ZERO)
                            .totalGastosCompartidosMes(BigDecimal.ZERO)
                            .totalGastosIndividualesMes(BigDecimal.ZERO)
                            .saldoMes(BigDecimal.ZERO)
                            .build();
                    return kardexMensualRepository.save(nuevo);
                });
    }

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        GrupoMiembro miembro = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new UnauthorizedException("No eres miembro de este grupo"));

        if (miembro.getRol() != RolGrupo.DIRECTIVA) {
            throw new UnauthorizedException("Solo la directiva puede realizar esta acción");
        }
    }

    private void validarMiembro(Long grupoId, Long usuarioId) {
        if (!grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new UnauthorizedException("No eres miembro de este grupo");
        }
    }

    private Grupo obtenerGrupo(Long grupoId) {
        return grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));
    }

    private Usuario obtenerUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private MovimientoResponse toMovimientoResponse(Movimiento movimiento, Usuario registradoPor) {
        String usuarioAfectado = movimientoDetalleRepository
                .findByMovimientoId(movimiento.getId())
                .stream()
                .map(d -> d.getUsuario().getNombre() + " " + d.getUsuario().getApellido())
                .collect(Collectors.joining(", "));

        return MovimientoResponse.builder()
                .id(movimiento.getId())
                .tipo(movimiento.getTipo())
                .descripcion(movimiento.getDescripcion())
                .montoTotal(movimiento.getMontoTotal())
                .fecha(movimiento.getFecha())
                .comprobanteUrl(movimiento.getComprobanteUrl())
                .origenIa(movimiento.getOrigenIa())
                .fondo(movimiento.getFondo() != null ? movimiento.getFondo().name() : null)
                .usuarioAfectado(usuarioAfectado)
                .registradoPor(UsuarioResponse.builder()
                        .id(registradoPor.getId())
                        .nombre(registradoPor.getNombre())
                        .apellido(registradoPor.getApellido())
                        .email(registradoPor.getEmail())
                        .build())
                .creadoEn(movimiento.getCreadoEn())
                .build();
    }
    @Override
    public List<MovimientoResponse> obtenerMovimientosUsuario(Long grupoId, Long usuarioId) {
        return movimientoDetalleRepository
                .findByMovimientoGrupoIdAndUsuarioId(grupoId, usuarioId)
                .stream()
                .map(d -> {
                    MovimientoResponse response = toMovimientoResponse(d.getMovimiento(), d.getMovimiento().getRegistradoPor());
                    // El miembro solo ve su monto, no el total
                    response.setMontoTotal(d.getMonto());
                    // No ve quiénes más participaron
                    response.setUsuarioAfectado(null);
                    return response;
                })
                .collect(Collectors.toList());
    }

    private void actualizarKardexMulta(Long grupoId, Long usuarioId, BigDecimal monto) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Kardex kardex = obtenerOCrearKardex(grupo, usuario);
        kardex.setTotalMultas(kardex.getTotalMultas().add(monto));
        kardexRepository.save(kardex);
    }
    @Override
    public List<MovimientoDetalleResponse> obtenerDetalleMovimiento(Long grupoId, Long movimientoId, Long usuarioId) {
        validarMiembro(grupoId, usuarioId);
        return movimientoDetalleRepository.findByMovimientoId(movimientoId)
                .stream()
                .map(d -> MovimientoDetalleResponse.builder()
                        .usuarioId(d.getUsuario().getId())
                        .nombreUsuario(d.getUsuario().getNombre() + " " + d.getUsuario().getApellido())
                        .cantidad(d.getCantidad())
                        .monto(d.getMonto())
                        .afectaKardex(d.getAfectaKardex())
                        .build())
                .collect(Collectors.toList());
    }
    @Override
    @Transactional
    public MovimientoResponse registrarIngresoDirecto(Long grupoId, RegistrarIngresoDirectoRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);
        validarFondoSimple(request.getFondo());
        BigDecimal[] montosFondo = calcularMontosPorFondo(request.getFondo(), request.getMonto(), null, null);

        Grupo grupo = obtenerGrupo(grupoId);
        Usuario registradoPor = obtenerUsuario(solicitanteId);

        Movimiento movimiento = Movimiento.builder()
                .grupo(grupo)
                .registradoPor(registradoPor)
                .tipo(TipoMovimiento.INGRESO_DIRECTO)
                .descripcion(request.getDescripcion())
                .montoTotal(request.getMonto())
                .fecha(request.getFecha())
                .origenIa(false)
                .fondo(request.getFondo())
                .montoCarnaval(montosFondo[0])
                .montoAhorro(montosFondo[1])
                .build();

        movimientoRepository.save(movimiento);
        actualizarSaldoGrupoIngreso(grupoId, montosFondo[0], montosFondo[1]);

        return toMovimientoResponse(movimiento, registradoPor);
    }

    @Override
    public List<MovimientoDetalleResponse> obtenerMiDetalleMovimiento(Long grupoId, Long movimientoId, Long usuarioId) {
        validarMiembro(grupoId, usuarioId);
        return movimientoDetalleRepository.findByMovimientoId(movimientoId)
                .stream()
                .filter(d -> d.getUsuario().getId().equals(usuarioId))
                .map(d -> MovimientoDetalleResponse.builder()
                        .usuarioId(d.getUsuario().getId())
                        .nombreUsuario(d.getUsuario().getNombre() + " " + d.getUsuario().getApellido())
                        .cantidad(d.getCantidad())
                        .monto(d.getMonto())
                        .afectaKardex(d.getAfectaKardex())
                        .build())
                .collect(Collectors.toList());
    }

}