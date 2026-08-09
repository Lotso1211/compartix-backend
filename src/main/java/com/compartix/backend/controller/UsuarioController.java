package com.compartix.backend.controller;

import com.compartix.backend.dto.request.ActualizarPerfilRequest;
import com.compartix.backend.dto.request.CambiarPasswordRequest;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtUtil jwtUtil;

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioResponse> obtenerPerfil(
            @RequestHeader("Authorization") String authHeader) {
        Long usuarioId = extraerUsuarioId(authHeader);
        return ResponseEntity.ok(usuarioService.obtenerPerfil(usuarioId));
    }

    @PatchMapping("/perfil")
    public ResponseEntity<UsuarioResponse> actualizarPerfil(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ActualizarPerfilRequest request) {
        Long usuarioId = extraerUsuarioId(authHeader);
        return ResponseEntity.ok(usuarioService.actualizarPerfil(usuarioId, request));
    }

    @PostMapping("/foto")
    public ResponseEntity<Map<String, String>> subirFoto(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("foto") MultipartFile foto) {
        Long usuarioId = extraerUsuarioId(authHeader);
        String url = usuarioService.actualizarFoto(usuarioId, foto);
        return ResponseEntity.ok(Map.of("fotoUrl", url));
    }

    @PatchMapping("/cambiar-password")
    public ResponseEntity<Void> cambiarPassword(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CambiarPasswordRequest request) {
        Long usuarioId = extraerUsuarioId(authHeader);
        usuarioService.cambiarPassword(usuarioId, request);
        return ResponseEntity.noContent().build();
    }

    private Long extraerUsuarioId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.extractUsuarioId(token);
    }
}
