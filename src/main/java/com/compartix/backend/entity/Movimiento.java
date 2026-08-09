package com.compartix.backend.entity;

import com.compartix.backend.enums.TipoFondo;
import com.compartix.backend.enums.TipoMovimiento;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "movimientos")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaMovimiento categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private LocalDate fecha = LocalDate.now();

    @Column(name = "comprobante_url", length = 500)
    private String comprobanteUrl;

    @Column(name = "origen_ia", nullable = false)
    private Boolean origenIa = false;

    // A qué fondo(s) pertenece este movimiento. Nulo en movimientos antiguos
    // registrados antes de esta funcionalidad (no se reclasifican).
    @Enumerated(EnumType.STRING)
    @Column(name = "fondo", length = 20)
    private TipoFondo fondo;

    @Builder.Default
    @Column(name = "monto_carnaval", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoCarnaval = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "monto_ahorro", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAhorro = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    // Relaciones
    @OneToMany(mappedBy = "movimiento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MovimientoDetalle> detalles;
}