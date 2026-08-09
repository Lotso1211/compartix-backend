package com.compartix.backend.service;

import com.compartix.backend.entity.CuotaProgramada;
import com.compartix.backend.entity.Kardex;
import com.compartix.backend.entity.PagoProgramado;
import com.compartix.backend.repository.CuotaProgramadaRepository;
import com.compartix.backend.repository.KardexRepository;
import com.compartix.backend.repository.PagoProgramadoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aplica las multas por mora de las cuotas programadas.
 * Corre automáticamente todos los días a las 06:00 para todos los grupos;
 * el botón "Verificar multas" de la directiva reusa la misma lógica.
 */
@Service
@RequiredArgsConstructor
public class VerificacionMultasService {

    private static final Logger log = LoggerFactory.getLogger(VerificacionMultasService.class);

    private final PagoProgramadoRepository pagoProgramadoRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final KardexRepository kardexRepository;
    private final NotificacionService notificacionService;

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void verificarTodosLosGrupos() {
        List<Long> grupoIds = pagoProgramadoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .map(p -> p.getGrupo().getId())
                .distinct()
                .collect(Collectors.toList());
        log.info("Verificación automática de multas: {} grupo(s) con pagos activos", grupoIds.size());
        grupoIds.forEach(this::verificarMultasGrupo);
    }

    @Transactional
    public void verificarMultasGrupo(Long grupoId) {
        LocalDate hoy = LocalDate.now();
        List<PagoProgramado> pagos = pagoProgramadoRepository.findByGrupoIdAndActivoTrue(grupoId);

        for (PagoProgramado pago : pagos) {
            // Recorre cada mes pasado (no el mes en curso)
            LocalDate mes = pago.getFechaInicio().withDayOfMonth(1);
            LocalDate limiteMes = hoy.withDayOfMonth(1);

            while (mes.isBefore(limiteMes)) {
                // La multa se activa 5 días después de terminar el mes
                LocalDate finDeMes = mes.withDayOfMonth(mes.lengthOfMonth());
                LocalDate fechaMulta = finDeMes.plusDays(5);

                if (hoy.isAfter(fechaMulta)) {
                    List<CuotaProgramada> cuotas = cuotaProgramadaRepository
                            .findByGrupoIdAndAnioAndMes(grupoId, mes.getYear(), mes.getMonthValue())
                            .stream()
                            .filter(c -> c.getPagoProgramado().getId().equals(pago.getId()))
                            .filter(c -> c.getEstado().equals("PENDIENTE"))
                            .filter(c -> !c.getMultaAplicada())
                            .collect(Collectors.toList());

                    for (CuotaProgramada cuota : cuotas) {
                        BigDecimal montoMulta = calcularMontoMulta(pago, cuota.getMonto());
                        cuota.setMultaAplicada(true);
                        cuota.setMontoMulta(montoMulta);
                        cuota.setEstado("VENCIDA");
                        cuotaProgramadaRepository.save(cuota);

                        // La multa solo se refleja como indicador pendiente (totalMultas),
                        // NO reduce el saldo personal (intencional: va al fondo del grupo).
                        Kardex kardex = kardexRepository
                                .findByGrupoIdAndUsuarioId(grupoId, cuota.getUsuario().getId())
                                .orElseGet(() -> kardexRepository.save(Kardex.builder()
                                        .grupo(cuota.getGrupo())
                                        .usuario(cuota.getUsuario())
                                        .totalAportes(BigDecimal.ZERO)
                                        .totalGastosCompartidos(BigDecimal.ZERO)
                                        .totalGastosIndividuales(BigDecimal.ZERO)
                                        .totalMultas(BigDecimal.ZERO)
                                        .totalAhorro(BigDecimal.ZERO)
                                        .saldoActual(BigDecimal.ZERO)
                                        .build()));
                        kardex.setTotalMultas(kardex.getTotalMultas().add(montoMulta));
                        kardexRepository.save(kardex);

                        notificacionService.notificarMoraCuota(cuota.getUsuario(), pago.getGrupo(),
                                montoMulta, getNombreMes(cuota.getMes()), cuota.getAnio());
                    }
                }

                mes = mes.plusMonths(1);
            }
        }
    }

    private BigDecimal calcularMontoMulta(PagoProgramado pago, BigDecimal montoCuota) {
        if ("PORCENTAJE".equals(pago.getTipoMulta())) {
            return montoCuota.multiply(pago.getPorcentajeMulta())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return pago.getMontoMulta() != null ? pago.getMontoMulta() : BigDecimal.ZERO;
    }

    private String getNombreMes(Integer mes) {
        return (mes != null && mes >= 1 && mes <= 12) ? MESES[mes - 1] : String.valueOf(mes);
    }
}
