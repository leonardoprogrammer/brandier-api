package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.AgregadoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.MarcaNoPainelDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelAutorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelGestorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PainelMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PeriodoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.PromotorDTO;
import com.leonardoprogrammer.brandier_api.model.dto.TotaisDTO;
import com.leonardoprogrammer.brandier_api.model.dto.UsoDeLimitesDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ProdutoRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PainelService {

    private static final Duration PERIODO_PADRAO = Duration.ofDays(30);
    private static final Duration PERIODO_MAXIMO = Duration.ofDays(366);
    private static final Duration TOLERANCIA_DE_RELOGIO = Duration.ofMinutes(5);

    private final ReposicaoRepository reposicaoRepository;
    private final ReposicaoService reposicaoService;
    private final MarcaRepository marcaRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final PromotorRepository promotorRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final LimitsProperties limits;
    private final Clock clock;

    public PainelService(ReposicaoRepository reposicaoRepository,
                         ReposicaoService reposicaoService,
                         MarcaRepository marcaRepository,
                         LojaRepository lojaRepository,
                         ProdutoRepository produtoRepository,
                         PromotorRepository promotorRepository,
                         ConvitePromotorRepository conviteRepository,
                         LimitsProperties limits,
                         Clock clock) {
        this.reposicaoRepository = reposicaoRepository;
        this.reposicaoService = reposicaoService;
        this.marcaRepository = marcaRepository;
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.promotorRepository = promotorRepository;
        this.conviteRepository = conviteRepository;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PainelGestorDTO painelDoGestor(Usuario gestor, Instant de, Instant ate) {
        Instant agora = Instant.now(clock);
        PeriodoDTO periodo = resolverPeriodo(de, ate, agora);

        Map<UUID, AgregadoDTO> totaisPorMarca = reposicaoRepository
                .porMarcaDoGestor(gestor.getId(), periodo.de(), periodo.ate()).stream()
                .collect(Collectors.toMap(AgregadoDTO::id, Function.identity()));

        List<MarcaNoPainelDTO> marcas = marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId()).stream()
                .map(marca -> new MarcaNoPainelDTO(marca.getId(), marca.getNome(),
                        totaisDe(totaisPorMarca.get(marca.getId())), usoDe(marca.getId(), agora)))
                .toList();

        return new PainelGestorDTO(periodo, marcas, limits.maxBrandsPerManager());
    }

    @Transactional(readOnly = true)
    public PainelMarcaDTO painelDaMarca(Usuario gestor, UUID marcaId, Instant de, Instant ate) {
        Marca marca = marcaRepository.findByIdAndGestorId(marcaId, gestor.getId())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));
        Instant agora = Instant.now(clock);
        PeriodoDTO periodo = resolverPeriodo(de, ate, agora);

        List<PromotorDTO> semAtividade = promotorRepository.listarSemAtividade(marcaId, periodo.de(), periodo.ate()).stream()
                .map(PromotorDTO::de)
                .toList();

        return new PainelMarcaDTO(
                periodo,
                marca.getId(),
                marca.getNome(),
                reposicaoRepository.totaisDaMarca(marcaId, periodo.de(), periodo.ate()),
                reposicaoRepository.porLojaDaMarca(marcaId, periodo.de(), periodo.ate()),
                reposicaoRepository.porAutorDaMarca(marcaId, periodo.de(), periodo.ate()),
                reposicaoRepository.porProdutoDaMarca(marcaId, periodo.de(), periodo.ate()),
                semAtividade,
                usoDe(marcaId, agora));
    }

    @Transactional(readOnly = true)
    public PainelAutorDTO painelDasMinhasReposicoes(Usuario autor, Instant de, Instant ate) {
        PeriodoDTO periodo = resolverPeriodo(de, ate, Instant.now(clock));

        return new PainelAutorDTO(
                periodo,
                reposicaoRepository.totaisDoAutor(autor.getId(), periodo.de(), periodo.ate()),
                reposicaoRepository.porMarcaDoAutor(autor.getId(), periodo.de(), periodo.ate()),
                reposicaoRepository.porLojaDoAutor(autor.getId(), periodo.de(), periodo.ate()),
                reposicaoRepository.porProdutoDoAutor(autor.getId(), periodo.de(), periodo.ate()),
                reposicaoService.ultimasDoUsuario(autor));
    }

    private static PeriodoDTO resolverPeriodo(Instant de, Instant ate, Instant agora) {
        Instant fim = ate != null ? ate : agora.plus(TOLERANCIA_DE_RELOGIO);
        Instant inicio = de != null ? de : fim.minus(PERIODO_PADRAO);

        if (!inicio.isBefore(fim) || Duration.between(inicio, fim).compareTo(PERIODO_MAXIMO) > 0) {
            throw new BusinessRuleException(ErrorCode.PERIODO_INVALIDO);
        }
        return new PeriodoDTO(inicio, fim);
    }

    private static TotaisDTO totaisDe(AgregadoDTO agregado) {
        return agregado == null ? new TotaisDTO(0L, 0L) : new TotaisDTO(agregado.reposicoes(), agregado.unidades());
    }

    private UsoDeLimitesDTO usoDe(UUID marcaId, Instant agora) {
        long promotoresOcupados = promotorRepository.countByMarcaId(marcaId)
                + conviteRepository.countByMarcaIdAndStatusAndExpiraEmAfter(marcaId, StatusConvite.PENDENTE, agora);

        return new UsoDeLimitesDTO(
                (int) lojaRepository.countByMarcaId(marcaId), limits.maxStoresPerBrand(),
                (int) produtoRepository.countByMarcaIdAndAtivoTrue(marcaId), limits.maxProductsPerBrand(),
                (int) promotoresOcupados, limits.maxPromotersPerBrand());
    }
}
