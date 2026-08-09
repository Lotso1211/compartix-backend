package com.compartix.backend.service.impl;

import com.compartix.backend.dto.response.GrupoResponse;
import com.compartix.backend.dto.response.KardexResponse;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.entity.Kardex;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.GrupoMiembroRepository;
import com.compartix.backend.repository.KardexRepository;
import com.compartix.backend.service.KardexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KardexServiceImpl implements KardexService {

    private final KardexRepository kardexRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;

    @Override
    public KardexResponse obtenerKardex(Long grupoId, Long usuarioId, Long solicitanteId) {
        // Un miembro solo puede ver su propio kardex
        // La directiva puede ver el de cualquiera
        boolean esDirectiva = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, solicitanteId)
                .map(gm -> gm.getRol() == RolGrupo.DIRECTIVA)
                .orElseThrow(() -> new UnauthorizedException("No eres miembro de este grupo"));

        if (!esDirectiva && !solicitanteId.equals(usuarioId)) {
            throw new UnauthorizedException("Solo puedes ver tu propio kardex");
        }

        Kardex kardex = kardexRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Kardex no encontrado"));

        return toKardexResponse(kardex);
    }

    @Override
    public List<KardexResponse> obtenerKardexGrupo(Long grupoId, Long solicitanteId) {
        // Solo la directiva puede ver el kardex de todos
        grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, solicitanteId)
                .filter(gm -> gm.getRol() == RolGrupo.DIRECTIVA)
                .orElseThrow(() -> new UnauthorizedException("Solo la directiva puede ver el kardex de todos los miembros"));

        return kardexRepository.findByGrupoId(grupoId)
                .stream()
                .map(this::toKardexResponse)
                .collect(Collectors.toList());
    }

    private KardexResponse toKardexResponse(Kardex kardex) {
        return KardexResponse.builder()
                .id(kardex.getId())
                .usuario(UsuarioResponse.builder()
                        .id(kardex.getUsuario().getId())
                        .nombre(kardex.getUsuario().getNombre())
                        .apellido(kardex.getUsuario().getApellido())
                        .email(kardex.getUsuario().getEmail())
                        .build())
                .grupo(GrupoResponse.builder()
                        .id(kardex.getGrupo().getId())
                        .nombre(kardex.getGrupo().getNombre())
                        .build())
                .totalAportes(kardex.getTotalAportes())
                .totalGastosCompartidos(kardex.getTotalGastosCompartidos())
                .totalGastosIndividuales(kardex.getTotalGastosIndividuales())
                .saldoActual(kardex.getSaldoActual())
                .actualizadoEn(kardex.getActualizadoEn())
                .totalMultas(kardex.getTotalMultas())
                .build();
    }
}