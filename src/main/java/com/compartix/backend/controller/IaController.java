package com.compartix.backend.controller;

import com.compartix.backend.dto.request.IaConsultaRequest;
import com.compartix.backend.dto.response.IaConsultaResponse;
import com.compartix.backend.dto.response.IaEscaneoResponse;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.GrupoMiembroRepository;
import com.compartix.backend.security.JwtUtil;
import com.compartix.backend.service.IaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/grupos/{grupoId}/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaService iaService;
    private final JwtUtil jwtUtil;
    private final GrupoMiembroRepository grupoMiembroRepository;

    @PostMapping("/consulta")
    public ResponseEntity<IaConsultaResponse> consulta(
            @PathVariable Long grupoId,
            @Valid @RequestBody IaConsultaRequest request,
            HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader("Authorization").substring(7);
        Long usuarioId = jwtUtil.extractUsuarioId(token);
        validarMiembro(grupoId, usuarioId);
        return ResponseEntity.ok(iaService.consultar(grupoId, usuarioId, request));
    }

    @PostMapping(value = "/escanear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IaEscaneoResponse> escanear(
            @PathVariable Long grupoId,
            @RequestParam("imagen") MultipartFile imagen,
            HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader("Authorization").substring(7);
        Long usuarioId = jwtUtil.extractUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);
        return ResponseEntity.ok(iaService.escanearFactura(grupoId, imagen));
    }

    private void validarMiembro(Long grupoId, Long usuarioId) {
        if (!grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new UnauthorizedException("No eres miembro de este grupo");
        }
    }

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        boolean esDirectiva = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .map(gm -> gm.getRol() == RolGrupo.DIRECTIVA)
                .orElse(false);
        if (!esDirectiva) {
            throw new UnauthorizedException("Solo la Directiva puede escanear y registrar facturas");
        }
    }
}