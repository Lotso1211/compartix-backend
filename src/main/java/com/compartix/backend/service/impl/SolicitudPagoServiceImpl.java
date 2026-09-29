package com.compartix.backend.service.impl;

import com.compartix.backend.dto.response.SolicitudPagoResponse;
import com.compartix.backend.entity.CuotaProgramada;
import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.entity.Multa;
import com.compartix.backend.entity.SolicitudPago;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.CuotaProgramadaRepository;
import com.compartix.backend.repository.GrupoMiembroRepository;
import com.compartix.backend.repository.MultaRepository;
import com.compartix.backend.repository.SolicitudPagoRepository;
import com.compartix.backend.repository.UsuarioRepository;
import com.compartix.backend.service.NotificacionService;
import com.compartix.backend.service.PagoVerificacionService;
import com.compartix.backend.service.SolicitudPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitudPagoServiceImpl implements SolicitudPagoService {

    private static final String UPLOAD_DIR = "uploads/comprobantes";
    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private final SolicitudPagoRepository solicitudPagoRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final MultaRepository multaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final PagoVerificacionService pagoVerificacionService;
    private final NotificacionService notificacionService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public SolicitudPagoResponse subirComprobanteCuota(Long grupoId, Long cuotaId, Long usuarioId, MultipartFile comprobante) {
        validarMiembro(grupoId, usuarioId);

        CuotaProgramada cuota = cuotaProgramadaRepository.findById(cuotaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuota no encontrada"));

        if (!cuota.getGrupo().getId().equals(grupoId)) {
            throw new ResourceNotFoundException("Cuota no encontrada");
        }
        if (!cuota.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedException("Esta cuota no te pertenece");
        }
        if (!"PENDIENTE".equals(cuota.getEstado()) && !"VENCIDA".equals(cuota.getEstado())) {
            throw new BadRequestException("Esta cuota no está pendiente de pago");
        }
        if (solicitudPagoRepository.existsByCuotaIdAndEstado(cuotaId, "PENDIENTE")) {
            throw new BadRequestException("Ya tienes una solicitud pendiente para esta cuota");
        }

        String url = guardarComprobante(comprobante, "cuota_" + cuotaId);

        BigDecimal montoTotal = cuota.getMonto();
        if (Boolean.TRUE.equals(cuota.getMultaAplicada()) && cuota.getMontoMulta() != null) {
            montoTotal = montoTotal.add(cuota.getMontoMulta());
        }
        String descripcion = cuota.getPagoProgramado().getNombre()
                + " (" + getNombreMes(cuota.getMes()) + " " + cuota.getAnio() + ")";

        SolicitudPago solicitud = SolicitudPago.builder()
                .grupo(cuota.getGrupo())
                .usuario(cuota.getUsuario())
                .tipo("CUOTA")
                .cuota(cuota)
                .monto(montoTotal)
                .comprobanteUrl(url)
                .build();
        solicitudPagoRepository.save(solicitud);

        notificacionService.notificarSolicitudPago(cuota.getGrupo(), cuota.getUsuario(),
                "la cuota " + descripcion, montoTotal);

        return toResponse(solicitud, descripcion);
    }

    @Override
    @Transactional
    public SolicitudPagoResponse subirComprobanteMulta(Long grupoId, Long multaId, Long usuarioId, MultipartFile comprobante) {
        validarMiembro(grupoId, usuarioId);

        Multa multa = multaRepository.findById(multaId)
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada"));

        if (!multa.getGrupo().getId().equals(grupoId)) {
            throw new ResourceNotFoundException("Multa no encontrada");
        }
        if (!multa.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedException("Esta multa no te pertenece");
        }
        if (!"PENDIENTE".equals(multa.getEstado())) {
            throw new BadRequestException("Esta multa no está pendiente de pago");
        }
        if (solicitudPagoRepository.existsByMultaIdAndEstado(multaId, "PENDIENTE")) {
            throw new BadRequestException("Ya tienes una solicitud pendiente para esta multa");
        }

        String url = guardarComprobante(comprobante, "multa_" + multaId);

        SolicitudPago solicitud = SolicitudPago.builder()
                .grupo(multa.getGrupo())
                .usuario(multa.getUsuario())
                .tipo("MULTA")
                .multa(multa)
                .monto(multa.getMonto())
                .comprobanteUrl(url)
                .build();
        solicitudPagoRepository.save(solicitud);

        notificacionService.notificarSolicitudPago(multa.getGrupo(), multa.getUsuario(),
                "la multa \"" + multa.getMotivo() + "\"", multa.getMonto());

        return toResponse(solicitud, multa.getMotivo());
    }

    @Override
    @Transactional
    public List<SolicitudPagoResponse> listar(Long grupoId, Long solicitanteId, String estadoFiltro) {
        validarDirectiva(grupoId, solicitanteId);
        List<SolicitudPago> solicitudes = (estadoFiltro == null || estadoFiltro.isBlank())
                ? solicitudPagoRepository.findByGrupoIdOrderByCreadoEnDesc(grupoId)
                : solicitudPagoRepository.findByGrupoIdAndEstadoOrderByCreadoEnDesc(grupoId, estadoFiltro);
        return solicitudes.stream().map(s -> toResponse(s, descripcionDe(s))).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<SolicitudPagoResponse> misSolicitudes(Long grupoId, Long usuarioId) {
        return solicitudPagoRepository.findByUsuarioIdAndGrupoIdOrderByCreadoEnDesc(usuarioId, grupoId)
                .stream().map(s -> toResponse(s, descripcionDe(s))).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SolicitudPagoResponse aprobar(Long grupoId, Long solicitudId, Long directivaId) {
        validarDirectiva(grupoId, directivaId);
        SolicitudPago solicitud = obtenerSolicitud(grupoId, solicitudId);

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new BadRequestException("Esta solicitud ya fue revisada");
        }

        if ("CUOTA".equals(solicitud.getTipo())) {
            pagoVerificacionService.confirmarPagoCuota(grupoId, solicitud.getCuota().getId(),
                    directivaId, solicitud.getComprobanteUrl());
        } else {
            pagoVerificacionService.confirmarPagoMulta(grupoId, solicitud.getMulta().getId(), directivaId);
        }

        solicitud.setEstado("APROBADA");
        solicitud.setRevisadoPor(obtenerUsuario(directivaId));
        solicitud.setFechaRevision(LocalDateTime.now());
        solicitudPagoRepository.save(solicitud);

        return toResponse(solicitud, descripcionDe(solicitud));
    }

    @Override
    @Transactional
    public SolicitudPagoResponse rechazar(Long grupoId, Long solicitudId, Long directivaId, String motivo) {
        validarDirectiva(grupoId, directivaId);
        SolicitudPago solicitud = obtenerSolicitud(grupoId, solicitudId);

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new BadRequestException("Esta solicitud ya fue revisada");
        }

        solicitud.setEstado("RECHAZADA");
        solicitud.setMotivoRechazo(motivo);
        solicitud.setRevisadoPor(obtenerUsuario(directivaId));
        solicitud.setFechaRevision(LocalDateTime.now());
        solicitudPagoRepository.save(solicitud);

        String descripcion = descripcionDe(solicitud);
        notificacionService.notificarSolicitudRechazada(solicitud.getUsuario(), solicitud.getGrupo(),
                descripcion, motivo);

        return toResponse(solicitud, descripcion);
    }

    private SolicitudPago obtenerSolicitud(Long grupoId, Long solicitudId) {
        SolicitudPago solicitud = solicitudPagoRepository.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada"));
        if (!solicitud.getGrupo().getId().equals(grupoId)) {
            throw new ResourceNotFoundException("Solicitud no encontrada");
        }
        return solicitud;
    }

    private String descripcionDe(SolicitudPago s) {
        if ("CUOTA".equals(s.getTipo()) && s.getCuota() != null) {
            CuotaProgramada c = s.getCuota();
            return c.getPagoProgramado().getNombre() + " (" + getNombreMes(c.getMes()) + " " + c.getAnio() + ")";
        }
        if (s.getMulta() != null) {
            return s.getMulta().getMotivo();
        }
        return s.getTipo();
    }

    private String guardarComprobante(MultipartFile archivo, String prefijo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("No se envió ningún comprobante");
        }
        String contentType = archivo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("El comprobante debe ser una imagen");
        }
        if (archivo.getSize() > 10 * 1024 * 1024) {
            throw new BadRequestException("La imagen no debe superar los 10 MB");
        }
        try {
            Path dir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(dir);

            String ext = obtenerExtension(archivo.getOriginalFilename());
            String nombreArchivo = prefijo + "_" + UUID.randomUUID() + ext;
            Path destino = dir.resolve(nombreArchivo);
            Files.copy(archivo.getInputStream(), destino);

            return baseUrl + "/uploads/comprobantes/" + nombreArchivo;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar el comprobante: " + e.getMessage());
        }
    }

    private String obtenerExtension(String nombre) {
        if (nombre == null) return ".jpg";
        int i = nombre.lastIndexOf('.');
        if (i < 0) return ".jpg";
        String ext = nombre.substring(i).toLowerCase();
        return ext.matches("\\.(png|jpg|jpeg|gif|webp)") ? ext : ".jpg";
    }

    private String getNombreMes(Integer mes) {
        return (mes != null && mes >= 1 && mes <= 12) ? MESES[mes - 1] : String.valueOf(mes);
    }

    private Usuario obtenerUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        GrupoMiembro miembro = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new UnauthorizedException("No eres miembro de este grupo"));
        if (miembro.getRol().name().equals("MIEMBRO")) {
            throw new UnauthorizedException("Solo la directiva puede realizar esta acción");
        }
    }

    private void validarMiembro(Long grupoId, Long usuarioId) {
        if (!grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new UnauthorizedException("No eres miembro de este grupo");
        }
    }

    private SolicitudPagoResponse toResponse(SolicitudPago s, String descripcion) {
        return SolicitudPagoResponse.builder()
                .id(s.getId())
                .usuarioId(s.getUsuario().getId())
                .nombreUsuario(s.getUsuario().getNombre() + " " + s.getUsuario().getApellido())
                .tipo(s.getTipo())
                .cuotaId(s.getCuota() != null ? s.getCuota().getId() : null)
                .multaId(s.getMulta() != null ? s.getMulta().getId() : null)
                .descripcion(descripcion)
                .monto(s.getMonto())
                .comprobanteUrl(s.getComprobanteUrl())
                .estado(s.getEstado())
                .motivoRechazo(s.getMotivoRechazo())
                .revisadoPor(s.getRevisadoPor() != null
                        ? s.getRevisadoPor().getNombre() + " " + s.getRevisadoPor().getApellido() : null)
                .fechaRevision(s.getFechaRevision())
                .creadoEn(s.getCreadoEn())
                .build();
    }
}
