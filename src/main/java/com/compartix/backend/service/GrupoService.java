package com.compartix.backend.service;

import com.compartix.backend.dto.request.ActualizarAlertasRequest;
import com.compartix.backend.dto.request.CrearGrupoRequest;
import com.compartix.backend.dto.response.GrupoResponse;
import com.compartix.backend.dto.response.SaldoGrupoResponse;
import com.compartix.backend.dto.response.UsuarioResponse;

import java.util.List;

public interface GrupoService {
    GrupoResponse crearGrupo(CrearGrupoRequest request, Long usuarioId);
    GrupoResponse unirseAGrupo(String codigoInvitacion, Long usuarioId);
    GrupoResponse obtenerGrupo(Long grupoId);
    List<GrupoResponse> obtenerGruposDeUsuario(Long usuarioId);
    SaldoGrupoResponse obtenerSaldoGrupo(Long grupoId);
    void agregarMiembro(Long grupoId, Long usuarioId, Long solicitanteId);
    void quitarMiembro(Long grupoId, Long usuarioId, Long solicitanteId);
    String obtenerRolUsuario(Long grupoId, Long usuarioId);
    List<UsuarioResponse> obtenerMiembros(Long grupoId, Long usuarioId);
    List<UsuarioResponse> obtenerMiembrosGestion(Long grupoId, Long usuarioId);
    void cambiarRolMiembro(Long grupoId, Long usuarioId, String rol, Long solicitanteId);
    void eliminarGrupo(Long grupoId, Long usuarioId);
    GrupoResponse actualizarAlertas(Long grupoId, ActualizarAlertasRequest request, Long solicitanteId);
}