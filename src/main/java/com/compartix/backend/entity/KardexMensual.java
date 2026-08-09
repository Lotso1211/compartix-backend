package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kardex_mensual", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"grupo_id", "usuario_id", "anio", "mes"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class KardexMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Short anio;

    @Column(nullable = false)
    private Short mes;

    @Column(name = "total_aportes_mes", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAportesMes = BigDecimal.ZERO;

    @Column(name = "total_gastos_compartidos_mes", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGastosCompartidosMes = BigDecimal.ZERO;

    @Column(name = "total_gastos_individuales_mes", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGastosIndividualesMes = BigDecimal.ZERO;

    @Column(name = "saldo_mes", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoMes = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}