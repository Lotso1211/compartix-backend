package com.compartix.backend.controller;

import com.compartix.backend.dto.response.MultaResponse;
import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.entity.Multa;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.GrupoMiembroRepository;
import com.compartix.backend.repository.MultaRepository;
import com.compartix.backend.repository.KardexRepository;
import com.compartix.backend.repository.SaldoGrupoRepository;
import com.compartix.backend.entity.Kardex;
import com.compartix.backend.entity.SaldoGrupo;
import com.compartix.backend.security.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grupos/{grupoId}/multas")
@RequiredArgsConstructor
public class MultaController {

    private final MultaRepository multaRepository;
    private final KardexRepository kardexRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<MultaResponse>> obtenerMultas(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);
        List<MultaResponse> multas = multaRepository
                .findByGrupoIdOrderByFechaDesc(grupoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(multas);
    }

    @GetMapping("/mis-multas")
    public ResponseEntity<List<MultaResponse>> obtenerMisMultas(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = jwtUtil.extractUsuarioId(token.substring(7));
        List<MultaResponse> multas = multaRepository
                .findByGrupoIdAndUsuarioIdOrderByFechaDesc(grupoId, usuarioId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(multas);
    }

    @PatchMapping("/{multaId}/pagar")
    public ResponseEntity<MultaResponse> marcarComoPagada(
            @PathVariable Long grupoId,
            @PathVariable Long multaId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Multa multa = multaRepository.findById(multaId)
                .orElseThrow(() -> new RuntimeException("Multa no encontrada"));

        if ("PAGADA".equals(multa.getEstado())) {
            return ResponseEntity.badRequest().build();
        }

        // Marcar como pagada
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

        return ResponseEntity.ok(toResponse(multa));
    }


    @DeleteMapping("/{multaId}")
    @Transactional
    public ResponseEntity<Void> eliminarMulta(
            @PathVariable Long grupoId,
            @PathVariable Long multaId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Multa multa = multaRepository.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada"));

        BigDecimal monto = multa.getMonto();
        if ("PENDIENTE".equals(multa.getEstado())) {
            // Nunca se acreditó al fondo (eso pasa recién al pagar): solo baja el
            // indicador del kardex personal.
            kardexRepository.findByGrupoIdAndUsuarioId(grupoId, multa.getUsuario().getId())
                    .ifPresent(kardex -> {
                        kardex.setTotalMultas(kardex.getTotalMultas().subtract(monto).max(BigDecimal.ZERO));
                        kardexRepository.save(kardex);
                    });
        } else {
            // Ya estaba PAGADA: el fondo sí se acreditó al pagarla, ahora se revierte.
            saldoGrupoRepository.findByGrupoId(grupoId).ifPresent(saldo -> {
                saldo.setTotalMultas(saldo.getTotalMultas().subtract(monto));
                saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(monto));
                saldo.setSaldoAhorro(saldo.getSaldoAhorro().subtract(monto));
                saldoGrupoRepository.save(saldo);
            });
        }

        multaRepository.delete(multa);
        return ResponseEntity.noContent().build();
    }

    private MultaResponse toResponse(Multa m) {
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