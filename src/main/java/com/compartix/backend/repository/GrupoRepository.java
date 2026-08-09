package com.compartix.backend.repository;

import com.compartix.backend.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GrupoRepository extends JpaRepository<Grupo, Long> {
    Optional<Grupo> findByCodigoInvitacion(String codigoInvitacion);
    Boolean existsByCodigoInvitacion(String codigoInvitacion);
}