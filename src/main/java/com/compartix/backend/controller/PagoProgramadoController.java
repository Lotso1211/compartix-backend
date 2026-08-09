package com.compartix.backend.controller;

import com.compartix.backend.dto.request.CrearPagoProgramadoRequest;
import com.compartix.backend.dto.request.RegistrarAporteRequest;
import com.compartix.backend.dto.response.CuotaResponse;
import com.compartix.backend.dto.response.PagoProgramadoResponse;
import com.compartix.backend.dto.response.ReactivacionInfoResponse;
import com.compartix.backend.entity.CuotaProgramada;
import com.compartix.backend.entity.Grupo;
import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.entity.Kardex;
import com.compartix.backend.entity.PagoProgramado;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.*;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.MovimientoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grupos/{grupoId}/pagos-programados")
@RequiredArgsConstructor
public class PagoProgramadoController {

    private final PagoProgramadoRepository pagoProgramadoRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final GrupoRepository grupoRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final UsuarioRepository usuarioRepository;
    private final KardexRepository kardexRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final MultaRepository multaRepository;
    private final MovimientoRepository movimientoRepository;
    private final MovimientoService movimientoService;
    private final com.compartix.backend.service.NotificacionService notificacionService;
    private final com.compartix.backend.service.VerificacionMultasService verificacionMultasService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<PagoProgramadoResponse> crearPagoProgramado(
            @PathVariable Long grupoId,
            @RequestBody CrearPagoProgramadoRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        if (request.getFechaInicio() == null || request.getFechaFin() == null) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "La fecha de inicio y fin son obligatorias");
        }
        if (request.getDiaLimitePago() == null) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "El día límite de pago es obligatorio");
        }

        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        // Desglose carnaval/ahorro. Si no se envía, todo va a carnaval.
        BigDecimal carnaval = request.getMontoCarnaval();
        BigDecimal ahorro   = request.getMontoAhorro();
        if (carnaval == null && ahorro == null) {
            carnaval = request.getMontoMensual();
            ahorro   = BigDecimal.ZERO;
        } else {
            carnaval = carnaval != null ? carnaval : BigDecimal.ZERO;
            ahorro   = ahorro   != null ? ahorro   : BigDecimal.ZERO;
        }
        BigDecimal mensual = carnaval.add(ahorro);

        PagoProgramado pago = PagoProgramado.builder()
                .grupo(grupo)
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .montoMensual(mensual)
                .montoCarnaval(carnaval)
                .montoAhorro(ahorro)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .diaLimitePago(request.getDiaLimitePago())
                .tipoMulta(request.getTipoMulta())
                .montoMulta(request.getMontoMulta())
                .porcentajeMulta(request.getPorcentajeMulta())
                .activo(true)
                .build();
        pagoProgramadoRepository.save(pago);

        generarCuotas(pago, grupo);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(pago));
    }

    @GetMapping
    public ResponseEntity<List<PagoProgramadoResponse>> obtenerPagosProgramados(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);

        List<PagoProgramadoResponse> pagos = pagoProgramadoRepository
                .findByGrupoId(grupoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(pagos);
    }

    @GetMapping("/cuotas")
    public ResponseEntity<List<CuotaResponse>> obtenerCuotasMes(
            @PathVariable Long grupoId,
            @RequestParam Integer anio,
            @RequestParam Integer mes,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);

        List<CuotaResponse> cuotas = cuotaProgramadaRepository
                .findByGrupoIdAndAnioAndMes(grupoId, anio, mes)
                .stream()
                .filter(this::cuotaVisible)
                .map(this::toCuotaResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(cuotas);
    }

    @GetMapping("/mis-cuotas")
    public ResponseEntity<List<CuotaResponse>> obtenerMisCuotas(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);

        List<CuotaResponse> cuotas = cuotaProgramadaRepository
                .findByUsuarioIdAndGrupoId(usuarioId, grupoId)
                .stream()
                .filter(this::cuotaVisible)
                .map(this::toCuotaResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(cuotas);
    }

    @PatchMapping("/cuotas/{cuotaId}/pagar")
    @Transactional
    public ResponseEntity<CuotaResponse> marcarCuotaPagada(
            @PathVariable Long grupoId,
            @PathVariable Long cuotaId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        CuotaProgramada cuota = cuotaProgramadaRepository.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada"));

        if ("PAGADA".equals(cuota.getEstado())) {
            return ResponseEntity.badRequest().build();
        }

        cuota.setEstado("PAGADA");
        cuota.setFechaPago(LocalDate.now());
        cuotaProgramadaRepository.save(cuota);

        // 1) La cuota se registra como APORTE repartido en Carnaval + Ahorro: genera un
        //    movimiento (extracto), suma al kardex del miembro (carnaval→aportes,
        //    ahorro→totalAhorro) y al saldo del grupo manteniendo el desglose por fondo.
        String descripcion = "Pago de cuota: " + cuota.getPagoProgramado().getNombre()
                + " (" + getNombreMes(cuota.getMes()) + " " + cuota.getAnio() + ")";
        var movPago = movimientoService.registrarPagoCuota(grupoId, cuota.getUsuario().getId(),
                cuota.getMontoCarnaval(), cuota.getMontoAhorro(), descripcion,
                usuarioId, LocalDate.now());

        // Vincula el movimiento generado con la cuota para poder revertir el pago si se elimina.
        movimientoRepository.findById(movPago.getId()).ifPresent(cuota::setMovimiento);
        cuotaProgramadaRepository.save(cuota);

        notificacionService.notificarCuotaPagada(cuota.getUsuario(), cuota.getGrupo(), cuota.getMonto(),
                getNombreMes(cuota.getMes()), cuota.getAnio());

        // 2) La multa va SOLO al fondo del grupo (intencional: no afecta el kardex personal).
        //    Se registra como un Movimiento (tipo MULTA, afectaKardex=false) para que
        //    aparezca en el extracto y los reportes, y suma al fondo del grupo.
        if (cuota.getMultaAplicada() && cuota.getMontoMulta() != null
                && cuota.getMontoMulta().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal montoMulta = cuota.getMontoMulta();

            movimientoService.registrarMultaCuota(grupoId, cuota.getUsuario().getId(), montoMulta,
                    "Multa por mora — cuota " + getNombreMes(cuota.getMes()) + " " + cuota.getAnio(),
                    usuarioId, LocalDate.now());

            // Limpiar el indicador de multa pendiente en el kardex (sin bajar de 0).
            kardexRepository.findByGrupoIdAndUsuarioId(grupoId, cuota.getUsuario().getId())
                    .ifPresent(kardex -> {
                        BigDecimal restante = kardex.getTotalMultas().subtract(montoMulta);
                        kardex.setTotalMultas(restante.max(BigDecimal.ZERO));
                        kardexRepository.save(kardex);
                    });
        }

        return ResponseEntity.ok(toCuotaResponse(cuota));
    }

    /** De un pago finalizado solo se muestran las cuotas pagadas. */
    private boolean cuotaVisible(CuotaProgramada c) {
        return Boolean.TRUE.equals(c.getPagoProgramado().getActivo())
                || "PAGADA".equals(c.getEstado());
    }

    private String getNombreMes(Integer mes) {
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        return (mes != null && mes >= 1 && mes <= 12) ? meses[mes - 1] : String.valueOf(mes);
    }

    // ============================================================
    //  GESTIÓN DE MIEMBROS INACTIVOS
    // ============================================================

    @PatchMapping("/miembros/{usuarioId}/inactivar")
    @Transactional
    public ResponseEntity<Void> inactivarMiembro(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestHeader("Authorization") String token) {
        validarDirectiva(grupoId, extraerUsuarioId(token));

        GrupoMiembro gm = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));
        gm.setActivo(false);
        grupoMiembroRepository.save(gm);

        // Congelar sus cuotas pendientes/vencidas (no generan multa ni cuentan como deuda)
        cuotaProgramadaRepository.findByUsuarioIdAndGrupoId(usuarioId, grupoId).stream()
                .filter(c -> "PENDIENTE".equals(c.getEstado()) || "VENCIDA".equals(c.getEstado()))
                .forEach(c -> { c.setEstado("CONGELADA"); cuotaProgramadaRepository.save(c); });

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/miembros/{usuarioId}/participacion-carnaval")
    @Transactional
    public ResponseEntity<Void> cambiarParticipacionCarnaval(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestParam boolean participa,
            @RequestHeader("Authorization") String token) {
        validarDirectiva(grupoId, extraerUsuarioId(token));

        GrupoMiembro gm = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));
        gm.setParticipaCarnaval(participa);
        grupoMiembroRepository.save(gm);

        // Ajustar las cuotas PENDIENTE del mes actual en adelante
        LocalDate hoy = LocalDate.now();
        cuotaProgramadaRepository.findByUsuarioIdAndGrupoId(usuarioId, grupoId).stream()
                .filter(c -> "PENDIENTE".equals(c.getEstado()))
                .filter(c -> (c.getAnio() * 12 + c.getMes()) >= (hoy.getYear() * 12 + hoy.getMonthValue()))
                .forEach(c -> {
                    BigDecimal carnaval = participa ? c.getPagoProgramado().getMontoCarnaval() : BigDecimal.ZERO;
                    BigDecimal ahorro = c.getPagoProgramado().getMontoAhorro();
                    c.setMontoCarnaval(carnaval);
                    c.setMontoAhorro(ahorro);
                    c.setMonto(carnaval.add(ahorro));
                    cuotaProgramadaRepository.save(c);
                });

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/miembros/{usuarioId}/reactivacion-info")
    public ResponseEntity<ReactivacionInfoResponse> infoReactivacion(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "true") boolean conCarnaval,
            @RequestHeader("Authorization") String token) {
        validarDirectiva(grupoId, extraerUsuarioId(token));
        return ResponseEntity.ok(construirInfoReactivacion(grupoId, usuarioId, conCarnaval));
    }

    @PatchMapping("/miembros/{usuarioId}/reactivar")
    @Transactional
    public ResponseEntity<ReactivacionInfoResponse> reactivarMiembro(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestParam(defaultValue = "true") boolean conCarnaval,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        validarDirectiva(grupoId, solicitanteId);

        GrupoMiembro gm = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));

        ReactivacionInfoResponse info = construirInfoReactivacion(grupoId, usuarioId, conCarnaval);

        // Registrar el "ponerse al día" como pago a los fondos (extracto + kardex + grupo)
        if (info.getTotal().compareTo(BigDecimal.ZERO) > 0) {
            movimientoService.registrarPagoCuota(grupoId, usuarioId,
                    info.getCarnavalAdeudado(), info.getAhorroAdeudado(),
                    "Reactivación — ponerse al día", solicitanteId, LocalDate.now());
        }

        gm.setActivo(true);
        gm.setParticipaCarnaval(conCarnaval);
        grupoMiembroRepository.save(gm);

        if (info.getTotal().compareTo(BigDecimal.ZERO) > 0) {
            notificacionService.notificarReactivacion(gm.getUsuario(), gm.getGrupo(), info.getTotal());
        }

        // Las cuotas congeladas quedan saldadas por el pago anterior → se eliminan
        List<CuotaProgramada> congeladas = cuotaProgramadaRepository
                .findByUsuarioIdAndGrupoId(usuarioId, grupoId).stream()
                .filter(c -> "CONGELADA".equals(c.getEstado()))
                .collect(Collectors.toList());
        cuotaProgramadaRepository.deleteAll(congeladas);

        // Generar las cuotas futuras (mes siguiente en adelante) para los pagos activos
        generarCuotasFuturasMiembro(grupoId, gm);

        return ResponseEntity.ok(info);
    }

    private ReactivacionInfoResponse construirInfoReactivacion(Long grupoId, Long usuarioId, boolean conCarnaval) {
        BigDecimal ahorro = calcularAhorroAdeudado(grupoId, usuarioId);
        BigDecimal carnaval = conCarnaval ? calcularCarnavalAdeudado(grupoId) : BigDecimal.ZERO;
        return ReactivacionInfoResponse.builder()
                .conCarnaval(conCarnaval)
                .ahorroAdeudado(ahorro)
                .carnavalAdeudado(carnaval)
                .total(ahorro.add(carnaval))
                .build();
    }

    private long mesesTranscurridos(LocalDate inicio, LocalDate fin, LocalDate hasta) {
        LocalDate finEf = hasta.isBefore(fin) ? hasta : fin;
        if (finEf.isBefore(inicio)) return 0;
        return ChronoUnit.MONTHS.between(inicio.withDayOfMonth(1), finEf.withDayOfMonth(1)) + 1;
    }

    /** Todo el ahorro acumulado por persona que le falta, a través de TODAS las gestiones. */
    private BigDecimal calcularAhorroAdeudado(Long grupoId, Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        BigDecimal esperado = BigDecimal.ZERO;
        for (PagoProgramado p : pagoProgramadoRepository.findByGrupoId(grupoId)) {
            long meses = mesesTranscurridos(p.getFechaInicio(), p.getFechaFin(), hoy);
            esperado = esperado.add(p.getMontoAhorro().multiply(BigDecimal.valueOf(meses)));
        }
        BigDecimal pagado = kardexRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .map(k -> k.getTotalAhorro() != null ? k.getTotalAhorro() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
        BigDecimal deuda = esperado.subtract(pagado);
        return deuda.compareTo(BigDecimal.ZERO) > 0 ? deuda : BigDecimal.ZERO;
    }

    /** Carnaval de la gestión ACTUAL transcurrida (pagos activos vigentes hoy). */
    private BigDecimal calcularCarnavalAdeudado(Long grupoId) {
        LocalDate hoy = LocalDate.now();
        BigDecimal carnaval = BigDecimal.ZERO;
        for (PagoProgramado p : pagoProgramadoRepository.findByGrupoIdAndActivoTrue(grupoId)) {
            if (!hoy.isBefore(p.getFechaInicio()) && !hoy.isAfter(p.getFechaFin())) {
                long meses = mesesTranscurridos(p.getFechaInicio(), p.getFechaFin(), hoy);
                carnaval = carnaval.add(p.getMontoCarnaval().multiply(BigDecimal.valueOf(meses)));
            }
        }
        return carnaval;
    }

    /** Genera las cuotas del miembro desde el mes siguiente (lo transcurrido ya se saldó al reactivar). */
    private void generarCuotasFuturasMiembro(Long grupoId, GrupoMiembro gm) {
        LocalDate proximo = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        for (PagoProgramado p : pagoProgramadoRepository.findByGrupoIdAndActivoTrue(grupoId)) {
            LocalDate inicioPago = p.getFechaInicio().withDayOfMonth(1);
            LocalDate desde = inicioPago.isAfter(proximo) ? inicioPago : proximo;
            LocalDate fin = p.getFechaFin().withDayOfMonth(1);
            LocalDate fecha = desde;
            while (!fecha.isAfter(fin)) {
                boolean existe = cuotaProgramadaRepository
                        .existsByPagoProgramadoIdAndUsuarioIdAndAnioAndMes(
                                p.getId(), gm.getUsuario().getId(), fecha.getYear(), fecha.getMonthValue());
                if (!existe) {
                    BigDecimal carnaval = Boolean.FALSE.equals(gm.getParticipaCarnaval())
                            ? BigDecimal.ZERO : p.getMontoCarnaval();
                    BigDecimal ahorro = p.getMontoAhorro();
                    CuotaProgramada cuota = CuotaProgramada.builder()
                            .pagoProgramado(p)
                            .usuario(gm.getUsuario())
                            .grupo(p.getGrupo())
                            .anio(fecha.getYear())
                            .mes(fecha.getMonthValue())
                            .monto(carnaval.add(ahorro))
                            .montoCarnaval(carnaval)
                            .montoAhorro(ahorro)
                            .estado("PENDIENTE")
                            .multaAplicada(false)
                            .montoMulta(BigDecimal.ZERO)
                            .build();
                    cuotaProgramadaRepository.save(cuota);
                }
                fecha = fecha.plusMonths(1);
            }
        }
    }

    // ============================================================
    //  AÑADIR UN MIEMBRO (que se sumó tarde al grupo) A UN PAGO PROGRAMADO YA EN CURSO
    // ============================================================

    @GetMapping("/{pagoProgramadoId}/miembros-faltantes")
    public ResponseEntity<List<com.compartix.backend.dto.response.UsuarioResponse>> obtenerMiembrosFaltantes(
            @PathVariable Long grupoId,
            @PathVariable Long pagoProgramadoId,
            @RequestHeader("Authorization") String token) {
        validarDirectiva(grupoId, extraerUsuarioId(token));

        pagoProgramadoRepository.findById(pagoProgramadoId)
                .filter(p -> p.getGrupo().getId().equals(grupoId))
                .orElseThrow(() -> new ResourceNotFoundException("Pago programado no encontrado"));

        List<Long> idsConCuota = cuotaProgramadaRepository.findByPagoProgramadoId(pagoProgramadoId).stream()
                .map(c -> c.getUsuario().getId())
                .distinct()
                .collect(Collectors.toList());

        List<com.compartix.backend.dto.response.UsuarioResponse> faltantes = grupoMiembroRepository
                .findByGrupoIdAndActivoTrue(grupoId).stream()
                .filter(gm -> !idsConCuota.contains(gm.getUsuario().getId()))
                .map(gm -> com.compartix.backend.dto.response.UsuarioResponse.builder()
                        .id(gm.getUsuario().getId())
                        .nombre(gm.getUsuario().getNombre())
                        .apellido(gm.getUsuario().getApellido())
                        .email(gm.getUsuario().getEmail())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(faltantes);
    }

    @PostMapping("/{pagoProgramadoId}/miembros/{usuarioId}")
    @Transactional
    public ResponseEntity<List<CuotaResponse>> agregarMiembroAPago(
            @PathVariable Long grupoId,
            @PathVariable Long pagoProgramadoId,
            @PathVariable Long usuarioId,
            @RequestHeader("Authorization") String token) {
        validarDirectiva(grupoId, extraerUsuarioId(token));

        PagoProgramado pago = pagoProgramadoRepository.findById(pagoProgramadoId)
                .filter(p -> p.getGrupo().getId().equals(grupoId))
                .orElseThrow(() -> new ResourceNotFoundException("Pago programado no encontrado"));
        if (!Boolean.TRUE.equals(pago.getActivo())) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "Este pago programado ya finalizó");
        }

        GrupoMiembro miembro = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Miembro no encontrado"));
        if (!Boolean.TRUE.equals(miembro.getActivo())) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "El miembro está inactivo en el grupo");
        }

        LocalDate mesActual = LocalDate.now().withDayOfMonth(1);
        List<CuotaProgramada> generadas = new java.util.ArrayList<>();

        LocalDate fecha = pago.getFechaInicio().withDayOfMonth(1);
        LocalDate fin = pago.getFechaFin().withDayOfMonth(1);
        while (!fecha.isAfter(fin)) {
            boolean existe = cuotaProgramadaRepository
                    .existsByPagoProgramadoIdAndUsuarioIdAndAnioAndMes(
                            pago.getId(), usuarioId, fecha.getYear(), fecha.getMonthValue());
            if (!existe) {
                BigDecimal carnaval = Boolean.FALSE.equals(miembro.getParticipaCarnaval())
                        ? BigDecimal.ZERO : pago.getMontoCarnaval();
                BigDecimal ahorro = pago.getMontoAhorro();
                // Los meses ya transcurridos antes de hoy quedan exentos de mora: se marcan
                // "multaAplicada=true" (en 0) para que la verificación automática de multas
                // los ignore. Solo el mes actual en adelante puede generar mora normalmente.
                boolean esMesPasado = fecha.isBefore(mesActual);

                CuotaProgramada cuota = CuotaProgramada.builder()
                        .pagoProgramado(pago)
                        .usuario(miembro.getUsuario())
                        .grupo(pago.getGrupo())
                        .anio(fecha.getYear())
                        .mes(fecha.getMonthValue())
                        .monto(carnaval.add(ahorro))
                        .montoCarnaval(carnaval)
                        .montoAhorro(ahorro)
                        .estado("PENDIENTE")
                        .multaAplicada(esMesPasado)
                        .montoMulta(BigDecimal.ZERO)
                        .build();
                cuotaProgramadaRepository.save(cuota);
                generadas.add(cuota);
            }
            fecha = fecha.plusMonths(1);
        }

        if (generadas.isEmpty()) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "El miembro ya tiene todas las cuotas de este pago programado");
        }

        notificacionService.notificarMiembroAgregadoAPago(miembro.getUsuario(), pago.getGrupo(), pago.getNombre());

        List<CuotaResponse> response = generadas.stream()
                .map(this::toCuotaResponse)
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verificar-multas")
    public ResponseEntity<Void> verificarMultas(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        verificacionMultasService.verificarMultasGrupo(grupoId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{pagoProgramadoId}/desactivar")
    @Transactional
    public ResponseEntity<Void> finalizarPagoProgramado(
            @PathVariable Long grupoId,
            @PathVariable Long pagoProgramadoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        PagoProgramado pago = pagoProgramadoRepository.findById(pagoProgramadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago programado no encontrado"));

        pago.setActivo(false);
        pago.setFechaFin(LocalDate.now());
        pagoProgramadoRepository.save(pago);

        // Al finalizar, las cuotas no pagadas dejan de ser exigibles → se eliminan
        // para que solo queden las pagadas en el historial.
        List<CuotaProgramada> noPagadas = cuotaProgramadaRepository
                .findByPagoProgramadoId(pagoProgramadoId).stream()
                .filter(c -> !"PAGADA".equals(c.getEstado()))
                .collect(Collectors.toList());
        cuotaProgramadaRepository.deleteAll(noPagadas);

        notificacionService.notificarPagoProgramadoFinalizado(pago.getGrupo(), pago.getNombre());

        return ResponseEntity.noContent().build();
    }

    private void generarCuotas(PagoProgramado pago, Grupo grupo) {
        List<GrupoMiembro> miembros = grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupo.getId());
        LocalDate inicio = pago.getFechaInicio();
        LocalDate fin = pago.getFechaFin();

        LocalDate fecha = inicio.withDayOfMonth(1);
        while (!fecha.isAfter(fin.withDayOfMonth(1))) {
            for (GrupoMiembro miembro : miembros) {
                boolean existe = cuotaProgramadaRepository
                        .existsByPagoProgramadoIdAndUsuarioIdAndAnioAndMes(
                                pago.getId(), miembro.getUsuario().getId(),
                                fecha.getYear(), fecha.getMonthValue());
                if (!existe) {
                    // Si el miembro no participa en carnaval, su cuota solo lleva el ahorro
                    BigDecimal carnaval = Boolean.FALSE.equals(miembro.getParticipaCarnaval())
                            ? BigDecimal.ZERO : pago.getMontoCarnaval();
                    BigDecimal ahorro = pago.getMontoAhorro();
                    BigDecimal total = carnaval.add(ahorro);

                    CuotaProgramada cuota = CuotaProgramada.builder()
                            .pagoProgramado(pago)
                            .usuario(miembro.getUsuario())
                            .grupo(grupo)
                            .anio(fecha.getYear())
                            .mes(fecha.getMonthValue())
                            .monto(total)
                            .montoCarnaval(carnaval)
                            .montoAhorro(ahorro)
                            .estado("PENDIENTE")
                            .multaAplicada(false)
                            .montoMulta(BigDecimal.ZERO)
                            .build();
                    cuotaProgramadaRepository.save(cuota);
                }
            }
            fecha = fecha.plusMonths(1);
        }
    }

    private PagoProgramadoResponse toResponse(PagoProgramado p) {
        return PagoProgramadoResponse.builder()
                .id(p.getId())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .montoMensual(p.getMontoMensual())
                .montoCarnaval(p.getMontoCarnaval())
                .montoAhorro(p.getMontoAhorro())
                .fechaInicio(p.getFechaInicio())
                .fechaFin(p.getFechaFin())
                .diaLimitePago(p.getDiaLimitePago())
                .tipoMulta(p.getTipoMulta())
                .montoMulta(p.getMontoMulta())
                .porcentajeMulta(p.getPorcentajeMulta())
                .activo(p.getActivo())
                .build();
    }

    private CuotaResponse toCuotaResponse(CuotaProgramada c) {
        return CuotaResponse.builder()
                .id(c.getId())
                .usuarioId(c.getUsuario().getId())
                .nombreUsuario(c.getUsuario().getNombre() + " " + c.getUsuario().getApellido())
                .pagoProgramadoId(c.getPagoProgramado().getId())
                .nombrePago(c.getPagoProgramado().getNombre())
                .anio(c.getAnio())
                .mes(c.getMes())
                .monto(c.getMonto())
                .montoCarnaval(c.getMontoCarnaval())
                .montoAhorro(c.getMontoAhorro())
                .estado(c.getEstado())
                .fechaPago(c.getFechaPago())
                .multaAplicada(c.getMultaAplicada())
                .montoMulta(c.getMontoMulta())
                .build();
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        GrupoMiembro miembro = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new UnauthorizedException("No eres miembro de este grupo"));
        if (miembro.getRol().name().equals("MIEMBRO")) {
            throw new UnauthorizedException("Solo la directiva puede realizar esta acción");
        }
    }

    private void validarMiembro(Long grupoId, Long usuarioId) {
        if (!grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new UnauthorizedException("No eres miembro de este grupo");
        }
    }
}