package com.compartix.backend.service.impl;

import com.compartix.backend.dto.request.ActualizarAlertasRequest;
import com.compartix.backend.dto.request.CrearGrupoRequest;
import com.compartix.backend.dto.response.GrupoResponse;
import com.compartix.backend.dto.response.SaldoGrupoResponse;
import com.compartix.backend.dto.response.UsuarioResponse;
import com.compartix.backend.entity.Grupo;
import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.entity.SaldoGrupo;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.exception.BadRequestException;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.*;
import com.compartix.backend.service.GrupoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrupoServiceImpl implements GrupoService {

    private final GrupoRepository grupoRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final UsuarioRepository usuarioRepository;
    private final KardexMensualRepository kardexMensualRepository;
    private final KardexRepository kardexRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final PagoProgramadoRepository pagoProgramadoRepository;
    private final MultaRepository multaRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoRepository pedidoRepository;
    private final NotificacionRepository notificacionRepository;
    private final MovimientoDetalleRepository movimientoDetalleRepository;
    private final MovimientoRepository movimientoRepository;
    private final CategoriaMovimientoRepository categoriaMovimientoRepository;
    private final com.compartix.backend.service.NotificacionService notificacionService;

    @Override
    @Transactional
    public GrupoResponse crearGrupo(CrearGrupoRequest request, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        String codigo = generarCodigoUnico();

        Grupo grupo = Grupo.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .codigoInvitacion(codigo)
                .cuotaBase(request.getCuotaBase())
                .moneda(request.getMoneda())
                .activo(true)
                .build();

        grupoRepository.save(grupo);

        // El creador es automáticamente DIRECTIVA
        GrupoMiembro miembro = GrupoMiembro.builder()
                .grupo(grupo)
                .usuario(usuario)
                .rol(RolGrupo.DIRECTIVA)
                .activo(true)
                .build();

        grupoMiembroRepository.save(miembro);

        // Crear saldo inicial del grupo
        SaldoGrupo saldo = SaldoGrupo.builder()
                .grupo(grupo)
                .totalIngresos(BigDecimal.ZERO)
                .totalEgresos(BigDecimal.ZERO)
                .totalMultas(BigDecimal.ZERO)
                .saldoDisponible(BigDecimal.ZERO)
                .build();

        saldoGrupoRepository.save(saldo);

        return toGrupoResponse(grupo);
    }

    @Override
    @Transactional
    public GrupoResponse unirseAGrupo(String codigoInvitacion, Long usuarioId) {
        Grupo grupo = grupoRepository.findByCodigoInvitacion(codigoInvitacion)
                .orElseThrow(() -> new ResourceNotFoundException("Código de invitación inválido"));

        if (!grupo.getActivo()) {
            throw new BadRequestException("Este grupo no está activo");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupo.getId(), usuarioId)) {
            throw new BadRequestException("Ya eres miembro de este grupo");
        }

        GrupoMiembro miembro = GrupoMiembro.builder()
                .grupo(grupo)
                .usuario(usuario)
                .rol(RolGrupo.MIEMBRO)
                .activo(true)
                .fechaIngreso(java.time.LocalDate.now())
                .build();

        grupoMiembroRepository.save(miembro);

        notificacionService.notificarNuevoMiembro(grupo, usuario, null);

        return toGrupoResponse(grupo);
    }

    @Override
    public GrupoResponse obtenerGrupo(Long grupoId) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));
        return toGrupoResponse(grupo);
    }

    @Override
    public List<GrupoResponse> obtenerGruposDeUsuario(Long usuarioId) {
        return grupoMiembroRepository.findByUsuarioIdAndActivoTrue(usuarioId)
                .stream()
                .map(gm -> toGrupoResponse(gm.getGrupo()))
                .collect(Collectors.toList());
    }

    @Override
    public SaldoGrupoResponse obtenerSaldoGrupo(Long grupoId) {
        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        SaldoGrupo saldo = saldoGrupoRepository.findByGrupoId(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo del grupo no encontrado"));

        return SaldoGrupoResponse.builder()
                .grupoId(grupoId)
                .nombreGrupo(grupo.getNombre())
                .totalIngresos(saldo.getTotalIngresos())
                .totalEgresos(saldo.getTotalEgresos())
                .totalMultas(saldo.getTotalMultas())
                .saldoDisponible(saldo.getSaldoDisponible())
                .saldoCarnaval(saldo.getSaldoCarnaval())
                .saldoAhorro(saldo.getSaldoAhorro())
                .actualizadoEn(saldo.getActualizadoEn())
                .build();
    }

    @Override
    @Transactional
    public void agregarMiembro(Long grupoId, Long usuarioId, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        if (grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new BadRequestException("El usuario ya es miembro del grupo");
        }

        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        GrupoMiembro miembro = GrupoMiembro.builder()
                .grupo(grupo)
                .usuario(usuario)
                .rol(RolGrupo.MIEMBRO)
                .activo(true)
                .fechaIngreso(java.time.LocalDate.now())
                .build();

        grupoMiembroRepository.save(miembro);

        notificacionService.notificarNuevoMiembro(grupo, usuario, solicitanteId);
    }

    @Override
    @Transactional
    public void quitarMiembro(Long grupoId, Long usuarioId, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        GrupoMiembro miembro = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no es miembro del grupo"));

        miembro.setActivo(false);
        grupoMiembroRepository.save(miembro);
    }

    // ---- Métodos privados ----

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        GrupoMiembro solicitante = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new UnauthorizedException("No eres miembro de este grupo"));

        if (solicitante.getRol() != RolGrupo.DIRECTIVA) {
            throw new UnauthorizedException("Solo la directiva puede realizar esta acción");
        }
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            codigo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (grupoRepository.existsByCodigoInvitacion(codigo));
        return codigo;
    }

    private GrupoResponse toGrupoResponse(Grupo grupo) {
        return GrupoResponse.builder()
                .id(grupo.getId())
                .nombre(grupo.getNombre())
                .descripcion(grupo.getDescripcion())
                .codigoInvitacion(grupo.getCodigoInvitacion())
                .cuotaBase(grupo.getCuotaBase())
                .moneda(grupo.getMoneda())
                .activo(grupo.getActivo())
                .alertaSaldoCarnaval(grupo.getAlertaSaldoCarnaval())
                .alertaSaldoAhorro(grupo.getAlertaSaldoAhorro())
                .creadoEn(grupo.getCreadoEn())
                .build();
    }
    @Override
    public String obtenerRolUsuario(Long grupoId, Long usuarioId) {
        return grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("No eres miembro de este grupo"))
                .getRol()
                .name();
    }
    @Override
    public List<UsuarioResponse> obtenerMiembros(Long grupoId, Long usuarioId) {
        validarMiembro(grupoId, usuarioId);
        return grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupoId)
                .stream()
                .map(this::toMiembroResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<UsuarioResponse> obtenerMiembrosGestion(Long grupoId, Long usuarioId) {
        // Solo la directiva ve también a los inactivos (para reactivarlos)
        validarDirectiva(grupoId, usuarioId);
        return grupoMiembroRepository.findByGrupoId(grupoId)
                .stream()
                .map(this::toMiembroResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    private UsuarioResponse toMiembroResponse(GrupoMiembro gm) {
        return UsuarioResponse.builder()
                .id(gm.getUsuario().getId())
                .nombre(gm.getUsuario().getNombre())
                .apellido(gm.getUsuario().getApellido())
                .email(gm.getUsuario().getEmail())
                .rol(gm.getRol().name())
                .activo(gm.getActivo())
                .participaCarnaval(gm.getParticipaCarnaval())
                .build();
    }

    private void validarMiembro(Long grupoId, Long usuarioId) {
        if (!grupoMiembroRepository.existsByGrupoIdAndUsuarioId(grupoId, usuarioId)) {
            throw new UnauthorizedException("No eres miembro de este grupo");
        }
    }
    @Override
    @Transactional
    public void cambiarRolMiembro(Long grupoId, Long usuarioId, String rol, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        GrupoMiembro miembro = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no es miembro del grupo"));

        miembro.setRol(RolGrupo.valueOf(rol));
        grupoMiembroRepository.save(miembro);
    }

    @Override
    @Transactional
    public void eliminarGrupo(Long grupoId, Long usuarioId) {
        validarDirectiva(grupoId, usuarioId);
        grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        // Eliminación en orden correcto para respetar FK constraints
        kardexMensualRepository.deleteByGrupoId(grupoId);
        kardexRepository.deleteByGrupoId(grupoId);
        cuotaProgramadaRepository.deleteByGrupoId(grupoId);
        pagoProgramadoRepository.deleteByGrupoId(grupoId);
        multaRepository.deleteByGrupoId(grupoId);
        pedidoDetalleRepository.deleteByGrupoId(grupoId);
        pedidoItemRepository.deleteByGrupoId(grupoId);
        pedidoRepository.deleteByGrupoId(grupoId);
        notificacionRepository.deleteByGrupoId(grupoId);
        movimientoDetalleRepository.deleteByGrupoId(grupoId);
        movimientoRepository.deleteByGrupoId(grupoId);
        categoriaMovimientoRepository.deleteByGrupoId(grupoId);
        grupoMiembroRepository.deleteByGrupoId(grupoId);
        saldoGrupoRepository.deleteByGrupoId(grupoId);
        grupoRepository.deleteById(grupoId);
    }

    @Override
    @Transactional
    public GrupoResponse actualizarAlertas(Long grupoId, ActualizarAlertasRequest request, Long solicitanteId) {
        validarDirectiva(grupoId, solicitanteId);

        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        grupo.setAlertaSaldoCarnaval(request.getAlertaSaldoCarnaval());
        grupo.setAlertaSaldoAhorro(request.getAlertaSaldoAhorro());
        grupoRepository.save(grupo);

        return toGrupoResponse(grupo);
    }
}