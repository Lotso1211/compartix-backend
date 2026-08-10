package com.compartix.backend.controller;

import com.compartix.backend.dto.request.GoogleLoginRequest;
import com.compartix.backend.dto.request.LoginRequest;
import com.compartix.backend.dto.request.RegisterRequest;
import com.compartix.backend.dto.request.Verificar2faRequest;
import com.compartix.backend.dto.response.AuthResponse;
import com.compartix.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginConGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginConGoogle(request.getIdToken()));
    }

    @PostMapping("/verificar-2fa")
    public ResponseEntity<AuthResponse> verificar2fa(@Valid @RequestBody Verificar2faRequest request) {
        return ResponseEntity.ok(authService.verificarCodigo2fa(request.getEmail(), request.getCodigo()));
    }

    @PostMapping("/reenviar-2fa")
    public ResponseEntity<Void> reenviar2fa(@RequestParam String email) {
        authService.reenviarCodigo2fa(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestParam String token) {
        return ResponseEntity.ok(authService.refreshToken(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam String token) {
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}