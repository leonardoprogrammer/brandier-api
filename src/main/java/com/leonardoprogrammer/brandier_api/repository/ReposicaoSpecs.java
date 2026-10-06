package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ReposicaoSpecs {

    private ReposicaoSpecs() {
    }

    public static Specification<Reposicao> doFiltro(FiltroRelatorio filtro) {
        return (root, query, builder) -> {
            List<Predicate> condicoes = condicoes(filtro, root, query, builder);
            return builder.and(condicoes.toArray(new Predicate[0]));
        };
    }

    public static List<Predicate> condicoes(FiltroRelatorio filtro, Root<Reposicao> root, CriteriaQuery<?> query,
                                            CriteriaBuilder builder) {
        List<Predicate> condicoes = new ArrayList<>();
        condicoes.add(builder.equal(root.get("marca").get("id"), filtro.marcaId()));

        if (filtro.lojaId() != null) {
            condicoes.add(builder.equal(root.get("loja").get("id"), filtro.lojaId()));
        }
        if (filtro.autorId() != null) {
            condicoes.add(builder.equal(root.get("autor").get("id"), filtro.autorId()));
        }
        if (filtro.de() != null) {
            condicoes.add(builder.greaterThanOrEqualTo(root.get("repostoEm"), filtro.de()));
        }
        if (filtro.ate() != null) {
            condicoes.add(builder.lessThan(root.get("repostoEm"), filtro.ate()));
        }
        if (filtro.produtoId() != null) {
            Subquery<Integer> subconsulta = query.subquery(Integer.class);
            Root<ItemReposicao> item = subconsulta.from(ItemReposicao.class);
            subconsulta.select(builder.literal(1)).where(
                    builder.equal(item.get("reposicao"), root),
                    builder.equal(item.get("produto").get("id"), filtro.produtoId()));
            condicoes.add(builder.exists(subconsulta));
        }
        return condicoes;
    }
}
