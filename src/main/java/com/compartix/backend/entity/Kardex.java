package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kardex", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"grupo_id", "usuario_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Kardex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "total_aportes", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAportes = BigDecimal.ZERO;

    @Column(name = "total_gastos_compartidos", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGastosCompartidos = BigDecimal.ZERO;

    @Column(name = "total_gastos_individuales", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGastosIndividuales = BigDecimal.ZERO;

    @Column(name = "saldo_actual", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoActual = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    @Builder.Default
    @Column(name = "total_multas", precision = 10, scale = 2)
    private BigDecimal totalMultas = BigDecimal.ZERO;

    // Aporte acumulado para ahorro (carnaval queda en total_aportes)
    @Builder.Default
    @Column(name = "total_ahorro", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAhorro = BigDecimal.ZERO;
}