package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;

public record RelatorioDTO(List<ReposicaoDTO> conteudo, int pagina, int tamanho, long totalElementos,
                           int totalPaginas, TotaisDTO totais) {
}
