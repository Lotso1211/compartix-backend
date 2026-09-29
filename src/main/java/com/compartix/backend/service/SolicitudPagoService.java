package com.compartix.backend.service;

import com.compartix.backend.dto.response.SolicitudPagoResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SolicitudPagoService {

    SolicitudPagoResponse subirComprobanteCuota(Long grupoId, Long cuotaId, Long usuarioId, MultipartFile comprobante);

    SolicitudPagoResponse subirComprobanteMulta(Long grupoId, Long multaId, Long usuarioId, MultipartFile comprobante);

    /** Solo directiva. estadoFiltro es opcional (null = todas). */
    List<SolicitudPagoResponse> listar(Long grupoId, Long solicitanteId, String estadoFiltro);

    List<SolicitudPagoResponse> misSolicitudes(Long grupoId, Long usuarioId);

    SolicitudPagoResponse aprobar(Long grupoId, Long solicitudId, Long directivaId);

    SolicitudPagoResponse rechazar(Long grupoId, Long solicitudId, Long directivaId, String motivo);
}
