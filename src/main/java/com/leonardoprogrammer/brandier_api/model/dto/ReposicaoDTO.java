package com.leonardoprogrammer.brandier_api.model.dto;

import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ReposicaoDTO(
        UUID id,
        UUID marcaId,
        String marcaNome,
        UUID lojaId,
        String lojaNome,
        String autorNome,
        Instant repostoEm,
        String observacoes,
        List<ItemReposicaoDTO> itens,
        int totalUnidades,
        Instant criadoEm,
        boolean podeEditar,
        Instant editavelAte) {

    public static ReposicaoDTO de(Reposicao reposicao, UUID usuarioId, Duration janelaEdicao, Instant agora) {
        boolean gestorDaMarca = reposicao.getMarca().getGestor().getId().equals(usuarioId);
        boolean autor = reposicao.getAutor().getId().equals(usuarioId);
        Instant fimDaJanela = reposicao.getCriadoEm().plus(janelaEdicao);

        List<ItemReposicaoDTO> itens = reposicao.getItens().stream()
                .map(ItemReposicaoDTO::de)
                .sorted(Comparator.comparing(ItemReposicaoDTO::produtoNome, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ReposicaoDTO(
                reposicao.getId(),
                reposicao.getMarca().getId(),
                reposicao.getMarca().getNome(),
                reposicao.getLoja().getId(),
                reposicao.getLoja().getNome(),
                reposicao.getAutor().getNome(),
                reposicao.getRepostoEm(),
                reposicao.getObservacoes(),
                itens,
                itens.stream().mapToInt(ItemReposicaoDTO::quantidade).sum(),
                reposicao.getCriadoEm(),
                gestorDaMarca || (autor && fimDaJanela.isAfter(agora)),
                !gestorDaMarca && autor ? fimDaJanela : null);
    }
}
