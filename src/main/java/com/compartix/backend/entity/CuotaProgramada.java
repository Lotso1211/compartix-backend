package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cuotas_programadas")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CuotaProgramada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_programado_id", nullable = false)
    private PagoProgramado pagoProgramado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Builder.Default
    @Column(name = "monto_carnaval", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoCarnaval = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "monto_ahorro", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAhorro = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Builder.Default
    @Column(name = "multa_aplicada")
    private Boolean multaAplicada = false;

    @Builder.Default
    @Column(name = "monto_multa", precision = 10, scale = 2)
    private BigDecimal montoMulta = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id")
    private Movimiento movimiento;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}