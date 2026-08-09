package com.compartix.backend.controller;

import com.compartix.backend.dto.response.KardexResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.KardexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grupos/{grupoId}/kardex")
@RequiredArgsConstructor
public class KardexController {

    private final KardexService kardexService;
    private final JwtUtil jwtUtil;

    @GetMapping("/mio")
    public ResponseEntity<KardexResponse> obtenerMiKardex(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(kardexService.obtenerKardex(grupoId, usuarioId, usuarioId));
    }

    @GetMapping("/{usuarioId}")
    public ResponseEntity<KardexResponse> obtenerKardexMiembro(
            @PathVariable Long grupoId,
            @PathVariable Long usuarioId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.ok(kardexService.obtenerKardex(grupoId, usuarioId, solicitanteId));
    }

    @GetMapping
    public ResponseEntity<List<KardexResponse>> obtenerKardexGrupo(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long solicitanteId = extraerUsuarioId(token);
        return ResponseEntity.ok(kardexService.obtenerKardexGrupo(grupoId, solicitanteId));
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }
}