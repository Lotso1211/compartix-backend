package com.compartix.backend.service;

import com.compartix.backend.dto.response.KardexResponse;

import java.util.List;

public interface KardexService {
    KardexResponse obtenerKardex(Long grupoId, Long usuarioId, Long solicitanteId);
    List<KardexResponse> obtenerKardexGrupo(Long grupoId, Long solicitanteId);
}