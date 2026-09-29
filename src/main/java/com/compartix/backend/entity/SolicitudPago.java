package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_pago")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SolicitudPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // "CUOTA" | "MULTA"
    @Column(nullable = false, length = 20)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuota_id")
    private CuotaProgramada cuota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "multa_id")
    private Multa multa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "comprobante_url", nullable = false, length = 500)
    private String comprobanteUrl;

    // "PENDIENTE" | "APROBADA" | "RECHAZADA"
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}
