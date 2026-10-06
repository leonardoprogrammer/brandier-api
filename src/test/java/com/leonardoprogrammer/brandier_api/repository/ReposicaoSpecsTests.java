package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReposicaoSpecsTests {

    private CriteriaBuilder builder;
    private Root<Reposicao> root;
    private CriteriaQuery<?> query;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() {
        builder = Mockito.mock(CriteriaBuilder.class);
        root = Mockito.mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        query = Mockito.mock(CriteriaQuery.class, Mockito.RETURNS_DEEP_STUBS);
    }

    private List<Predicate> condicoes(FiltroRelatorio filtro) {
        return ReposicaoSpecs.condicoes(filtro, root, query, builder);
    }

    @Test
    void semFiltrosAdicionaSomenteARestricaoDaMarca() {
        UUID marcaId = UUID.randomUUID();

        List<Predicate> resultado = condicoes(new FiltroRelatorio(marcaId, null, null, null, null, null));

        assertThat(resultado).hasSize(1);
        verify(builder).equal(any(), eq(marcaId));
        verify(builder, never()).exists(any());
        verify(builder, never()).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Instant.class));
    }

    @Test
    void cadaFiltroInformadoAdicionaSuaRestricao() {
        UUID marcaId = UUID.randomUUID();
        UUID lojaId = UUID.randomUUID();
        UUID autorId = UUID.randomUUID();
        UUID produtoId = UUID.randomUUID();
        Instant de = Instant.parse("2026-02-01T00:00:00Z");
        Instant ate = Instant.parse("2026-03-01T00:00:00Z");

        List<Predicate> resultado = condicoes(new FiltroRelatorio(marcaId, lojaId, autorId, produtoId, de, ate));

        assertThat(resultado).hasSize(6);
        verify(builder).equal(any(), eq(marcaId));
        verify(builder).equal(any(), eq(lojaId));
        verify(builder).equal(any(), eq(autorId));
        verify(builder).equal(any(), eq(produtoId));
        verify(builder).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), eq(de));
        verify(builder).lessThan(any(jakarta.persistence.criteria.Expression.class), eq(ate));
        verify(builder).exists(any());
    }

    @Test
    void oFiltroDeProdutoUsaSubconsultaParaNaoDuplicarReposicoes() {
        UUID produtoId = UUID.randomUUID();

        condicoes(new FiltroRelatorio(UUID.randomUUID(), null, null, produtoId, null, null));

        verify(query).subquery(Integer.class);
        verify(builder).exists(any());
    }

    @Test
    void periodoAbertoEmUmDosLadosAdicionaSomenteOLadoInformado() {
        Instant de = Instant.parse("2026-02-01T00:00:00Z");

        List<Predicate> resultado = condicoes(new FiltroRelatorio(UUID.randomUUID(), null, null, null, de, null));

        assertThat(resultado).hasSize(2);
        verify(builder).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), eq(de));
        verify(builder, never()).lessThan(any(jakarta.persistence.criteria.Expression.class), any(Instant.class));
    }

    @Test
    void aSpecificationCombinaTodasAsRestricoesComE() {
        Predicate combinado = Mockito.mock(Predicate.class);
        Mockito.when(builder.and(any(Predicate[].class))).thenReturn(combinado);

        Predicate resultado = ReposicaoSpecs.doFiltro(new FiltroRelatorio(UUID.randomUUID(), null, null, null, null, null))
                .toPredicate(root, query, builder);

        assertThat(resultado).isSameAs(combinado);
    }
}
