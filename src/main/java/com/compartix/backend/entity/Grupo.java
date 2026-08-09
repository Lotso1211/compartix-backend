package com.compartix.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grupos")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "codigo_invitacion", nullable = false, unique = true, length = 20)
    private String codigoInvitacion;

    @Column(name = "cuota_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal cuotaBase = BigDecimal.ZERO;

    @Column(length = 10)
    private String moneda = "BOB";

    @Column(nullable = false)
    private Boolean activo = true;

    // Umbral opcional de saldo bajo por fondo (null = alerta desactivada). Cuando el
    // saldo cruza por debajo de este valor tras un gasto, se avisa a la Directiva.
    @Column(name = "alerta_saldo_carnaval", precision = 10, scale = 2)
    private BigDecimal alertaSaldoCarnaval;

    @Column(name = "alerta_saldo_ahorro", precision = 10, scale = 2)
    private BigDecimal alertaSaldoAhorro;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    // Relaciones
    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrupoMiembro> miembros = new ArrayList<>();

    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movimiento> movimientos = new ArrayList<>();

    @OneToOne(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    private SaldoGrupo saldoGrupo;
}