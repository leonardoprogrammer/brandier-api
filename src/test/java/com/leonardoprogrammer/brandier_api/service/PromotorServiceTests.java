package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotorServiceTests {

    private static final Instant AGORA = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private PromotorRepository promotorRepository;

    @Mock
    private ConvitePromotorRepository conviteRepository;

    @Mock
    private MarcaRepository marcaRepository;

    private PromotorService service;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        service = new PromotorService(promotorRepository, conviteRepository, marcaRepository,
                new LimitsProperties(2, 5, 10, 5, 500), Clock.fixed(AGORA, ZoneOffset.UTC));
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setGestor(gestor);
        lenient().when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        lenient().when(marcaRepository.findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
    }

    private Promotor promotor(Usuario usuario) {
        Promotor promotor = new Promotor();
        promotor.setId(UUID.randomUUID());
        promotor.setMarca(marca);
        promotor.setUsuario(usuario);
        promotor.setEntrouEm(AGORA.minusSeconds(100));
        return promotor;
    }

    private Usuario usuario(String nome) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        return usuario;
    }

    private ConvitePromotor convite(StatusConvite status, Instant expiraEm) {
        ConvitePromotor convite = new ConvitePromotor();
        convite.setId(UUID.randomUUID());
        convite.setMarca(marca);
        convite.setStatus(status);
        convite.setExpiraEm(expiraEm);
        return convite;
    }

    private static void assertNaoEncontrado(Throwable erro) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void listarDaMarcaContaPromotoresEApenasConvitesPendentesNaoExpirados() {
        List<Promotor> promotores = List.of(promotor(usuario("A")), promotor(usuario("B")));
        List<ConvitePromotor> convites = List.of(
                convite(StatusConvite.PENDENTE, AGORA.plusSeconds(60)),
                convite(StatusConvite.PENDENTE, AGORA.minusSeconds(60)),
                convite(StatusConvite.RECUSADO, AGORA.plusSeconds(60)),
                convite(StatusConvite.ACEITO, AGORA.plusSeconds(60)));
        when(promotorRepository.listarPorMarca(marca.getId())).thenReturn(promotores);
        when(conviteRepository.findAllByMarcaIdOrderByCriadoEmDesc(marca.getId())).thenReturn(convites);

        PromotorService.PromotoresDaMarca resultado = service.listarDaMarca(gestor, marca.getId());

        assertThat(resultado.promotores()).isEqualTo(promotores);
        assertThat(resultado.convites()).isEqualTo(convites);
        assertThat(resultado.usadas()).isEqualTo(3);
        assertThat(resultado.limite()).isEqualTo(5);
        assertThat(resultado.agora()).isEqualTo(AGORA);
    }

    @Test
    void listarDaMarcaDeOutroGestorLancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarDaMarca(gestor, marcaAlheia)).satisfies(PromotorServiceTests::assertNaoEncontrado);

        verify(promotorRepository, never()).listarPorMarca(any());
    }

    @Test
    void removerApagaOPromotorDaMarcaDoGestor() {
        Promotor existente = promotor(usuario("A"));
        when(promotorRepository.findByIdAndMarcaId(existente.getId(), marca.getId())).thenReturn(Optional.of(existente));

        service.remover(gestor, marca.getId(), existente.getId());

        verify(promotorRepository).delete(existente);
    }

    @Test
    void removerPromotorDeOutraMarcaLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(promotorRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(gestor, marca.getId(), id)).satisfies(PromotorServiceTests::assertNaoEncontrado);

        verify(promotorRepository, never()).delete(any());
    }

    @Test
    void removerEmMarcaDeOutroGestorLancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(gestor, marcaAlheia, UUID.randomUUID()))
                .satisfies(PromotorServiceTests::assertNaoEncontrado);

        verify(promotorRepository, never()).delete(any());
    }

    @Test
    void meusVinculosDevolveOsVinculosDoUsuario() {
        Usuario usuario = usuario("A");
        marca.setNome("Marca Alfa");
        gestor.setNome("Gestor Silva");
        Promotor vinculo = promotor(usuario);
        when(promotorRepository.listarPorUsuario(usuario.getId())).thenReturn(List.of(vinculo));

        assertThat(service.meusVinculos(usuario)).containsExactly(
                new VinculoPromotorDTO(marca.getId(), "Marca Alfa", "Gestor Silva", vinculo.getEntrouEm()));
    }

    @Test
    void sairApagaOVinculoDoProprioUsuario() {
        Usuario usuario = usuario("A");
        Promotor vinculo = promotor(usuario);
        when(promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), usuario.getId())).thenReturn(Optional.of(vinculo));

        service.sair(usuario, marca.getId());

        verify(promotorRepository).delete(vinculo);
    }

    @Test
    void sairDeMarcaSemVinculoLancaNaoEncontrado() {
        Usuario usuario = usuario("A");
        when(promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), usuario.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sair(usuario, marca.getId())).satisfies(PromotorServiceTests::assertNaoEncontrado);

        verify(promotorRepository, never()).delete(any());
    }
}
