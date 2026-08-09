package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saldo_grupo")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SaldoGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false, unique = true)
    private Grupo grupo;

    @Column(name = "total_ingresos", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalIngresos = BigDecimal.ZERO;

    @Column(name = "total_egresos", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEgresos = BigDecimal.ZERO;

    @Column(name = "total_multas", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalMultas = BigDecimal.ZERO;

    @Column(name = "saldo_disponible", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoDisponible = BigDecimal.ZERO;

    // Visibilidad por fondo (lo recaudado para cada bolsa)
    @Builder.Default
    @Column(name = "saldo_carnaval", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoCarnaval = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "saldo_ahorro", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldoAhorro = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}