package com.compartix.backend.controller;

import com.compartix.backend.dto.request.*;
import com.compartix.backend.dto.response.MovimientoDetalleResponse;
import com.compartix.backend.dto.response.MovimientoResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.MovimientoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos/{grupoId}/movimientos")
@RequiredArgsConstructor
public class MovimientoController {

    private final MovimientoService movimientoService;
    private final JwtUtil jwtUtil;

    @PostMapping("/aporte")
    public ResponseEntity<MovimientoResponse> registrarAporte(
            @PathVariable Long grupoId,
            @Valid @RequestBody RegistrarAporteRequest request,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarAporte(grupoId, request, solicitanteId));
    }

    @PostMapping("/gasto-compartido")
    public ResponseEntity<MovimientoResponse> registrarGastoCompartido(
            @PathVariable Long grupoId,
            @Valid @RequestBody RegistrarGastoCompartidoRequest request,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarGastoCompartido(grupoId, request, solicitanteId));
    }

    @PostMapping("/gasto-individual")
    public ResponseEntity<MovimientoResponse> registrarGastoIndividual(
            @PathVariable Long grupoId,
            @Valid @RequestBody RegistrarGastoIndividualRequest request,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarGastoIndividual(grupoId, request, solicitanteId));
    }

    @PostMapping("/multa")
    public ResponseEntity<MovimientoResponse> registrarMulta(
            @PathVariable Long grupoId,
            @Valid @RequestBody RegistrarMultaRequest request,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarMulta(grupoId, request, solicitanteId));
    }

    @GetMapping
    public ResponseEntity<List<MovimientoResponse>> obtenerMovimientos(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.ok(movimientoService.obtenerMovimientosGrupo(grupoId, solicitanteId));
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }
    @GetMapping("/mis-movimientos")
    public ResponseEntity<List<MovimientoResponse>> obtenerMisMovimientos(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(movimientoService.obtenerMovimientosUsuario(grupoId, usuarioId));
    }
    @GetMapping("/{movimientoId}/detalle")
    public ResponseEntity<List<MovimientoDetalleResponse>> obtenerDetalle(
            @PathVariable Long grupoId,
            @PathVariable Long movimientoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(movimientoService.obtenerDetalleMovimiento(grupoId, movimientoId, usuarioId));
    }
    @PostMapping("/ingreso-directo")
    public ResponseEntity<MovimientoResponse> registrarIngresoDirecto(
            @PathVariable Long grupoId,
            @Valid @RequestBody RegistrarIngresoDirectoRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(movimientoService.registrarIngresoDirecto(grupoId, request, usuarioId));
    }
    @DeleteMapping("/{movimientoId}")
    public ResponseEntity<Void> eliminarMovimiento(
            @PathVariable Long grupoId,
            @PathVariable Long movimientoId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        movimientoService.eliminarMovimiento(grupoId, movimientoId, solicitanteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{movimientoId}/mi-detalle")
    public ResponseEntity<List<MovimientoDetalleResponse>> obtenerMiDetalle(
            @PathVariable Long grupoId,
            @PathVariable Long movimientoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(movimientoService.obtenerMiDetalleMovimiento(grupoId, movimientoId, usuarioId));
    }
}