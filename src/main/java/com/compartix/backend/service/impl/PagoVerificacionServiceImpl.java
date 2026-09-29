package com.compartix.backend.service.impl;

import com.compartix.backend.dto.response.CuotaResponse;
import com.compartix.backend.dto.response.MultaResponse;
import com.compartix.backend.entity.CuotaProgramada;
import com.compartix.backend.entity.Multa;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.repository.CuotaProgramadaRepository;
import com.compartix.backend.repository.KardexRepository;
import com.compartix.backend.repository.MovimientoRepository;
import com.compartix.backend.repository.MultaRepository;
import com.compartix.backend.repository.SaldoGrupoRepository;
import com.compartix.backend.service.MovimientoService;
import com.compartix.backend.service.NotificacionService;
import com.compartix.backend.service.PagoVerificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PagoVerificacionServiceImpl implements PagoVerificacionService {

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final MultaRepository multaRepository;
    private final MovimientoRepository movimientoRepository;
    private final MovimientoService movimientoService;
    private final KardexRepository kardexRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final NotificacionService notificacionService;

    @Override
    @Transactional
    public CuotaResponse confirmarPagoCuota(Long grupoId, Long cuotaId, Long ejecutorId, String comprobanteUrl) {
        CuotaProgramada cuota = cuotaProgramadaRepository.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada"));

        if ("PAGADA".equals(cuota.getEstado())) {
            throw new BadRequestException("Esta cuota ya está pagada");
        }
        if ("ANULADA".equals(cuota.getEstado())) {
            throw new BadRequestException(
                    "Esta cuota está anulada. Revierte la anulación antes de marcarla como pagada.");
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
                ejecutorId, LocalDate.now(), comprobanteUrl);

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
                    ejecutorId, LocalDate.now());

            // Limpiar el indicador de multa pendiente en el kardex (sin bajar de 0).
            kardexRepository.findByGrupoIdAndUsuarioId(grupoId, cuota.getUsuario().getId())
                    .ifPresent(kardex -> {
                        BigDecimal restante = kardex.getTotalMultas().subtract(montoMulta);
                        kardex.setTotalMultas(restante.max(BigDecimal.ZERO));
                        kardexRepository.save(kardex);
                    });
        }

        return toCuotaResponse(cuota);
    }

    @Override
    @Transactional
    public MultaResponse confirmarPagoMulta(Long grupoId, Long multaId, Long ejecutorId) {
        Multa multa = multaRepository.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada"));

        if ("PAGADA".equals(multa.getEstado())) {
            throw new BadRequestException("Esta multa ya está pagada");
        }

        multa.setEstado("PAGADA");
        multa.setFechaPago(LocalDate.now());
        multaRepository.save(multa);

        // El indicador de "debe" desaparece del kardex personal; el monto NO se acredita
        // al saldo del miembro (la multa nunca fue plata suya, era del fondo del grupo).
        kardexRepository.findByGrupoIdAndUsuarioId(grupoId, multa.getUsuario().getId())
                .ifPresent(kardex -> {
                    kardex.setTotalMultas(kardex.getTotalMultas().subtract(multa.getMonto()).max(BigDecimal.ZERO));
                    kardexRepository.save(kardex);
                });

        // Recién ahora, al pagarse, se acredita al fondo de ahorro del grupo.
        saldoGrupoRepository.findByGrupoId(grupoId).ifPresent(saldo -> {
            saldo.setTotalMultas(saldo.getTotalMultas().add(multa.getMonto()));
            saldo.setSaldoDisponible(saldo.getSaldoDisponible().add(multa.getMonto()));
            saldo.setSaldoAhorro(saldo.getSaldoAhorro().add(multa.getMonto()));
            saldoGrupoRepository.save(saldo);
        });

        notificacionService.notificarMultaPagada(multa.getUsuario(), multa.getGrupo(),
                multa.getMonto(), multa.getMotivo());

        return toMultaResponse(multa);
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
                .motivoAnulacion(c.getMotivoAnulacion())
                .build();
    }

    private MultaResponse toMultaResponse(Multa m) {
        return MultaResponse.builder()
                .id(m.getId())
                .usuarioId(m.getUsuario().getId())
                .nombreUsuario(m.getUsuario().getNombre() + " " + m.getUsuario().getApellido())
                .motivo(m.getMotivo())
                .monto(m.getMonto())
                .estado(m.getEstado())
                .fecha(m.getFecha())
                .fechaPago(m.getFechaPago())
                .periodoMes(m.getPeriodoMes())
                .periodoAnio(m.getPeriodoAnio())
                .build();
    }

    private String getNombreMes(Integer mes) {
        return (mes != null && mes >= 1 && mes <= 12) ? MESES[mes - 1] : String.valueOf(mes);
    }
}
