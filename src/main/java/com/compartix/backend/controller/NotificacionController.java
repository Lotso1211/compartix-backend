package com.compartix.backend.controller;

import com.compartix.backend.dto.response.NotificacionResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar(
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(notificacionService.listar(usuarioId));
    }

    @GetMapping("/no-leidas/count")
    public ResponseEntity<Map<String, Long>> contarNoLeidas(
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        return ResponseEntity.ok(Map.of("count", notificacionService.contarNoLeidas(usuarioId)));
    }

    @PatchMapping("/{id}/leer")
    public ResponseEntity<Void> marcarLeida(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        notificacionService.marcarLeida(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.noContent().build();
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }
}
