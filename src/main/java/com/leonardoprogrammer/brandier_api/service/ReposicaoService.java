package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ItemReposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Reposicao;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReposicaoService {

    public record ItemEntrada(UUID produtoId, int quantidade) {
    }

    private static final Duration TOLERANCIA_DE_RELOGIO = Duration.ofMinutes(5);
    private static final int TAMANHO_MAXIMO_DA_PAGINA = 100;

    private final ReposicaoRepository reposicaoRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final MarcaAcessoService acessoService;
    private final Clock clock;
    private final Duration janelaDeEdicao;

    public ReposicaoService(ReposicaoRepository reposicaoRepository,
                            LojaRepository lojaRepository,
                            ProdutoRepository produtoRepository,
                            MarcaAcessoService acessoService,
                            Clock clock,
                            @Value("${brandier.replenishment.edit-window-hours}") long janelaDeEdicaoHoras) {
        this.reposicaoRepository = reposicaoRepository;
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.acessoService = acessoService;
        this.clock = clock;
        this.janelaDeEdicao = Duration.ofHours(janelaDeEdicaoHoras);
    }

    @Transactional
    public ReposicaoDTO criar(Usuario usuario, UUID marcaId, UUID lojaId, Instant repostoEm,
                              List<ItemEntrada> itens, String observacoes) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        if (!acesso.marca().isAtiva()) {
            throw new BusinessRuleException(ErrorCode.MARCA_INATIVA);
        }
        Instant agora = Instant.now(clock);

        Reposicao reposicao = new Reposicao();
        reposicao.setMarca(acesso.marca());
        reposicao.setAutor(usuario);
        reposicao.setCriadoEm(agora);
        aplicar(reposicao, marcaId, lojaId, repostoEm, itens, observacoes, agora);

        return paraDTO(reposicaoRepository.save(reposicao), usuario, agora);
    }

    @Transactional
    public ReposicaoDTO atualizar(Usuario usuario, UUID marcaId, UUID id, UUID lojaId, Instant repostoEm,
                                  List<ItemEntrada> itens, String observacoes) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        Instant agora = Instant.now(clock);
        Reposicao reposicao = buscarVisivel(usuario, acesso, marcaId, id);
        exigirPermissaoDeAlteracao(reposicao, acesso, agora);

        aplicar(reposicao, marcaId, lojaId, repostoEm, itens, observacoes, agora);

        return paraDTO(reposicaoRepository.save(reposicao), usuario, agora);
    }

    @Transactional
    public void excluir(Usuario usuario, UUID marcaId, UUID id) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        Reposicao reposicao = buscarVisivel(usuario, acesso, marcaId, id);
        exigirPermissaoDeAlteracao(reposicao, acesso, Instant.now(clock));

        reposicaoRepository.delete(reposicao);
    }

    @Transactional(readOnly = true)
    public ReposicaoDTO buscar(Usuario usuario, UUID marcaId, UUID id) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        return paraDTO(buscarVisivel(usuario, acesso, marcaId, id), usuario, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public PaginaDTO<ReposicaoDTO> listarDaMarca(Usuario usuario, UUID marcaId, int pagina, int tamanho) {
        MarcaAcessoService.Acesso acesso = acessoService.exigir(usuario, marcaId);
        Pageable pageable = paginacao(pagina, tamanho);
        Page<Reposicao> resultado = acesso.gestor()
                ? reposicaoRepository.findAllByMarcaId(marcaId, pageable)
                : reposicaoRepository.findAllByMarcaIdAndAutorId(marcaId, usuario.getId(), pageable);

        Instant agora = Instant.now(clock);
        return PaginaDTO.de(resultado, reposicao -> paraDTO(reposicao, usuario, agora));
    }

    @Transactional(readOnly = true)
    public List<ReposicaoDTO> ultimasDoUsuario(Usuario usuario) {
        Instant agora = Instant.now(clock);
        return reposicaoRepository.findTop5ByAutorIdOrderByRepostoEmDescCriadoEmDesc(usuario.getId()).stream()
                .map(reposicao -> paraDTO(reposicao, usuario, agora))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaDTO<ReposicaoDTO> listarMinhas(Usuario usuario, int pagina, int tamanho) {
        Page<Reposicao> resultado = reposicaoRepository.findAllByAutorId(usuario.getId(), paginacao(pagina, tamanho));

        Instant agora = Instant.now(clock);
        return PaginaDTO.de(resultado, reposicao -> paraDTO(reposicao, usuario, agora));
    }

    private void aplicar(Reposicao reposicao, UUID marcaId, UUID lojaId, Instant repostoEm,
                         List<ItemEntrada> itens, String observacoes, Instant agora) {
        if (itens == null || itens.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.REPOSICAO_SEM_ITENS);
        }
        if (itens.stream().anyMatch(item -> item.quantidade() <= 0)) {
            throw new BusinessRuleException(ErrorCode.QUANTIDADE_INVALIDA);
        }

        List<UUID> idsDosProdutos = itens.stream().map(ItemEntrada::produtoId).toList();
        if (new HashSet<>(idsDosProdutos).size() != idsDosProdutos.size()) {
            throw new BusinessRuleException(ErrorCode.PRODUTO_REPETIDO);
        }

        Instant momento = repostoEm == null ? agora : repostoEm;
        if (momento.isAfter(agora.plus(TOLERANCIA_DE_RELOGIO))) {
            throw new BusinessRuleException(ErrorCode.DATA_REPOSICAO_FUTURA);
        }

        Loja loja = lojaRepository.findByIdAndMarcaId(lojaId, marcaId).orElseThrow(ReposicaoService::naoEncontrada);

        Map<UUID, Produto> produtos = produtoRepository.findAllByIdInAndMarcaId(idsDosProdutos, marcaId).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        Set<UUID> jaNaReposicao = reposicao.getItens().stream()
                .map(item -> item.getProduto().getId())
                .collect(Collectors.toSet());
        for (UUID idDoProduto : idsDosProdutos) {
            Produto produto = produtos.get(idDoProduto);
            if (produto == null || (!produto.isAtivo() && !jaNaReposicao.contains(idDoProduto))) {
                throw new BusinessRuleException(ErrorCode.PRODUTO_INVALIDO);
            }
        }

        reposicao.setLoja(loja);
        reposicao.setRepostoEm(momento);
        reposicao.setObservacoes(observacoes == null || observacoes.isBlank() ? null : observacoes.trim());
        sincronizarItens(reposicao, itens, produtos);
    }

    private static void sincronizarItens(Reposicao reposicao, List<ItemEntrada> itens, Map<UUID, Produto> produtos) {
        Set<UUID> mantidos = itens.stream().map(ItemEntrada::produtoId).collect(Collectors.toSet());
        reposicao.getItens().removeIf(item -> !mantidos.contains(item.getProduto().getId()));

        Map<UUID, ItemReposicao> existentes = reposicao.getItens().stream()
                .collect(Collectors.toMap(item -> item.getProduto().getId(), Function.identity()));
        for (ItemEntrada entrada : itens) {
            ItemReposicao item = existentes.get(entrada.produtoId());
            if (item == null) {
                item = new ItemReposicao();
                item.setReposicao(reposicao);
                item.setProduto(produtos.get(entrada.produtoId()));
                reposicao.getItens().add(item);
            }
            item.setQuantidade(entrada.quantidade());
        }
    }

    private Reposicao buscarVisivel(Usuario usuario, MarcaAcessoService.Acesso acesso, UUID marcaId, UUID id) {
        Reposicao reposicao = reposicaoRepository.findByIdAndMarcaId(id, marcaId)
                .orElseThrow(ReposicaoService::naoEncontrada);
        if (!acesso.gestor() && !reposicao.getAutor().getId().equals(usuario.getId())) {
            throw naoEncontrada();
        }
        return reposicao;
    }

    private void exigirPermissaoDeAlteracao(Reposicao reposicao, MarcaAcessoService.Acesso acesso,
                                            Instant agora) {
        if (acesso.gestor()) {
            return;
        }
        if (!reposicao.getCriadoEm().plus(janelaDeEdicao).isAfter(agora)) {
            throw new BusinessRuleException(ErrorCode.JANELA_EDICAO_ENCERRADA);
        }
    }

    public ReposicaoDTO paraDTO(Reposicao reposicao, Usuario usuario, Instant agora) {
        return ReposicaoDTO.de(reposicao, usuario.getId(), janelaDeEdicao, agora);
    }

    private static Pageable paginacao(int pagina, int tamanho) {
        return PageRequest.of(
                Math.max(0, pagina),
                Math.min(Math.max(1, tamanho), TAMANHO_MAXIMO_DA_PAGINA),
                Sort.by(Sort.Order.desc("repostoEm"), Sort.Order.desc("criadoEm")));
    }

    private static BusinessRuleException naoEncontrada() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
