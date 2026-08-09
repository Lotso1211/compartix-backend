package com.compartix.backend.service.impl;

import com.compartix.backend.dto.request.LoginRequest;
import com.compartix.backend.dto.request.RegisterRequest;
import com.compartix.backend.dto.response.AuthResponse;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.entity.RefreshToken;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.RefreshTokenRepository;
import com.compartix.backend.repository.UsuarioRepository;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Value("${google.client-id:}")
    private String googleClientId;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Ya existe un usuario con ese email");
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .telefono(request.getTelefono())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .activo(true)
                .build();

        usuarioRepository.save(usuario);

        return generateAuthResponse(usuario);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return generateAuthResponse(usuario);
    }

    @Override
    @Transactional
    public AuthResponse loginConGoogle(String idToken) {
        // Google valida la firma del ID token en su endpoint tokeninfo;
        // aquí solo verificamos que la respuesta sea válida y para nuestra app.
        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> respuesta = new RestTemplate().getForObject(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken, Map.class);
            payload = respuesta;
        } catch (Exception e) {
            throw new UnauthorizedException("Token de Google inválido o expirado");
        }

        if (payload == null || payload.get("email") == null) {
            throw new UnauthorizedException("Token de Google inválido");
        }
        if (!googleClientId.isBlank() && !googleClientId.equals(payload.get("aud"))) {
            throw new UnauthorizedException("El token de Google no corresponde a esta aplicación");
        }
        if (!"true".equals(String.valueOf(payload.get("email_verified")))) {
            throw new UnauthorizedException("La cuenta de Google no tiene el email verificado");
        }

        String email = (String) payload.get("email");
        Usuario usuario = usuarioRepository.findByEmail(email).orElseGet(() -> {
            String nombre = payload.get("given_name") != null
                    ? (String) payload.get("given_name") : email.split("@")[0];
            String apellido = payload.get("family_name") != null
                    ? (String) payload.get("family_name") : "";
            // Cuenta creada vía Google: password aleatoria (solo podrá entrar con Google
            // hasta que cambie su contraseña desde el perfil).
            return usuarioRepository.save(Usuario.builder()
                    .nombre(nombre)
                    .apellido(apellido)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .fotoUrl((String) payload.get("picture"))
                    .activo(true)
                    .build());
        });

        return generateAuthResponse(usuario);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (refreshToken.getRevocado() || refreshToken.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token expirado o revocado");
        }

        refreshToken.setRevocado(true);
        refreshTokenRepository.save(refreshToken);

        return generateAuthResponse(refreshToken.getUsuario());
    }

    @Override
    @Transactional
    public void logout(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevocado(true);
            refreshTokenRepository.save(rt);
        });
    }

    private AuthResponse generateAuthResponse(Usuario usuario) {
        String accessToken = jwtUtil.generateToken(usuario.getEmail(), usuario.getId());
        String refreshTokenStr = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .usuario(usuario)
                .token(refreshTokenStr)
                .expiraEn(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .revocado(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        UsuarioResponse usuarioResponse = UsuarioResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .email(usuario.getEmail())
                .telefono(usuario.getTelefono())
                .fotoUrl(usuario.getFotoUrl())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .usuario(usuarioResponse)
                .build();
    }
}