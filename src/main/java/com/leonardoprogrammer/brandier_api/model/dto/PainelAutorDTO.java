package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.List;

public record PainelAutorDTO(
        PeriodoDTO periodo,
        boolean possuiReposicoes,
        TotaisDTO totais,
        List<AgregadoDTO> porMarca,
        List<AgregadoDTO> porLoja,
        List<AgregadoDTO> porProduto,
        List<ReposicaoDTO> ultimas) {
}
