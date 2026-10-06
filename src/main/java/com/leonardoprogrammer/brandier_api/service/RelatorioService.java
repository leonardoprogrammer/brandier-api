package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.RelatorioDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.FiltroRelatorio;
import com.leonardoprogrammer.brandier_api.repository.RelatorioReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoSpecs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RelatorioService {

    public record ArquivoCsv(String nome, byte[] conteudo) {
    }

    public static final int MAXIMO_DE_REPOSICOES_NO_CSV = 5000;

    private static final int TAMANHO_MAXIMO_DA_PAGINA = 100;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final DateTimeFormatter DATA_DO_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(FUSO);
    private static final String BOM = "﻿";
    private static final List<String> CABECALHO = List.of(
            "Data e hora", "Marca", "Loja", "Cidade", "UF", "Autor", "Produto", "Quantidade", "Observações");

    private final ReposicaoRepository reposicaoRepository;
    private final RelatorioReposicaoRepository relatorioRepository;
    private final ReposicaoService reposicaoService;
    private final MarcaAcessoService acessoService;
    private final Clock clock;

    public RelatorioService(ReposicaoRepository reposicaoRepository,
                            RelatorioReposicaoRepository relatorioRepository,
                            ReposicaoService reposicaoService,
                            MarcaAcessoService acessoService,
                            Clock clock) {
        this.reposicaoRepository = reposicaoRepository;
        this.relatorioRepository = relatorioRepository;
        this.reposicaoService = reposicaoService;
        this.acessoService = acessoService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RelatorioDTO consultar(Usuario usuario, UUID marcaId, UUID lojaId, UUID autorId, UUID produtoId,
                                  Instant de, Instant ate, int pagina, int tamanho, String ordenar, String direcao) {
        FiltroRelatorio filtro = filtro(usuario, marcaId, lojaId, autorId, produtoId, de, ate);
        PageRequest pageable = PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamanho), TAMANHO_MAXIMO_DA_PAGINA),
                ordenacao(ordenar, direcao));

        Page<Reposicao> resultado = reposicaoRepository.findAll(ReposicaoSpecs.doFiltro(filtro), pageable);
        Instant agora = Instant.now(clock);

        return new RelatorioDTO(
                resultado.getContent().stream().map(reposicao -> reposicaoService.paraDTO(reposicao, usuario, agora)).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                relatorioRepository.totais(filtro));
    }

    @Transactional(readOnly = true)
    public ArquivoCsv exportar(Usuario usuario, UUID marcaId, UUID lojaId, UUID autorId, UUID produtoId,
                               Instant de, Instant ate, String ordenar, String direcao) {
        FiltroRelatorio filtro = filtro(usuario, marcaId, lojaId, autorId, produtoId, de, ate);
        Page<Reposicao> resultado = reposicaoRepository.findAll(
                ReposicaoSpecs.doFiltro(filtro), PageRequest.of(0, MAXIMO_DE_REPOSICOES_NO_CSV, ordenacao(ordenar, direcao)));

        if (resultado.getTotalElements() > MAXIMO_DE_REPOSICOES_NO_CSV) {
            throw new BusinessRuleException(ErrorCode.RELATORIO_MUITO_GRANDE);
        }

        StringBuilder csv = new StringBuilder(BOM).append(CsvEscaper.linha(CABECALHO));
        for (Reposicao reposicao : resultado.getContent()) {
            reposicao.getItens().stream()
                    .sorted(Comparator.comparing(item -> item.getProduto().getNome(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(item -> csv.append(CsvEscaper.linha(linha(reposicao, item))));
        }

        String nome = "reposicoes-" + DATA_DO_ARQUIVO.format(Instant.now(clock)) + ".csv";
        return new ArquivoCsv(nome, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> linha(Reposicao reposicao, ItemReposicao item) {
        return List.of(
                DATA_HORA.format(reposicao.getRepostoEm()),
                reposicao.getMarca().getNome(),
                reposicao.getLoja().getNome(),
                reposicao.getLoja().getCidade(),
                reposicao.getLoja().getUf(),
                reposicao.getAutor().getNome(),
                item.getProduto().getNome(),
                String.valueOf(item.getQuantidade()),
                reposicao.getObservacoes() == null ? "" : reposicao.getObservacoes());
    }

    private FiltroRelatorio filtro(Usuario usuario, UUID marcaId, UUID lojaId, UUID autorId, UUID produtoId,
                                   Instant de, Instant ate) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);

        if (de != null && ate != null && !de.isBefore(ate)) {
            throw new BusinessRuleException(ErrorCode.PERIODO_INVALIDO);
        }

        UUID autorEfetivo = acesso.gestor() ? autorId : usuario.getId();
        return new FiltroRelatorio(marcaId, lojaId, autorEfetivo, produtoId, de, ate);
    }

    private static Sort ordenacao(String ordenar, String direcao) {
        Sort.Direction sentido = "asc".equalsIgnoreCase(direcao) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String campo = switch (ordenar == null || ordenar.isBlank() ? "data" : ordenar) {
            case "data" -> "repostoEm";
            case "loja" -> "loja.nome";
            case "autor" -> "autor.nome";
            default -> throw new BusinessRuleException(ErrorCode.REQUISICAO_INVALIDA);
        };
        return Sort.by(new Sort.Order(sentido, campo), Sort.Order.desc("criadoEm"), Sort.Order.asc("id"));
    }
}
