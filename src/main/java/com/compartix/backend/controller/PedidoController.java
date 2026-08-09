package com.compartix.backend.controller;

import com.compartix.backend.dto.request.CrearPedidoRequest;
import com.compartix.backend.dto.response.PedidoResponse;
import com.compartix.backend.entity.*;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.*;
import com.compartix.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grupos/{grupoId}/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final GrupoRepository grupoRepository;
    private final GrupoMiembroRepository grupoMiembroRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final KardexRepository kardexRepository;
    private final SaldoGrupoRepository saldoGrupoRepository;
    private final com.compartix.backend.service.NotificacionService notificacionService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @PathVariable Long grupoId,
            @RequestBody CrearPedidoRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Grupo grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        Pedido pedido = Pedido.builder()
                .grupo(grupo)
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .fecha(request.getFecha())
                .estado("ABIERTO")
                .build();
        pedidoRepository.save(pedido);

        Map<Long, BigDecimal> totalPorUsuario = new HashMap<>();
        Map<Long, Usuario> usuariosPorId = new java.util.HashMap<>();

        for (CrearPedidoRequest.ItemRequest itemReq : request.getItems()) {
            PedidoItem item = PedidoItem.builder()
                    .pedido(pedido)
                    .nombre(itemReq.getNombre())
                    .precioUnitario(itemReq.getPrecioUnitario())
                    .descripcion(itemReq.getDescripcion())
                    .build();
            pedidoItemRepository.save(item);

            if (itemReq.getCantidadesPorUsuario() != null) {
                for (var entry : itemReq.getCantidadesPorUsuario().entrySet()) {
                    Usuario usuario = usuarioRepository.findById(entry.getKey())
                            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                    BigDecimal subtotal = itemReq.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(entry.getValue()));
                    PedidoDetalle detalle = PedidoDetalle.builder()
                            .pedido(pedido)
                            .item(item)
                            .usuario(usuario)
                            .cantidad(entry.getValue())
                            .subtotal(subtotal)
                            .build();
                    pedidoDetalleRepository.save(detalle);

                    totalPorUsuario.merge(usuario.getId(), subtotal, BigDecimal::add);
                    usuariosPorId.put(usuario.getId(), usuario);
                }
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : totalPorUsuario.entrySet()) {
            notificacionService.notificarPedidoCreado(usuariosPorId.get(entry.getKey()), grupo,
                    pedido.getNombre(), entry.getValue());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toPedidoResponse(pedido, false));
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponse>> obtenerPedidos(
            @PathVariable Long grupoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);
        boolean esDirectiva = esDirectiva(grupoId, usuarioId);

        List<PedidoResponse> pedidos = pedidoRepository
                .findByGrupoIdOrderByCreadoEnDesc(grupoId)
                .stream()
                .map(p -> toPedidoResponse(p, !esDirectiva ? usuarioId : null))
                .collect(Collectors.toList());

        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<PedidoResponse> obtenerPedido(
            @PathVariable Long grupoId,
            @PathVariable Long pedidoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarMiembro(grupoId, usuarioId);
        boolean esDirectiva = esDirectiva(grupoId, usuarioId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        return ResponseEntity.ok(toPedidoResponse(pedido, !esDirectiva ? usuarioId : null));
    }

    @PutMapping("/{pedidoId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<PedidoResponse> actualizarPedido(
            @PathVariable Long grupoId,
            @PathVariable Long pedidoId,
            @RequestBody CrearPedidoRequest request,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (!"ABIERTO".equals(pedido.getEstado())) {
            throw new com.compartix.backend.exception.BadRequestException(
                    "Solo se puede editar un pedido abierto");
        }

        pedido.setNombre(request.getNombre());
        pedido.setDescripcion(request.getDescripcion());
        if (request.getFecha() != null) {
            pedido.setFecha(request.getFecha());
        }
        pedidoRepository.save(pedido);

        // Reemplazar items y detalles por los nuevos (el pedido sigue abierto,
        // así que nada se registró aún en kardex/saldo).
        pedidoDetalleRepository.deleteAll(pedidoDetalleRepository.findByPedidoId(pedidoId));
        pedidoItemRepository.deleteAll(pedidoItemRepository.findByPedidoId(pedidoId));
        pedidoDetalleRepository.flush();
        pedidoItemRepository.flush();

        for (CrearPedidoRequest.ItemRequest itemReq : request.getItems()) {
            PedidoItem item = PedidoItem.builder()
                    .pedido(pedido)
                    .nombre(itemReq.getNombre())
                    .precioUnitario(itemReq.getPrecioUnitario())
                    .descripcion(itemReq.getDescripcion())
                    .build();
            pedidoItemRepository.save(item);

            if (itemReq.getCantidadesPorUsuario() != null) {
                for (var entry : itemReq.getCantidadesPorUsuario().entrySet()) {
                    Usuario usuario = usuarioRepository.findById(entry.getKey())
                            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                    BigDecimal subtotal = itemReq.getPrecioUnitario()
                            .multiply(BigDecimal.valueOf(entry.getValue()));
                    PedidoDetalle detalle = PedidoDetalle.builder()
                            .pedido(pedido)
                            .item(item)
                            .usuario(usuario)
                            .cantidad(entry.getValue())
                            .subtotal(subtotal)
                            .build();
                    pedidoDetalleRepository.save(detalle);
                }
            }
        }

        return ResponseEntity.ok(toPedidoResponse(pedido, (Long) null));
    }

    @PatchMapping("/{pedidoId}/cerrar")
    public ResponseEntity<Void> cerrarPedido(
            @PathVariable Long grupoId,
            @PathVariable Long pedidoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if ("CERRADO".equals(pedido.getEstado())) {
            return ResponseEntity.badRequest().build();
        }

        pedido.setEstado("CERRADO");
        pedidoRepository.save(pedido);

        // Calcular totales por usuario y actualizar kardex
        List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedidoId);

        Map<Long, BigDecimal> totalPorUsuario = new HashMap<>();
        Map<Long, Usuario> usuariosPorId = new HashMap<>();
        for (PedidoDetalle detalle : detalles) {
            Long uid = detalle.getUsuario().getId();
            totalPorUsuario.merge(uid, detalle.getSubtotal(), BigDecimal::add);
            usuariosPorId.put(uid, detalle.getUsuario());
        }

        BigDecimal totalGeneral = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : totalPorUsuario.entrySet()) {
            totalGeneral = totalGeneral.add(entry.getValue());
            kardexRepository.findByGrupoIdAndUsuarioId(grupoId, entry.getKey())
                    .ifPresent(kardex -> {
                        kardex.setTotalGastosIndividuales(
                                kardex.getTotalGastosIndividuales().add(entry.getValue()));
                        kardex.setSaldoActual(
                                kardex.getSaldoActual().subtract(entry.getValue()));
                        kardexRepository.save(kardex);
                    });
        }

        // Descontar del saldo del grupo
        BigDecimal montoFinal = totalGeneral;
        saldoGrupoRepository.findByGrupoId(grupoId).ifPresent(saldo -> {
            saldo.setTotalEgresos(saldo.getTotalEgresos().add(montoFinal));
            saldo.setSaldoDisponible(saldo.getSaldoDisponible().subtract(montoFinal));
            saldoGrupoRepository.save(saldo);
        });

        for (Map.Entry<Long, BigDecimal> entry : totalPorUsuario.entrySet()) {
            notificacionService.notificarPedidoCerrado(usuariosPorId.get(entry.getKey()), pedido.getGrupo(),
                    pedido.getNombre(), entry.getValue());
        }

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{pedidoId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> eliminarPedido(
            @PathVariable Long grupoId,
            @PathVariable Long pedidoId,
            @RequestHeader("Authorization") String token) {
        Long usuarioId = extraerUsuarioId(token);
        validarDirectiva(grupoId, usuarioId);

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        // Un pedido CERRADO ya descontó del kardex y del saldo del grupo:
        // al eliminarlo hay que devolver esos montos para no dejar plata "fantasma".
        if ("CERRADO".equals(pedido.getEstado())) {
            List<PedidoDetalle> detalles = pedidoDetalleRepository.findByPedidoId(pedidoId);

            Map<Long, BigDecimal> totalPorUsuario = new HashMap<>();
            for (PedidoDetalle detalle : detalles) {
                totalPorUsuario.merge(detalle.getUsuario().getId(),
                        detalle.getSubtotal(), BigDecimal::add);
            }

            BigDecimal totalGeneral = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal> entry : totalPorUsuario.entrySet()) {
                totalGeneral = totalGeneral.add(entry.getValue());
                kardexRepository.findByGrupoIdAndUsuarioId(grupoId, entry.getKey())
                        .ifPresent(kardex -> {
                            kardex.setTotalGastosIndividuales(
                                    kardex.getTotalGastosIndividuales().subtract(entry.getValue()));
                            kardex.setSaldoActual(
                                    kardex.getSaldoActual().add(entry.getValue()));
                            kardexRepository.save(kardex);
                        });
            }

            BigDecimal montoFinal = totalGeneral;
            saldoGrupoRepository.findByGrupoId(grupoId).ifPresent(saldo -> {
                saldo.setTotalEgresos(saldo.getTotalEgresos().subtract(montoFinal));
                saldo.setSaldoDisponible(saldo.getSaldoDisponible().add(montoFinal));
                saldoGrupoRepository.save(saldo);
            });
        }

        pedidoDetalleRepository.deleteAll(pedidoDetalleRepository.findByPedidoId(pedidoId));
        pedidoItemRepository.deleteAll(pedidoItemRepository.findByPedidoId(pedidoId));
        pedidoRepository.delete(pedido);
        return ResponseEntity.noContent().build();
    }

    private PedidoResponse toPedidoResponse(Pedido pedido, Long filtrarPorUsuario) {
        List<PedidoItem> items = pedidoItemRepository.findByPedidoId(pedido.getId());

        List<PedidoResponse.ItemResponse> itemResponses = items.stream().map(item -> {
            List<PedidoDetalle> detalles = filtrarPorUsuario != null
                    ? pedidoDetalleRepository.findByPedidoIdAndUsuarioId(pedido.getId(), filtrarPorUsuario)
                    .stream().filter(d -> d.getItem().getId().equals(item.getId())).collect(Collectors.toList())
                    : pedidoDetalleRepository.findByPedidoId(pedido.getId())
                    .stream().filter(d -> d.getItem().getId().equals(item.getId())).collect(Collectors.toList());

            BigDecimal totalItem = detalles.stream()
                    .map(PedidoDetalle::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<PedidoResponse.DetalleResponse> detalleResponses = detalles.stream()
                    .map(d -> PedidoResponse.DetalleResponse.builder()
                            .usuarioId(d.getUsuario().getId())
                            .nombreUsuario(d.getUsuario().getNombre() + " " + d.getUsuario().getApellido())
                            .cantidad(d.getCantidad())
                            .subtotal(d.getSubtotal())
                            .build())
                    .collect(Collectors.toList());

            return PedidoResponse.ItemResponse.builder()
                    .id(item.getId())
                    .nombre(item.getNombre())
                    .precioUnitario(item.getPrecioUnitario())
                    .descripcion(item.getDescripcion())
                    .detalles(detalleResponses)
                    .totalItem(totalItem)
                    .build();
        }).collect(Collectors.toList());

        BigDecimal totalGeneral = itemResponses.stream()
                .map(PedidoResponse.ItemResponse::getTotalItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PedidoResponse.builder()
                .id(pedido.getId())
                .nombre(pedido.getNombre())
                .descripcion(pedido.getDescripcion())
                .estado(pedido.getEstado())
                .fecha(pedido.getFecha())
                .items(itemResponses)
                .totalGeneral(totalGeneral)
                .build();
    }

    private Long extraerUsuarioId(String token) {
        return jwtUtil.extractUsuarioId(token.substring(7));
    }

    private void validarDirectiva(Long grupoId, Long usuarioId) {
        GrupoMiembro miembro = grupoMiembroRepository
                .findByGrupoIdAndUsuarioId(grupoId, usuarioId)
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

    private boolean esDirectiva(Long grupoId, Long usuarioId) {
        return grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, usuarioId)
                .map(m -> m.getRol().name().equals("DIRECTIVA"))
                .orElse(false);
    }

    private PedidoResponse toPedidoResponse(Pedido pedido, boolean soloMio) {
        return toPedidoResponse(pedido, (Long) null);
    }
}