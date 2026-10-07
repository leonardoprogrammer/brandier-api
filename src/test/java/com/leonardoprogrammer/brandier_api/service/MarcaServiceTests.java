package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcaServiceTests {

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ReposicaoRepository reposicaoRepository;

    private MarcaService marcaService;
    private Usuario gestor;

    @BeforeEach
    void preparar() {
        marcaService = new MarcaService(marcaRepository, usuarioRepository, reposicaoRepository,
                new LimitsProperties(2, 5, 10, 5, 500));
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        gestor.setEmail("gestor@exemplo.com");
        lenient().when(usuarioRepository.findByIdForUpdate(gestor.getId())).thenReturn(Optional.of(gestor));
        lenient().when(marcaRepository.save(any(Marca.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private Marca marca(String nome) {
        Marca marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setGestor(gestor);
        marca.setNome(nome);
        return marca;
    }

    @Test
    void limiteDeMarcasVemDaConfiguracao() {
        assertThat(marcaService.limiteDeMarcas()).isEqualTo(2);
    }

    @Test
    void criarGuardaNomeSemEspacosEDescricaoVaziaComoNula() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(0L);

        Marca criada = marcaService.criar(gestor, "  Marca Um  ", "   ");

        ArgumentCaptor<Marca> capturada = ArgumentCaptor.forClass(Marca.class);
        verify(marcaRepository).save(capturada.capture());
        assertThat(criada.getNome()).isEqualTo("Marca Um");
        assertThat(capturada.getValue().getDescricao()).isNull();
        assertThat(capturada.getValue().getGestor()).isSameAs(gestor);
    }

    @Test
    void criarGuardaADescricaoSemEspacosNasPontas() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(0L);

        Marca criada = marcaService.criar(gestor, "Marca", "  Uma descrição  ");

        assertThat(criada.getDescricao()).isEqualTo("Uma descrição");
    }

    @Test
    void criarTravaALinhaDoGestorAntesDeContarAsMarcas() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(0L);

        marcaService.criar(gestor, "Marca", null);

        InOrder ordem = inOrder(usuarioRepository, marcaRepository);
        ordem.verify(usuarioRepository).findByIdForUpdate(gestor.getId());
        ordem.verify(marcaRepository).countByGestorId(gestor.getId());
        ordem.verify(marcaRepository).save(any(Marca.class));
    }

    @Test
    void criarPermiteACriacaoDaUltimaMarcaDentroDoLimite() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(1L);

        assertThat(marcaService.criar(gestor, "Segunda", null)).isNotNull();
    }

    @Test
    void criarNoLimiteLancaLimiteAtingidoENaoSalva() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(2L);

        assertThatThrownBy(() -> marcaService.criar(gestor, "Terceira", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.LIMITE_MARCAS_ATINGIDO));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    void criarComNomeJaUsadoPeloGestorIgnorandoMaiusculasLancaNomeDuplicado() {
        when(marcaRepository.countByGestorId(gestor.getId())).thenReturn(1L);
        when(marcaRepository.existsByGestorIdAndNomeIgnoreCase(gestor.getId(), "Marca Um")).thenReturn(true);

        assertThatThrownBy(() -> marcaService.criar(gestor, " Marca Um ", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.MARCA_NOME_DUPLICADO));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    void criarComGestorInexistenteLancaNaoEncontrado() {
        when(usuarioRepository.findByIdForUpdate(gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.criar(gestor, "Marca", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void listarDevolveSomenteAsMarcasDoGestor() {
        List<Marca> marcas = List.of(marca("A"), marca("B"));
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId())).thenReturn(marcas);

        assertThat(marcaService.listar(gestor)).isEqualTo(marcas);
    }

    @Test
    void buscarDevolveAMarcaDoGestor() {
        Marca existente = marca("A");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));

        assertThat(marcaService.buscar(gestor, existente.getId())).isSameAs(existente);
    }

    @Test
    void buscarMarcaDeOutroGestorOuInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(id, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.buscar(gestor, id))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void atualizarAlteraNomeEDescricao() {
        Marca existente = marca("Antigo");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));

        Marca atualizada = marcaService.atualizar(gestor, existente.getId(), "  Novo  ", "Nova descrição");

        assertThat(atualizada.getNome()).isEqualTo("Novo");
        assertThat(atualizada.getDescricao()).isEqualTo("Nova descrição");
        verify(marcaRepository).save(existente);
    }

    @Test
    void atualizarPodeManterOMesmoNomeDaPropriaMarca() {
        Marca existente = marca("Mesmo");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));
        when(marcaRepository.existsByGestorIdAndNomeIgnoreCaseAndIdNot(gestor.getId(), "Mesmo", existente.getId()))
                .thenReturn(false);

        assertThat(marcaService.atualizar(gestor, existente.getId(), "Mesmo", null).getNome()).isEqualTo("Mesmo");
    }

    @Test
    void atualizarComNomeDeOutraMarcaDoGestorLancaNomeDuplicado() {
        Marca existente = marca("Antigo");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));
        when(marcaRepository.existsByGestorIdAndNomeIgnoreCaseAndIdNot(gestor.getId(), "Outra", existente.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> marcaService.atualizar(gestor, existente.getId(), "Outra", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.MARCA_NOME_DUPLICADO));

        assertThat(existente.getNome()).isEqualTo("Antigo");
        verify(marcaRepository, never()).save(any());
    }

    @Test
    void atualizarMarcaDeOutroGestorLancaNaoEncontradoSemAlterarNada() {
        UUID id = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(id, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.atualizar(gestor, id, "Nome", null))
                .isInstanceOf(BusinessRuleException.class);

        verify(marcaRepository, never()).save(any());
    }

    @Test
    void excluirRemoveAMarcaSemReposicoes() {
        Marca existente = marca("A");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));
        when(reposicaoRepository.existsByMarcaId(existente.getId())).thenReturn(false);

        marcaService.excluir(gestor, existente.getId());

        verify(marcaRepository).delete(existente);
        verify(marcaRepository, never()).save(existente);
    }

    @Test
    void excluirApenasInativaAMarcaComReposicoes() {
        Marca existente = marca("A");
        when(marcaRepository.findByIdAndGestorId(existente.getId(), gestor.getId())).thenReturn(Optional.of(existente));
        when(reposicaoRepository.existsByMarcaId(existente.getId())).thenReturn(true);

        marcaService.excluir(gestor, existente.getId());

        assertThat(existente.isAtiva()).isFalse();
        verify(marcaRepository).save(existente);
        verify(marcaRepository, never()).delete(any(Marca.class));
    }

    @Test
    void excluirMarcaDeOutroGestorLancaNaoEncontradoSemExcluir() {
        UUID id = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(id, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> marcaService.excluir(gestor, id)).isInstanceOf(BusinessRuleException.class);

        verify(marcaRepository, never()).delete(any(Marca.class));
    }

    @Test
    void excluirTodasDoGestorInativaAsComReposicoesEExcluiAsDemais() {
        Marca comHistorico = marca("Com histórico");
        Marca semHistorico = marca("Sem histórico");
        when(marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId()))
                .thenReturn(List.of(comHistorico, semHistorico));
        when(reposicaoRepository.existsByMarcaId(comHistorico.getId())).thenReturn(true);
        when(reposicaoRepository.existsByMarcaId(semHistorico.getId())).thenReturn(false);

        marcaService.excluirTodasDoGestor(gestor.getId());

        assertThat(comHistorico.isAtiva()).isFalse();
        verify(marcaRepository).save(comHistorico);
        verify(marcaRepository).delete(semHistorico);
    }
}
