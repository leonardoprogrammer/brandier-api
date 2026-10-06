package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LojaRepository extends JpaRepository<Loja, UUID> {

    List<Loja> findAllByMarcaIdOrderByNomeAsc(UUID marcaId);

    Optional<Loja> findByIdAndMarcaId(UUID id, UUID marcaId);

    long countByMarcaId(UUID marcaId);

    boolean existsByMarcaIdAndNomeIgnoreCase(UUID marcaId, String nome);

    boolean existsByMarcaIdAndNomeIgnoreCaseAndIdNot(UUID marcaId, String nome, UUID id);
}
