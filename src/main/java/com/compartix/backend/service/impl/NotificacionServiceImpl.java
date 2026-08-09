package com.compartix.backend.service.impl;

import com.compartix.backend.dto.response.NotificacionResponse;
import com.compartix.backend.entity.CuotaProgramada;
import com.compartix.backend.entity.Grupo;
import com.compartix.backend.entity.GrupoMiembro;
import com.compartix.backend.entity.Notificacion;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.enums.TipoNotificacion;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.CuotaProgramadaRepository;
import com.compartix.backend.repository.GrupoMiembroRepository;
import com.compartix.backend.repository.NotificacionRepository;
import com.compartix.backend.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final CuotaProgramadaRepository cuotaProgramadaRepository;
    private final com.compartix.backend.service.MailService mailService;

    // Días antes/después del cierre de mes en los que avisamos "cuota por vencer".
    private static final int DIAS_GRACIA = 5;          // multa se activa 5 días tras fin de mes
    private static final int VENTANA_AVISO = 5;        // avisamos desde 5 días antes del fin de mes

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    @Override
    @Transactional
    public List<NotificacionResponse> listar(Long usuarioId) {
        generarRecordatoriosCuotas(usuarioId);
        return notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public long contarNoLeidas(Long usuarioId) {
        generarRecordatoriosCuotas(usuarioId);
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    @Override
    @Transactional
    public void marcarLeida(Long usuarioId, Long notificacionId) {
        Notificacion n = notificacionRepository.findById(notificacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));
        if (!n.getUsuario().getId().equals(usuarioId)) {
            throw new UnauthorizedException("Esta notificación no te pertenece");
        }
        n.setLeida(true);
        notificacionRepository.save(n);
    }

    @Override
    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        List<Notificacion> noLeidas =
                notificacionRepository.findByUsuarioIdAndLeidaFalseOrderByCreadoEnDesc(usuarioId);
        noLeidas.forEach(n -> n.setLeida(true));
        notificacionRepository.saveAll(noLeidas);
    }

    @Override
    @Transactional
    public void notificarMulta(Usuario usuario, Grupo grupo, BigDecimal monto, String motivo) {
        crearYEnviar(usuario, grupo, TipoNotificacion.MULTA, "⚠️ Nueva multa registrada",
                "Se te aplicó una multa de " + monto + " " + grupo.getMoneda()
                        + " en " + grupo.getNombre()
                        + (motivo != null && !motivo.isBlank() ? " — Motivo: " + motivo : "") + ".");
    }

    @Override
    @Transactional
    public void notificarMoraCuota(Usuario usuario, Grupo grupo, BigDecimal monto, String mesNombre, Integer anio) {
        crearYEnviar(usuario, grupo, TipoNotificacion.MORA_CUOTA, "⏰ Mora aplicada por cuota vencida",
                "No pagaste a tiempo tu cuota de " + mesNombre + " " + anio + " en " + grupo.getNombre()
                        + ": se aplicó una mora de " + monto + " " + grupo.getMoneda() + ".");
    }

    @Override
    @Transactional
    public void notificarCuotaPagada(Usuario usuario, Grupo grupo, BigDecimal monto, String mesNombre, Integer anio) {
        crearYEnviar(usuario, grupo, TipoNotificacion.APORTE_REGISTRADO, "✅ Cuota pagada",
                "Se registró el pago de tu cuota de " + mesNombre + " " + anio + " en " + grupo.getNombre()
                        + ": " + monto + " " + grupo.getMoneda() + ".");
    }

    @Override
    @Transactional
    public void notificarAporte(Usuario usuario, Grupo grupo, BigDecimal monto, String descripcion) {
        crearYEnviar(usuario, grupo, TipoNotificacion.APORTE_REGISTRADO, "💰 Nuevo aporte registrado",
                "Se registró un aporte de " + monto + " " + grupo.getMoneda() + " a tu nombre en "
                        + grupo.getNombre()
                        + (descripcion != null && !descripcion.isBlank() ? " — " + descripcion : "") + ".");
    }

    @Override
    @Transactional
    public void notificarGasto(Usuario usuario, Grupo grupo, String descripcion, BigDecimal montoUsuario) {
        crearYEnviar(usuario, grupo, TipoNotificacion.GASTO, "🧾 Nuevo gasto registrado",
                "Se registró el gasto \"" + descripcion + "\" en " + grupo.getNombre()
                        + " — tu parte: " + montoUsuario + " " + grupo.getMoneda() + ".");
    }

    @Override
    @Transactional
    public void notificarReactivacion(Usuario usuario, Grupo grupo, BigDecimal montoTotal) {
        crearYEnviar(usuario, grupo, TipoNotificacion.GRUPO, "🔄 Reactivación completada",
                "Te reactivaste en " + grupo.getNombre() + ". Se registró tu pago de " + montoTotal
                        + " " + grupo.getMoneda() + " para ponerte al día.");
    }

    @Override
    @Transactional
    public void notificarFondoBajo(Grupo grupo, String nombreFondo, BigDecimal saldoActual, BigDecimal umbral) {
        String titulo = "🔻 Saldo bajo en Fondo " + nombreFondo;
        String mensaje = "El Fondo " + nombreFondo + " de " + grupo.getNombre() + " bajó a "
                + saldoActual + " " + grupo.getMoneda() + ", por debajo del umbral configurado ("
                + umbral + " " + grupo.getMoneda() + ").";
        for (GrupoMiembro directiva : grupoMiembroRepository
                .findByGrupoIdAndRolAndActivoTrue(grupo.getId(), RolGrupo.DIRECTIVA)) {
            crearYEnviar(directiva.getUsuario(), grupo, TipoNotificacion.FONDO_BAJO, titulo, mensaje);
        }
    }

    @Override
    @Transactional
    public void notificarNuevoMiembro(Grupo grupo, Usuario nuevoMiembro, Long excluirUsuarioId) {
        String titulo = "👥 Nuevo miembro en el grupo";
        String mensaje = nuevoMiembro.getNombre() + " " + nuevoMiembro.getApellido()
                + " se unió a " + grupo.getNombre() + ".";
        for (GrupoMiembro directiva : grupoMiembroRepository
                .findByGrupoIdAndRolAndActivoTrue(grupo.getId(), RolGrupo.DIRECTIVA)) {
            if (excluirUsuarioId != null && directiva.getUsuario().getId().equals(excluirUsuarioId)) {
                continue;
            }
            crearYEnviar(directiva.getUsuario(), grupo, TipoNotificacion.GRUPO, titulo, mensaje);
        }
    }

    @Override
    @Transactional
    public void notificarMiembroAgregadoAPago(Usuario usuario, Grupo grupo, String nombrePago) {
        crearYEnviar(usuario, grupo, TipoNotificacion.GRUPO, "📋 Añadido a un pago programado",
                "Se te añadió al pago programado \"" + nombrePago + "\" en " + grupo.getNombre()
                        + " — se generaron tus cuotas pendientes.");
    }

    @Override
    @Transactional
    public void notificarPagoProgramadoFinalizado(Grupo grupo, String nombrePago) {
        String titulo = "🏁 Pago programado finalizado";
        String mensaje = "El pago programado \"" + nombrePago + "\" en " + grupo.getNombre() + " finalizó.";
        for (GrupoMiembro miembro : grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupo.getId())) {
            crearYEnviar(miembro.getUsuario(), grupo, TipoNotificacion.GRUPO, titulo, mensaje);
        }
    }

    @Override
    @Transactional
    public void notificarPedidoCreado(Usuario usuario, Grupo grupo, String nombrePedido, BigDecimal monto) {
        crearYEnviar(usuario, grupo, TipoNotificacion.PEDIDO, "🛒 Nuevo pedido registrado",
                "Se creó el pedido \"" + nombrePedido + "\" en " + grupo.getNombre()
                        + " — tu parte: " + monto + " " + grupo.getMoneda() + ".");
    }

    @Override
    @Transactional
    public void notificarPedidoCerrado(Usuario usuario, Grupo grupo, String nombrePedido, BigDecimal monto) {
        crearYEnviar(usuario, grupo, TipoNotificacion.PEDIDO, "✅ Pedido cerrado",
                "El pedido \"" + nombrePedido + "\" en " + grupo.getNombre() + " se cerró — tu parte: "
                        + monto + " " + grupo.getMoneda() + ".");
    }

    private void crearYEnviar(Usuario usuario, Grupo grupo, TipoNotificacion tipo, String titulo, String mensaje) {
        Notificacion n = Notificacion.builder()
                .usuario(usuario)
                .grupo(grupo)
                .titulo(titulo)
                .mensaje(mensaje)
                .tipo(tipo)
                .leida(false)
                .build();
        notificacionRepository.save(n);
        mailService.enviar(usuario.getEmail(), n.getTitulo(), n.getMensaje());
    }

    // ─────────────────────────────────────────────────────────
    //  Generación on-demand de recordatorios de cuotas por vencer
    // ─────────────────────────────────────────────────────────

    private void generarRecordatoriosCuotas(Long usuarioId) {
        LocalDate hoy = LocalDate.now();
        List<GrupoMiembro> membresias = grupoMiembroRepository.findByUsuarioIdAndActivoTrue(usuarioId);

        for (GrupoMiembro gm : membresias) {
            Grupo grupo = gm.getGrupo();
            List<CuotaProgramada> cuotas =
                    cuotaProgramadaRepository.findByUsuarioIdAndGrupoId(usuarioId, grupo.getId());

            for (CuotaProgramada c : cuotas) {
                if (!"PENDIENTE".equals(c.getEstado())) continue;

                LocalDate finDeMes = LocalDate.of(c.getAnio(), c.getMes(), 1)
                        .withDayOfMonth(LocalDate.of(c.getAnio(), c.getMes(), 1).lengthOfMonth());
                LocalDate fechaLimite = finDeMes.plusDays(DIAS_GRACIA);
                LocalDate inicioAviso = finDeMes.minusDays(VENTANA_AVISO);

                // Solo avisamos dentro de la ventana [finMes-5 , finMes+5] y sin haber vencido aún
                boolean enVentana = !hoy.isBefore(inicioAviso) && !hoy.isAfter(fechaLimite);
                if (!enVentana) continue;

                String mesNombre = MESES[c.getMes() - 1];
                String titulo = "📅 Cuota de " + mesNombre + " " + c.getAnio() + " por vencer";

                if (notificacionRepository.existsByUsuarioIdAndTituloAndTipo(
                        usuarioId, titulo, TipoNotificacion.RECORDATORIO)) {
                    continue; // ya existe, no duplicar
                }

                Notificacion n = Notificacion.builder()
                        .usuario(gm.getUsuario())
                        .grupo(grupo)
                        .titulo(titulo)
                        .mensaje("Tu cuota \"" + c.getPagoProgramado().getNombre() + "\" de "
                                + c.getMonto() + " " + grupo.getMoneda() + " en " + grupo.getNombre()
                                + " vence el " + fechaLimite + ". Evita la multa pagando a tiempo.")
                        .tipo(TipoNotificacion.RECORDATORIO)
                        .leida(false)
                        .build();
                notificacionRepository.save(n);
                mailService.enviar(gm.getUsuario().getEmail(), n.getTitulo(), n.getMensaje());
            }
        }
    }

    private NotificacionResponse toResponse(Notificacion n) {
        return NotificacionResponse.builder()
                .id(n.getId())
                .titulo(n.getTitulo())
                .mensaje(n.getMensaje())
                .tipo(n.getTipo().name())
                .leida(n.getLeida())
                .grupoId(n.getGrupo() != null ? n.getGrupo().getId() : null)
                .nombreGrupo(n.getGrupo() != null ? n.getGrupo().getNombre() : null)
                .creadoEn(n.getCreadoEn())
                .build();
    }
}
