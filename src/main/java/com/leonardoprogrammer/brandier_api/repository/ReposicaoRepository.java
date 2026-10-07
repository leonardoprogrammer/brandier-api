package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReposicaoRepository extends JpaRepository<Reposicao, UUID>, JpaSpecificationExecutor<Reposicao> {

    @Override
    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    Page<Reposicao> findAll(Specification<Reposicao> especificacao, Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    Page<Reposicao> findAllByMarcaId(UUID marcaId, Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    Page<Reposicao> findAllByMarcaIdAndAutorId(UUID marcaId, UUID autorId, Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    Page<Reposicao> findAllByAutorId(UUID autorId, Pageable pageable);

    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    Optional<Reposicao> findByIdAndMarcaId(UUID id, UUID marcaId);

    @EntityGraph(attributePaths = {"marca", "marca.gestor", "loja", "autor"})
    List<Reposicao> findTop5ByAutorIdOrderByRepostoEmDescCriadoEmDesc(UUID autorId);

    boolean existsByLojaId(UUID lojaId);

    boolean existsByAutorId(UUID autorId);

    boolean existsByMarcaId(UUID marcaId);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO(count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.itens i "
            + "where r.marca.id = :marcaId and r.repostoEm >= :de and r.repostoEm < :ate")
    TotaisDTO totaisDaMarca(@Param("marcaId") UUID marcaId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(l.id, l.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.loja l join r.itens i "
            + "where r.marca.id = :marcaId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by l.id, l.nome order by sum(i.quantidade) desc, l.nome asc")
    List<AgregadoDTO> porLojaDaMarca(@Param("marcaId") UUID marcaId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(a.id, a.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.autor a join r.itens i "
            + "where r.marca.id = :marcaId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by a.id, a.nome order by sum(i.quantidade) desc, a.nome asc")
    List<AgregadoDTO> porAutorDaMarca(@Param("marcaId") UUID marcaId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(p.id, p.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.itens i join i.produto p "
            + "where r.marca.id = :marcaId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by p.id, p.nome order by sum(i.quantidade) desc, p.nome asc")
    List<AgregadoDTO> porProdutoDaMarca(@Param("marcaId") UUID marcaId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(m.id, m.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.marca m join r.itens i "
            + "where m.gestor.id = :gestorId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by m.id, m.nome")
    List<AgregadoDTO> porMarcaDoGestor(@Param("gestorId") UUID gestorId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO(count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.itens i "
            + "where r.autor.id = :autorId and r.repostoEm >= :de and r.repostoEm < :ate")
    TotaisDTO totaisDoAutor(@Param("autorId") UUID autorId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(m.id, m.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.marca m join r.itens i "
            + "where r.autor.id = :autorId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by m.id, m.nome order by sum(i.quantidade) desc, m.nome asc")
    List<AgregadoDTO> porMarcaDoAutor(@Param("autorId") UUID autorId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(l.id, l.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.loja l join r.itens i "
            + "where r.autor.id = :autorId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by l.id, l.nome order by sum(i.quantidade) desc, l.nome asc")
    List<AgregadoDTO> porLojaDoAutor(@Param("autorId") UUID autorId, @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("select new com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO(p.id, p.nome, count(distinct r.id), coalesce(sum(i.quantidade), 0L)) "
            + "from Reposicao r join r.itens i join i.produto p "
            + "where r.autor.id = :autorId and r.repostoEm >= :de and r.repostoEm < :ate "
            + "group by p.id, p.nome order by sum(i.quantidade) desc, p.nome asc")
    List<AgregadoDTO> porProdutoDoAutor(@Param("autorId") UUID autorId, @Param("de") Instant de, @Param("ate") Instant ate);
}
