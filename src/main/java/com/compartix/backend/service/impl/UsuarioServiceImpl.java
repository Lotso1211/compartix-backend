package com.compartix.backend.service.impl;

import com.compartix.backend.dto.request.ActualizarPerfilRequest;
import com.compartix.backend.dto.request.CambiarPasswordRequest;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.repository.UsuarioRepository;
import com.compartix.backend.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String UPLOAD_DIR = "uploads/avatars";

    // URL pública del backend (para construir la URL de las fotos subidas)
    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public void cambiarPassword(Long usuarioId, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }

        if (passwordEncoder.matches(request.getPasswordNuevo(), usuario.getPasswordHash())) {
            throw new BadRequestException("La nueva contraseña debe ser diferente a la actual");
        }

        usuario.setPasswordHash(passwordEncoder.encode(request.getPasswordNuevo()));
        usuarioRepository.save(usuario);
    }

    @Override
    public UsuarioResponse obtenerPerfil(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return toResponse(u);
    }

    @Override
    @Transactional
    public UsuarioResponse actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        u.setNombre(request.getNombre().trim());
        u.setApellido(request.getApellido().trim());
        u.setTelefono(request.getTelefono() != null ? request.getTelefono().trim() : null);
        usuarioRepository.save(u);
        return toResponse(u);
    }

    @Override
    @Transactional
    public String actualizarFoto(Long usuarioId, MultipartFile foto) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (foto == null || foto.isEmpty()) {
            throw new BadRequestException("No se envió ninguna imagen");
        }
        String contentType = foto.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("El archivo debe ser una imagen");
        }
        if (foto.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("La imagen no debe superar los 5 MB");
        }

        try {
            Path dir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(dir);

            String ext = obtenerExtension(foto.getOriginalFilename());
            String nombreArchivo = "user_" + usuarioId + "_" + UUID.randomUUID() + ext;
            Path destino = dir.resolve(nombreArchivo);
            Files.copy(foto.getInputStream(), destino);

            String url = baseUrl + "/uploads/avatars/" + nombreArchivo;
            u.setFotoUrl(url);
            usuarioRepository.save(u);
            return url;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar la imagen: " + e.getMessage());
        }
    }

    private String obtenerExtension(String nombre) {
        if (nombre == null) return ".png";
        int i = nombre.lastIndexOf('.');
        if (i < 0) return ".png";
        String ext = nombre.substring(i).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|gif|webp)") ? ext : ".png";
    }

    private UsuarioResponse toResponse(Usuario u) {
        return UsuarioResponse.builder()
                .id(u.getId())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .email(u.getEmail())
                .telefono(u.getTelefono())
                .fotoUrl(u.getFotoUrl())
                .build();
    }
}
