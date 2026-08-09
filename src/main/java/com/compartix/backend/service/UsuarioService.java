package com.compartix.backend.service;

import com.compartix.backend.dto.request.ActualizarPerfilRequest;
import com.compartix.backend.dto.request.CambiarPasswordRequest;
import com.compartix.backend.dto.response.UsuarioResponse;
import org.springframework.web.multipart.MultipartFile;

public interface UsuarioService {
    void cambiarPassword(Long usuarioId, CambiarPasswordRequest request);
    UsuarioResponse obtenerPerfil(Long usuarioId);
    UsuarioResponse actualizarPerfil(Long usuarioId, ActualizarPerfilRequest request);
    String actualizarFoto(Long usuarioId, MultipartFile foto);
}