package com.compartix.backend.service;

import com.compartix.backend.dto.response.NotificacionResponse;
import com.compartix.backend.entity.Grupo;
import com.compartix.backend.entity.Usuario;

import java.math.BigDecimal;
import java.util.List;

public interface NotificacionService {

    /** Lista las notificaciones del usuario (genera recordatorios pendientes primero). */
    List<NotificacionResponse> listar(Long usuarioId);

    /** Cantidad de notificaciones no leídas (para el badge del navbar). */
    long contarNoLeidas(Long usuarioId);

    void marcarLeida(Long usuarioId, Long notificacionId);

    void marcarTodasLeidas(Long usuarioId);

    /** Hook usado al aplicar una multa manual. */
    void notificarMulta(Usuario usuario, Grupo grupo, BigDecimal monto, String motivo);

    /** Hook usado al aplicar mora automática por cuota vencida (distinto de multa manual). */
    void notificarMoraCuota(Usuario usuario, Grupo grupo, BigDecimal monto, String mesNombre, Integer anio);

    /** Hook usado al marcar una cuota programada como pagada. */
    void notificarCuotaPagada(Usuario usuario, Grupo grupo, BigDecimal monto, String mesNombre, Integer anio);

    /** Hook usado al registrar un aporte suelto a nombre de un miembro. */
    void notificarAporte(Usuario usuario, Grupo grupo, BigDecimal monto, String descripcion);

    /** Hook usado al registrar un gasto (compartido o individual) que afecta a un miembro. */
    void notificarGasto(Usuario usuario, Grupo grupo, String descripcion, BigDecimal montoUsuario);

    /** Hook usado al reactivar un miembro y registrar su "ponerse al día". */
    void notificarReactivacion(Usuario usuario, Grupo grupo, BigDecimal montoTotal);

    /** Hook usado cuando el saldo de un fondo cruza por debajo del umbral configurado. Avisa a la Directiva. */
    void notificarFondoBajo(Grupo grupo, String nombreFondo, BigDecimal saldoActual, BigDecimal umbral);

    /** Hook usado cuando un nuevo miembro se une al grupo. Avisa a la Directiva (excepto a quien lo agregó, si aplica). */
    void notificarNuevoMiembro(Grupo grupo, Usuario nuevoMiembro, Long excluirUsuarioId);

    /** Hook usado al añadir un miembro a un pago programado ya en curso. */
    void notificarMiembroAgregadoAPago(Usuario usuario, Grupo grupo, String nombrePago);

    /** Hook usado al finalizar un pago programado. Avisa a todos los miembros activos del grupo. */
    void notificarPagoProgramadoFinalizado(Grupo grupo, String nombrePago);

    /** Hook usado al crear un pedido, por cada miembro con un subtotal asignado. */
    void notificarPedidoCreado(Usuario usuario, Grupo grupo, String nombrePedido, BigDecimal monto);

    /** Hook usado al cerrar un pedido, por cada miembro con un subtotal asignado. */
    void notificarPedidoCerrado(Usuario usuario, Grupo grupo, String nombrePedido, BigDecimal monto);
}
