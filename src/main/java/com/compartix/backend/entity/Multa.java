package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "multas")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Multa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_id")
    private Movimiento movimiento;

    @Column(nullable = false)
    private String motivo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "periodo_mes")
    private Integer periodoMes;

    @Column(name = "periodo_anio")
    private Integer periodoAnio;
}