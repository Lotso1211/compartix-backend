package com.compartix.backend.service;

import com.compartix.backend.dto.request.IaConsultaRequest;
import com.compartix.backend.dto.response.IaConsultaResponse;
import com.compartix.backend.dto.response.IaEscaneoResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface IaService {
    IaConsultaResponse consultar(Long grupoId, Long usuarioId, IaConsultaRequest request);
    IaEscaneoResponse escanearFactura(Long grupoId, MultipartFile imagen);
    Map<String, Object> obtenerMetricas();
}