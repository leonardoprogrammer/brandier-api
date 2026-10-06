package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailCanonico(String emailCanonico);

    @Modifying
    @Query("delete from Usuario u where u.status = :status and u.criadoEm < :limit")
    int deleteByStatusCreatedBefore(@Param("status") StatusUsuario status, @Param("limit") Instant limit);
}
