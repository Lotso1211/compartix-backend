package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos_programados")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PagoProgramado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private Grupo grupo;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(name = "monto_mensual", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoMensual;

    @Builder.Default
    @Column(name = "monto_carnaval", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoCarnaval = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "monto_ahorro", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAhorro = BigDecimal.ZERO;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "dia_limite_pago", nullable = false)
    private Integer diaLimitePago;

    @Builder.Default
    @Column(name = "tipo_multa", nullable = false, length = 20)
    private String tipoMulta = "FIJO";

    @Column(name = "monto_multa", precision = 10, scale = 2)
    private BigDecimal montoMulta;

    @Column(name = "porcentaje_multa", precision = 5, scale = 2)
    private BigDecimal porcentajeMulta;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;
}