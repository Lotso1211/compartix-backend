package com.compartix.backend.controller;

import com.compartix.backend.dto.request.ActualizarAlertasRequest;
import com.compartix.backend.dto.request.CrearGrupoRequest;
import com.compartix.backend.dto.response.GrupoResponse;
import com.compartix.backend.dto.response.SaldoGrupoResponse;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.GrupoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<GrupoResponse> crearGrupo(
            @Valid @RequestBody CrearGrupoRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(grupoService.crearGrupo(request, usuarioId));
    }

    @PostMapping("/unirse")
    public ResponseEntity<GrupoResponse> unirseAGrupo(
            @RequestParam String codigo,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.unirseAGrupo(codigo, usuarioId));
    }

    @GetMapping("/{grupoId}")
    public ResponseEntity<GrupoResponse> obtenerGrupo(@PathVariable Long grupoId) {
        return ResponseEntity.ok(grupoService.obtenerGrupo(grupoId));
    }

    @GetMapping("/mis-grupos")
    public ResponseEntity<List<GrupoResponse>> obtenerMisGrupos(
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.obtenerGruposDeUsuario(usuarioId));
    }

    @GetMapping("/{grupoId}/saldo")
    public ResponseEntity<SaldoGrupoResponse> obtenerSaldo(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(grupoService.obtenerSaldoGrupo(grupoId));
    }

    @PostMapping("/{grupoId}/miembros/{usuarioId}")
    public ResponseEntity<Void> agregarMiembro(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        grupoService.agregarMiembro(grupoId, usuarioId, solicitanteId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{grupoId}/miembros/{usuarioId}")
    public ResponseEntity<Void> quitarMiembro(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        grupoService.quitarMiembro(grupoId, usuarioId, solicitanteId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{grupoId}/miembros")
    public ResponseEntity<List<UsuarioResponse>> obtenerMiembros(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.obtenerMiembros(grupoId, usuarioId));
    }
    @GetMapping("/{grupoId}/miembros/gestion")
    public ResponseEntity<List<UsuarioResponse>> obtenerMiembrosGestion(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.obtenerMiembrosGestion(grupoId, usuarioId));
    }
    @GetMapping("/{grupoId}/mi-rol")
    public ResponseEntity<String> obtenerMiRol(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.obtenerRolUsuario(grupoId, usuarioId));
    }
    @PatchMapping("/{grupoId}/miembros/{usuarioId}/rol")
    public ResponseEntity<Void> cambiarRol(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestParam String rol,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        grupoService.cambiarRolMiembro(grupoId, usuarioId, rol, solicitanteId);
        return ResponseEntity.noContent().build();
    }

    private Long extraerUsuarioId(String token) {

        return jwtUtil.extractUsuarioId(token.substring(7));
    }

    @DeleteMapping("/{grupoId}")
    public ResponseEntity<Void> eliminarGrupo(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        grupoService.eliminarGrupo(grupoId, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{grupoId}/alertas")
    public ResponseEntity<GrupoResponse> actualizarAlertas(
            @PathVariable Long grupoId,
            @RequestBody ActualizarAlertasRequest request,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.ok(grupoService.actualizarAlertas(grupoId, request, solicitanteId));
    }
}