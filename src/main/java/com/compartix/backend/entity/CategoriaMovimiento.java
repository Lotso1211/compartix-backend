package com.compartix.backend.entity;

import com.compartix.backend.enums.TipoCategoria;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "categorias_movimiento")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CategoriaMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;  // nullable: NULL = categoría global del sistema

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoCategoria tipo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo = true;

    @OneToMany(mappedBy = "categoria", fetch = FetchType.LAZY)
    private List<Movimiento> movimientos;
}