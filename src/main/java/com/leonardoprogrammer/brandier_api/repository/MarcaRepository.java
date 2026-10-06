package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcaRepository extends JpaRepository<Marca, UUID> {

    List<Marca> findAllByGestorIdOrderByNomeAsc(UUID gestorId);

    Optional<Marca> findByIdAndGestorId(UUID id, UUID gestorId);

    long countByGestorId(UUID gestorId);

    boolean existsByGestorIdAndNomeIgnoreCase(UUID gestorId, String nome);

    boolean existsByGestorIdAndNomeIgnoreCaseAndIdNot(UUID gestorId, String nome, UUID id);
}
