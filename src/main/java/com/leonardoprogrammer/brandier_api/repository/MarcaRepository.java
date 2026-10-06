package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcaRepository extends JpaRepository<Marca, UUID> {

    List<Marca> findAllByGestorIdOrderByNomeAsc(UUID gestorId);

    Optional<Marca> findByIdAndGestorId(UUID id, UUID gestorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Marca m where m.id = :id and m.gestor.id = :gestorId")
    Optional<Marca> findByIdAndGestorIdForUpdate(@Param("id") UUID id, @Param("gestorId") UUID gestorId);

    long countByGestorId(UUID gestorId);

    boolean existsByGestorIdAndNomeIgnoreCase(UUID gestorId, String nome);

    boolean existsByGestorIdAndNomeIgnoreCaseAndIdNot(UUID gestorId, String nome, UUID id);
}
