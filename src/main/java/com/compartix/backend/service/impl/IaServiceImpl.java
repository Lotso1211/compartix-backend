package com.compartix.backend.service.impl;

import com.compartix.backend.dto.request.*;
import com.compartix.backend.dto.response.*;
import com.compartix.backend.entity.Usuario;
import com.compartix.backend.enums.RolGrupo;
import com.compartix.backend.exception.ResourceNotFoundException;
import com.compartix.backend.exception.UnauthorizedException;
import com.compartix.backend.repository.*;
import com.compartix.backend.service.IaService;
import com.compartix.backend.service.MovimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaServiceImpl implements IaService {

    private final GrupoRepository            grupoRepository;
    private final GrupoMiembroRepository     grupoMiembroRepository;
    private final KardexRepository           kardexRepository;
    private final SaldoGrupoRepository       saldoGrupoRepository;
    private final UsuarioRepository          usuarioRepository;
    private final CuotaProgramadaRepository  cuotaProgramadaRepository;
    private final MovimientoService          movimientoService;
    private final RestTemplate               restTemplate;

    @Value("${ia.service.url:http://localhost:5000}")
    private String IA_URL;

    // ─────────────────────────────────────────────────────────
    //  Consulta IA (chat)
    // ─────────────────────────────────────────────────────────

    @Override
    public IaConsultaResponse consultar(Long grupoId, Long usuarioId, IaConsultaRequest request) {
        var grupo = grupoRepository.findById(grupoId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado"));

        Map<String, Object> contexto = construirContexto(grupoId, grupo.getMoneda(),
                grupo.getNombre(), usuarioId);

        Map<String, Object> body = new HashMap<>();
        body.put("pregunta", request.getPregunta());
        body.put("contexto", contexto);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    IA_URL + "/consulta",
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            assert response != null;
            boolean esAccion  = Boolean.TRUE.equals(response.get("esAccion"));
            String  intencion = (String) response.get("intencion");
            String  respuesta = (String) response.get("respuesta");
            double  confianza = ((Number) response.get("confianza")).doubleValue();

            var builder = IaConsultaResponse.builder()
                    .intencion(intencion)
                    .confianza(confianza)
                    .esAccion(esAccion);

            if (esAccion) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entidades =
                        (Map<String, Object>) response.getOrDefault("entidades", Map.of());
                return procesarAccion(grupoId, usuarioId, intencion, entidades,
                        grupo.getMoneda(), builder);
            }
            return builder.respuesta(respuesta).build();

        } catch (Exception e) {
            log.error("Error llamando al servicio IA: {}", e.getMessage());
            return IaConsultaResponse.builder()
                    .respuesta("⚠️ El servicio de IA no está disponible en este momento. " +
                               "Verifica que el microservicio esté corriendo en el puerto 5000.")
                    .intencion("ERROR")
                    .confianza(0.0)
                    .esAccion(false)
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Procesamiento de acciones de registro
    // ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private IaConsultaResponse procesarAccion(Long grupoId, Long usuarioId, String intencion,
                                               Map<String, Object> entidades, String moneda,
                                               IaConsultaResponse.IaConsultaResponseBuilder builder) {
        Double monto = entidades.get("monto") != null
                ? ((Number) entidades.get("monto")).doubleValue() : null;
        List<String> nombres = entidades.get("nombres") != null
                ? (List<String>) entidades.get("nombres") : Collections.emptyList();
        boolean todos    = Boolean.TRUE.equals(entidades.get("todos"));
        String  fechaStr = (String) entidades.get("fecha");
        String  desc     = (String) entidades.getOrDefault("descripcion", "Registrado por IA");

        // Validar monto
        if (monto == null || monto <= 0) {
            return builder.respuesta(
                    "❓ No pude identificar el monto. " +
                    "Ejemplo: *'Juan pagó **50 Bs**'* o *'Gastamos **100** entre todos'*"
            ).build();
        }

        LocalDate   fecha        = parseFecha(fechaStr);
        BigDecimal  montoDecimal = BigDecimal.valueOf(monto)
                                             .setScale(2, BigDecimal.ROUND_HALF_UP);
        String      descripcion  = (desc == null || desc.isBlank()) ? "Registrado por IA" : desc;

        try {
            return switch (intencion) {
                case "REGISTRAR_APORTE"           -> registrarAporte(grupoId, usuarioId, nombres,
                                                         montoDecimal, fecha, descripcion, moneda, builder);
                case "REGISTRAR_GASTO_COMPARTIDO" -> registrarGastoCompartido(grupoId, usuarioId, nombres,
                                                         todos, montoDecimal, fecha, descripcion, moneda, builder);
                case "REGISTRAR_MULTA"            -> registrarMulta(grupoId, usuarioId, nombres,
                                                         montoDecimal, fecha, descripcion, moneda, builder);
                case "REGISTRAR_INGRESO_DIRECTO"  -> registrarIngresoDirecto(grupoId, usuarioId,
                                                         montoDecimal, fecha, descripcion, moneda, builder);
                default -> builder.respuesta("⚠️ Tipo de acción no reconocida: " + intencion).build();
            };
        } catch (UnauthorizedException e) {
            return builder.respuesta(
                    "⛔ Solo los miembros con rol de **Directiva** pueden registrar movimientos."
            ).build();
        } catch (Exception e) {
            log.error("Error procesando acción IA [{}]: {}", intencion, e.getMessage());
            return builder.respuesta(
                    "❌ No se pudo registrar el movimiento: " + e.getMessage()
            ).build();
        }
    }

    private IaConsultaResponse registrarAporte(Long grupoId, Long usuarioId,
                                                List<String> nombres, BigDecimal monto,
                                                LocalDate fecha, String descripcion,
                                                String moneda,
                                                IaConsultaResponse.IaConsultaResponseBuilder builder) {
        if (nombres.isEmpty()) {
            return builder.respuesta(
                    "❓ ¿A quién le registro el aporte? Menciona el nombre. " +
                    "Ejemplo: *'**Juan** pagó 50 Bs'*"
            ).build();
        }
        Long miembroId = resolverMiembro(grupoId, nombres.get(0));
        if (miembroId == null) {
            return builder.respuesta(
                    "❌ No encontré al miembro **" + nombres.get(0) + "** en el grupo. " +
                    "Verifica que el nombre esté correcto."
            ).build();
        }

        RegistrarAporteRequest req = new RegistrarAporteRequest();
        req.setUsuarioId(miembroId);
        req.setMonto(monto);
        req.setFecha(fecha);
        req.setDescripcion(descripcion);

        MovimientoResponse mov = movimientoService.registrarAporte(grupoId, req, usuarioId);
        String nombreCompleto  = getNombreCompleto(grupoId, miembroId);

        return builder
                .respuesta("✅ **Aporte registrado exitosamente.**\n" +
                           "• Miembro: **" + nombreCompleto + "**\n" +
                           "• Monto: **" + monto + " " + moneda + "**\n" +
                           "• Fecha: " + fecha + "\n" +
                           "• Concepto: " + descripcion)
                .accionRealizada("APORTE_REGISTRADO")
                .movimientoRegistradoId(mov.getId())
                .build();
    }

    private IaConsultaResponse registrarGastoCompartido(Long grupoId, Long usuarioId,
                                                         List<String> nombres, boolean todos,
                                                         BigDecimal monto, LocalDate fecha,
                                                         String descripcion, String moneda,
                                                         IaConsultaResponse.IaConsultaResponseBuilder builder) {
        List<Long> usuarioIds;
        String quienes;

        if (todos || nombres.isEmpty()) {
            usuarioIds = obtenerTodosMiembros(grupoId);
            quienes    = "todos los miembros";
        } else {
            usuarioIds = resolverMiembros(grupoId, nombres);
            if (usuarioIds.isEmpty()) {
                return builder.respuesta(
                        "❌ No encontré a los miembros mencionados. Verifica los nombres."
                ).build();
            }
            quienes = String.join(", ", nombres);
        }

        RegistrarGastoCompartidoRequest req = new RegistrarGastoCompartidoRequest();
        req.setDescripcion(descripcion);
        req.setMontoTotal(monto);
        req.setFecha(fecha);
        req.setUsuarioIds(usuarioIds);

        MovimientoResponse mov = movimientoService.registrarGastoCompartido(grupoId, req, usuarioId);

        int n               = usuarioIds.size();
        BigDecimal porPersona = monto.divide(BigDecimal.valueOf(n), 2, BigDecimal.ROUND_HALF_UP);

        return builder
                .respuesta("✅ **Gasto compartido registrado.**\n" +
                           "• Monto total: **" + monto + " " + moneda + "**\n" +
                           "• Dividido entre: **" + quienes + "** (" + n + " persona" + (n != 1 ? "s" : "") + ")\n" +
                           "• Por persona: **" + porPersona + " " + moneda + "**\n" +
                           "• Concepto: " + descripcion)
                .accionRealizada("GASTO_COMPARTIDO_REGISTRADO")
                .movimientoRegistradoId(mov.getId())
                .build();
    }

    private IaConsultaResponse registrarMulta(Long grupoId, Long usuarioId,
                                               List<String> nombres, BigDecimal monto,
                                               LocalDate fecha, String descripcion,
                                               String moneda,
                                               IaConsultaResponse.IaConsultaResponseBuilder builder) {
        if (nombres.isEmpty()) {
            return builder.respuesta(
                    "❓ ¿A quién le aplico la multa? Menciona el nombre. " +
                    "Ejemplo: *'Ponle multa a **Juan** de 20 Bs'*"
            ).build();
        }
        Long miembroId = resolverMiembro(grupoId, nombres.get(0));
        if (miembroId == null) {
            return builder.respuesta(
                    "❌ No encontré al miembro **" + nombres.get(0) + "** en el grupo."
            ).build();
        }

        RegistrarMultaRequest req = new RegistrarMultaRequest();
        req.setUsuarioId(miembroId);
        req.setMonto(monto);
        req.setMotivo(descripcion);
        req.setFecha(fecha);
        req.setPeriodoMes(fecha.getMonthValue());
        req.setPeriodoAnio(fecha.getYear());

        MovimientoResponse mov     = movimientoService.registrarMulta(grupoId, req, usuarioId);
        String nombreCompleto      = getNombreCompleto(grupoId, miembroId);

        return builder
                .respuesta("✅ **Multa registrada.**\n" +
                           "• Miembro: **" + nombreCompleto + "**\n" +
                           "• Monto: **" + monto + " " + moneda + "**\n" +
                           "• Motivo: " + descripcion + "\n" +
                           "• Período: " + fecha.getMonth().name() + " " + fecha.getYear())
                .accionRealizada("MULTA_REGISTRADA")
                .movimientoRegistradoId(mov.getId())
                .build();
    }

    private IaConsultaResponse registrarIngresoDirecto(Long grupoId, Long usuarioId,
                                                        BigDecimal monto, LocalDate fecha,
                                                        String descripcion, String moneda,
                                                        IaConsultaResponse.IaConsultaResponseBuilder builder) {
        RegistrarIngresoDirectoRequest req = RegistrarIngresoDirectoRequest.builder()
                .descripcion(descripcion)
                .monto(monto)
                .fecha(fecha)
                .build();

        MovimientoResponse mov = movimientoService.registrarIngresoDirecto(grupoId, req, usuarioId);

        return builder
                .respuesta("✅ **Ingreso directo registrado.**\n" +
                           "• Monto: **" + monto + " " + moneda + "**\n" +
                           "• Concepto: " + descripcion + "\n" +
                           "• Fecha: " + fecha)
                .accionRealizada("INGRESO_REGISTRADO")
                .movimientoRegistradoId(mov.getId())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    //  Escaneo de facturas
    // ─────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public IaEscaneoResponse escanearFactura(Long grupoId, MultipartFile imagen) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(imagen.getBytes()) {
                @Override public String getFilename() { return imagen.getOriginalFilename(); }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            Map<String, Object> response = restTemplate.postForObject(
                    IA_URL + "/escanear",
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            assert response != null;

            return IaEscaneoResponse.builder()
                    .monto(response.get("monto") != null
                            ? ((Number) response.get("monto")).doubleValue() : null)
                    .fecha((String) response.get("fecha"))
                    .descripcion((String) response.get("descripcion"))
                    .textoExtraido((String) response.get("textoExtraido"))
                    .tipoDocumento((String) response.getOrDefault("tipoDocumento", "DOCUMENTO"))
                    .confianza(response.get("confianza") != null
                            ? ((Number) response.get("confianza")).doubleValue() : null)
                    .resumen((String) response.getOrDefault("resumen", "Documento procesado."))
                    .exitoso(Boolean.TRUE.equals(response.get("exitoso")))
                    .build();

        } catch (Exception e) {
            log.error("Error escaneando factura: {}", e.getMessage());
            return IaEscaneoResponse.builder()
                    .exitoso(false)
                    .resumen("❌ No se pudo procesar la imagen. Intenta con una foto más nítida y bien iluminada.")
                    .confianza(0.0)
                    .build();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Métricas del modelo (evidencia académica)
    // ─────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> obtenerMetricas() {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    IA_URL + "/metricas", Map.class);
            if (response == null) {
                return Map.of("disponible", false,
                        "error", "Sin respuesta del servicio de IA");
            }
            return response;
        } catch (Exception e) {
            log.error("Error obteniendo métricas IA: {}", e.getMessage());
            return Map.of("disponible", false,
                    "error", "El servicio de IA no está disponible en el puerto 5000.");
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Construcción del contexto
    // ─────────────────────────────────────────────────────────

    private Map<String, Object> construirContexto(Long grupoId, String moneda,
                                                   String nombreGrupo, Long solicitanteId) {
        // Rol e info del solicitante
        var gmSolicitante = grupoMiembroRepository.findByGrupoIdAndUsuarioId(grupoId, solicitanteId);
        String rolSolicitante = gmSolicitante
                .map(gm -> gm.getRol().name())
                .orElse("MIEMBRO");

        String nombreSolicitante = usuarioRepository.findById(solicitanteId)
                .map(Usuario::getNombre)
                .orElse("");

        // Datos personales del solicitante
        Map<String, Object> datosSolicitante = new HashMap<>();
        kardexRepository.findByGrupoIdAndUsuarioId(grupoId, solicitanteId).ifPresent(k -> {
            datosSolicitante.put("saldoPersonal", k.getSaldoActual());
            datosSolicitante.put("totalAportes",  k.getTotalAportes());
            datosSolicitante.put("totalGastos",   k.getTotalGastosCompartidos()
                                                    .add(k.getTotalGastosIndividuales()));
            datosSolicitante.put("totalMultas",   k.getTotalMultas());
        });

        boolean esDirectiva = "DIRECTIVA".equals(rolSolicitante);

        // Lista de miembros. Un miembro normal solo debe ver nombres y roles,
        // nunca los datos financieros de sus compañeros — eso es información
        // exclusiva de la Directiva.
        List<Map<String, Object>> miembrosData = new ArrayList<>();
        grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupoId).forEach(gm -> {
            var u = gm.getUsuario();
            Map<String, Object> m = new HashMap<>();
            m.put("nombre",   u.getNombre());
            m.put("apellido", u.getApellido());
            m.put("rol",      gm.getRol().name());
            if (esDirectiva) {
                kardexRepository.findByGrupoIdAndUsuarioId(grupoId, u.getId()).ifPresent(k -> {
                    m.put("saldoPersonal", k.getSaldoActual());
                    m.put("totalAportes",  k.getTotalAportes());
                    m.put("totalGastos",   k.getTotalGastosCompartidos()
                                            .add(k.getTotalGastosIndividuales()));
                    m.put("totalMultas",   k.getTotalMultas());
                });
            }
            miembrosData.add(m);
        });

        // Saldo grupal — solo la Directiva puede conocer cuánto tiene/debe el grupo.
        Map<String, Object> saldoData = new HashMap<>();
        if (esDirectiva) {
            saldoGrupoRepository.findByGrupoId(grupoId).ifPresent(s -> {
                saldoData.put("saldoDisponible", s.getSaldoDisponible());
                saldoData.put("totalIngresos",   s.getTotalIngresos());
                saldoData.put("totalEgresos",    s.getTotalEgresos());
                saldoData.put("totalMultas",     s.getTotalMultas());
            });
        }

        // Cuotas/mensualidades pendientes: la Directiva ve las de todo el grupo;
        // un miembro normal solo puede ver las suyas propias.
        List<Map<String, Object>> cuotasPendientesData = new ArrayList<>();
        cuotaProgramadaRepository.findByGrupoIdAndEstado(grupoId, "PENDIENTE").stream()
                .filter(c -> esDirectiva || c.getUsuario().getId().equals(solicitanteId))
                .forEach(c -> {
                    Map<String, Object> cuota = new HashMap<>();
                    cuota.put("nombre",    c.getUsuario().getNombre());
                    cuota.put("apellido",  c.getUsuario().getApellido());
                    cuota.put("monto",     c.getMonto());
                    cuota.put("concepto",  c.getPagoProgramado().getNombre());
                    cuota.put("mes",       c.getMes());
                    cuota.put("anio",      c.getAnio());
                    cuotasPendientesData.add(cuota);
                });

        Map<String, Object> contexto = new HashMap<>();
        contexto.put("nombreGrupo",       nombreGrupo);
        contexto.put("moneda",            moneda);
        contexto.put("rolSolicitante",    rolSolicitante);
        contexto.put("nombreSolicitante", nombreSolicitante);
        contexto.put("datosSolicitante",  datosSolicitante);
        contexto.put("miembros",          miembrosData);
        contexto.put("saldoGrupo",        saldoData);
        contexto.put("cuotasPendientes",  cuotasPendientesData);
        return contexto;
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers de resolución de nombres
    // ─────────────────────────────────────────────────────────

    private Long resolverMiembro(Long grupoId, String nombre) {
        String nombreNorm = normalizar(nombre);
        return grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupoId).stream()
                .filter(gm -> {
                    String n = normalizar(gm.getUsuario().getNombre());
                    String a = normalizar(gm.getUsuario().getApellido());
                    return n.contains(nombreNorm) || a.contains(nombreNorm)
                        || nombreNorm.contains(n)  || nombreNorm.contains(a);
                })
                .map(gm -> gm.getUsuario().getId())
                .findFirst()
                .orElse(null);
    }

    private List<Long> resolverMiembros(Long grupoId, List<String> nombres) {
        return nombres.stream()
                .map(n -> resolverMiembro(grupoId, n))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Long> obtenerTodosMiembros(Long grupoId) {
        return grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupoId).stream()
                .map(gm -> gm.getUsuario().getId())
                .collect(Collectors.toList());
    }

    private String getNombreCompleto(Long grupoId, Long miembroId) {
        return grupoMiembroRepository.findByGrupoIdAndActivoTrue(grupoId).stream()
                .filter(gm -> gm.getUsuario().getId().equals(miembroId))
                .map(gm -> gm.getUsuario().getNombre() + " " + gm.getUsuario().getApellido())
                .findFirst()
                .orElse("el miembro");
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String s = texto.toLowerCase().trim();
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        return s.replaceAll("[^\\p{ASCII}]", "");
    }

    private static LocalDate parseFecha(String fechaStr) {
        try {
            if (fechaStr != null && !fechaStr.isBlank()) {
                return LocalDate.parse(fechaStr);
            }
        } catch (Exception ignored) {}
        return LocalDate.now();
    }
}
