package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
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
class LojaServiceTests {

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private MarcaAcessoService acessoService;

    @Mock
    private ReposicaoRepository reposicaoRepository;

    private LojaService lojaService;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        lojaService = new LojaService(lojaRepository, marcaRepository, acessoService, reposicaoRepository,
                new LimitsProperties(2, 5, 10, 5));
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setGestor(gestor);
        marca.setNome("Marca");
        lenient().when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        lenient().when(marcaRepository.findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId()))
                .thenReturn(Optional.of(marca));
        lenient().when(lojaRepository.save(any(Loja.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private Loja loja(String nome) {
        Loja loja = new Loja();
        loja.setId(UUID.randomUUID());
        loja.setMarca(marca);
        loja.setNome(nome);
        loja.setCidade("Cidade");
        loja.setUf("SP");
        return loja;
    }

    private static void assertErro(Throwable erro, ErrorCode esperado) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(esperado));
    }

    @Test
    void limiteDeLojasVemDaConfiguracao() {
        assertThat(lojaService.limiteDeLojas()).isEqualTo(5);
    }

    @Test
    void criarNormalizaOsCamposEVinculaALojaAMarca() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        Loja criada = lojaService.criar(gestor, marca.getId(), "  Loja Centro  ", "  Campinas ", " sp ", "   ");

        ArgumentCaptor<Loja> capturada = ArgumentCaptor.forClass(Loja.class);
        verify(lojaRepository).save(capturada.capture());
        assertThat(criada.getNome()).isEqualTo("Loja Centro");
        assertThat(capturada.getValue().getCidade()).isEqualTo("Campinas");
        assertThat(capturada.getValue().getUf()).isEqualTo("SP");
        assertThat(capturada.getValue().getEndereco()).isNull();
        assertThat(capturada.getValue().getMarca()).isSameAs(marca);
    }

    @Test
    void criarGuardaOEnderecoSemEspacosNasPontas() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        Loja criada = lojaService.criar(gestor, marca.getId(), "Loja", "Cidade", "RJ", "  Rua A, 10  ");

        assertThat(criada.getEndereco()).isEqualTo("Rua A, 10");
    }

    @Test
    void criarTravaALinhaDaMarcaAntesDeContarAsLojas() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(0L);

        lojaService.criar(gestor, marca.getId(), "Loja", "Cidade", "SP", null);

        InOrder ordem = inOrder(marcaRepository, lojaRepository);
        ordem.verify(marcaRepository).findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId());
        ordem.verify(lojaRepository).countByMarcaId(marca.getId());
        ordem.verify(lojaRepository).save(any(Loja.class));
    }

    @Test
    void criarPermiteACriacaoDaUltimaLojaDentroDoLimite() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(4L);

        assertThat(lojaService.criar(gestor, marca.getId(), "Quinta", "Cidade", "SP", null)).isNotNull();
    }

    @Test
    void criarNoLimiteLancaLimiteAtingidoENaoSalva() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(5L);

        assertThatThrownBy(() -> lojaService.criar(gestor, marca.getId(), "Sexta", "Cidade", "SP", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.LIMITE_LOJAS_ATINGIDO));

        verify(lojaRepository, never()).save(any());
    }

    @Test
    void criarComNomeJaUsadoNaMarcaIgnorandoMaiusculasLancaNomeDuplicado() {
        when(lojaRepository.countByMarcaId(marca.getId())).thenReturn(1L);
        when(lojaRepository.existsByMarcaIdAndNomeIgnoreCase(marca.getId(), "Loja Centro")).thenReturn(true);

        assertThatThrownBy(() -> lojaService.criar(gestor, marca.getId(), " Loja Centro ", "Cidade", "SP", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.LOJA_NOME_DUPLICADO));

        verify(lojaRepository, never()).save(any());
    }

    @Test
    void criarEmMarcaDeOutroGestorLancaNaoEncontradoENaoConta() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lojaService.criar(gestor, marcaAlheia, "Loja", "Cidade", "SP", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(lojaRepository, never()).countByMarcaId(any());
        verify(lojaRepository, never()).save(any());
    }

    @Test
    void listarDevolveAsLojasDaMarcaDoGestor() {
        List<Loja> lojas = List.of(loja("A"), loja("B"));
        when(acessoService.exigir(gestor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, true));
        when(lojaRepository.findAllByMarcaIdOrderByNomeAsc(marca.getId())).thenReturn(lojas);

        assertThat(lojaService.listar(gestor, marca.getId())).isEqualTo(lojas);
    }

    @Test
    void listarMarcaSemAcessoLancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(acessoService.exigir(gestor, marcaAlheia)).thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        assertThatThrownBy(() -> lojaService.listar(gestor, marcaAlheia))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(lojaRepository, never()).findAllByMarcaIdOrderByNomeAsc(any());
    }

    @Test
    void listarPermiteLeituraParaPromotorDaMarca() {
        Usuario promotor = new Usuario();
        promotor.setId(UUID.randomUUID());
        List<Loja> lojas = List.of(loja("A"));
        when(acessoService.exigir(promotor, marca.getId())).thenReturn(new MarcaAcessoService.Acesso(marca, false));
        when(lojaRepository.findAllByMarcaIdOrderByNomeAsc(marca.getId())).thenReturn(lojas);

        assertThat(lojaService.listar(promotor, marca.getId())).isEqualTo(lojas);
    }

    @Test
    void buscarDevolveALojaDaMarca() {
        Loja existente = loja("A");
        when(lojaRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        assertThat(lojaService.buscar(gestor, marca.getId(), existente.getId())).isSameAs(existente);
    }

    @Test
    void buscarLojaDeOutraMarcaLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(lojaRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lojaService.buscar(gestor, marca.getId(), id))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void atualizarAlteraOsCamposNormalizados() {
        Loja existente = loja("Antiga");
        when(lojaRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        Loja atualizada = lojaService.atualizar(gestor, marca.getId(), existente.getId(), " Nova ", " Santos ", "sp",
                " Av. Mar, 1 ");

        assertThat(atualizada.getNome()).isEqualTo("Nova");
        assertThat(atualizada.getCidade()).isEqualTo("Santos");
        assertThat(atualizada.getUf()).isEqualTo("SP");
        assertThat(atualizada.getEndereco()).isEqualTo("Av. Mar, 1");
    }

    @Test
    void atualizarComNomeDeOutraLojaDaMarcaLancaNomeDuplicado() {
        Loja existente = loja("Antiga");
        when(lojaRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(lojaRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marca.getId(), "Outra", existente.getId()))
                .thenReturn(true);

        assertThatThrownBy(() -> lojaService.atualizar(gestor, marca.getId(), existente.getId(), "Outra", "Cidade",
                "SP", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.LOJA_NOME_DUPLICADO));

        verify(lojaRepository, never()).save(any());
    }

    @Test
    void atualizarLojaInexistenteLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(lojaRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lojaService.atualizar(gestor, marca.getId(), id, "Nome", "Cidade", "SP", null))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void excluirRemoveALojaDaMarcaDoGestor() {
        Loja existente = loja("A");
        when(lojaRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        lojaService.excluir(gestor, marca.getId(), existente.getId());

        verify(lojaRepository).delete(existente);
    }

    @Test
    void excluirLojaComReposicoesLancaLojaComReposicoesENaoRemove() {
        Loja existente = loja("A");
        when(lojaRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));
        when(reposicaoRepository.existsByLojaId(existente.getId())).thenReturn(true);

        assertThatThrownBy(() -> lojaService.excluir(gestor, marca.getId(), existente.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.LOJA_COM_REPOSICOES));

        verify(lojaRepository, never()).delete(any());
    }

    @Test
    void excluirEmMarcaDeOutroGestorLancaNaoEncontradoENaoRemove() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lojaService.excluir(gestor, marcaAlheia, UUID.randomUUID()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(lojaRepository, never()).delete(any());
    }
}
