package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotorRepository extends JpaRepository<Promotor, UUID> {

    @Query("select p from Promotor p join fetch p.usuario where p.marca.id = :marcaId order by p.entrouEm asc")
    List<Promotor> listarPorMarca(@Param("marcaId") UUID marcaId);

    @Query("select p from Promotor p join fetch p.marca m join fetch m.gestor where p.usuario.id = :usuarioId order by m.nome asc")
    List<Promotor> listarPorUsuario(@Param("usuarioId") UUID usuarioId);

    @Query("select p from Promotor p join fetch p.usuario where p.marca.id = :marcaId "
            + "and not exists (select 1 from Reposicao r where r.marca.id = p.marca.id and r.autor.id = p.usuario.id "
            + "and r.repostoEm >= :de and r.repostoEm < :ate) order by p.usuario.nome asc")
    List<Promotor> listarSemAtividade(@Param("marcaId") UUID marcaId, @Param("de") Instant de, @Param("ate") Instant ate);

    long countByMarcaId(UUID marcaId);

    boolean existsByMarcaIdAndUsuarioId(UUID marcaId, UUID usuarioId);

    long countByUsuarioId(UUID usuarioId);

    Optional<Promotor> findByIdAndMarcaId(UUID id, UUID marcaId);

    Optional<Promotor> findByMarcaIdAndUsuarioId(UUID marcaId, UUID usuarioId);

    @Modifying
    @Query("delete from Promotor p where p.usuario.id = :usuarioId")
    int deleteByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
