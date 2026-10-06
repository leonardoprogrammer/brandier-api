package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

@Repository
public class RelatorioReposicaoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public TotaisDTO totais(FiltroRelatorio filtro) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> consulta = builder.createQuery(Object[].class);
        Root<Reposicao> reposicao = consulta.from(Reposicao.class);
        Join<Reposicao, ItemReposicao> item = reposicao.join("itens");

        consulta.multiselect(
                builder.countDistinct(reposicao.get("id")),
                builder.coalesce(builder.sumAsLong(item.<Integer>get("quantidade")), 0L));
        consulta.where(ReposicaoSpecs.condicoes(filtro, reposicao, consulta, builder).toArray(new jakarta.persistence.criteria.Predicate[0]));

        Object[] linha = entityManager.createQuery(consulta).getSingleResult();
        return new TotaisDTO((Long) linha[0], (Long) linha[1]);
    }
}
