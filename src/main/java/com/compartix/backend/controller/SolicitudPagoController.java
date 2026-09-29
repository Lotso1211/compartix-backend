package com.compartix.backend.controller;

import com.compartix.backend.dto.request.RechazarSolicitudRequest;
import com.compartix.backend.dto.response.SolicitudPagoResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.SolicitudPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/grupos/{grupoId}/solicitudes-pago")
@RequiredArgsConstructor
public class SolicitudPagoController {

    private final SolicitudPagoService solicitudPagoService;
    private final JwtUtil jwtUtil;

    @PostMapping("/cuota/{cuotaId}")
    public ResponseEntity<SolicitudPagoResponse> subirComprobanteCuota(
            @PathVariable Long grupoId,
            @PathVariable Long cuotaId,
            @RequestParam("comprobante") MultipartFile comprobante,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitudPagoService.subirComprobanteCuota(grupoId, cuotaId, usuarioId, comprobante));
    }

    @PostMapping("/multa/{multaId}")
    public ResponseEntity<SolicitudPagoResponse> subirComprobanteMulta(
            @PathVariable Long grupoId,
            @PathVariable Long multaId,
            @RequestParam("comprobante") MultipartFile comprobante,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitudPagoService.subirComprobanteMulta(grupoId, multaId, usuarioId, comprobante));
    }

    @GetMapping
    public ResponseEntity<List<SolicitudPagoResponse>> listar(
            @PathVariable Long grupoId,
            @RequestParam(required = false) String estado,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(solicitudPagoService.listar(grupoId, usuarioId, estado));
    }

    @GetMapping("/mis-solicitudes")
    public ResponseEntity<List<SolicitudPagoResponse>> misSolicitudes(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(solicitudPagoService.misSolicitudes(grupoId, usuarioId));
    }

    @PatchMapping("/{solicitudId}/aprobar")
    public ResponseEntity<SolicitudPagoResponse> aprobar(
            @PathVariable Long grupoId,
            @PathVariable Long solicitudId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(solicitudPagoService.aprobar(grupoId, solicitudId, usuarioId));
    }

    @PatchMapping("/{solicitudId}/rechazar")
    public ResponseEntity<SolicitudPagoResponse> rechazar(
            @PathVariable Long grupoId,
            @PathVariable Long solicitudId,
            @RequestBody(required = false) RechazarSolicitudRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        String motivo = request != null ? request.getMotivo() : null;
        return ResponseEntity.ok(solicitudPagoService.rechazar(grupoId, solicitudId, usuarioId, motivo));
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }
}
