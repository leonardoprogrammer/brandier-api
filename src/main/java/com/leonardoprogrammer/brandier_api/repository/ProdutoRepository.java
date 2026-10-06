package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    List<Produto> findAllByMarcaIdOrderByNomeAsc(UUID marcaId);

    Optional<Produto> findByIdAndMarcaId(UUID id, UUID marcaId);

    List<Produto> findAllByMarcaIdAndAtivoTrueOrderByNomeAsc(UUID marcaId);

    List<Produto> findAllByIdInAndMarcaId(Collection<UUID> ids, UUID marcaId);

    long countByMarcaIdAndAtivoTrue(UUID marcaId);

    boolean existsByMarcaIdAndNomeIgnoreCase(UUID marcaId, String nome);

    boolean existsByMarcaIdAndNomeIgnoreCaseAndIdNot(UUID marcaId, String nome, UUID id);
}
