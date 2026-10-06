package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.event.ConviteEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.ConvitePendenteDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteRecebidoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import com.leonardoprogrammer.brandier_api.security.OpaqueTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConviteServiceTests {

    private static final Instant AGORA = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private ConvitePromotorRepository conviteRepository;

    @Mock
    private PromotorRepository promotorRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ConviteService service;
    private Usuario gestor;
    private Usuario convidado;
    private Marca marca;

    @BeforeEach
    void preparar() {
        service = new ConviteService(conviteRepository, promotorRepository, marcaRepository, usuarioRepository,
                new LimitsProperties(2, 5, 10, 5, 500), eventPublisher, Clock.fixed(AGORA, ZoneOffset.UTC), 7);
        gestor = usuario("Gestor", "gestor@exemplo.com");
        convidado = usuario("Convidada", "convidada@exemplo.com");
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
        lenient().when(marcaRepository.findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        lenient().when(marcaRepository.findByIdAndGestorId(marca.getId(), gestor.getId())).thenReturn(Optional.of(marca));
        lenient().when(conviteRepository.save(any(ConvitePromotor.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        lenient().when(promotorRepository.save(any(Promotor.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private static Usuario usuario(String nome, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setEmailCanonico(email);
        return usuario;
    }

    private static void assertErro(Throwable erro, ErrorCode esperado) {
        assertThat(erro).isInstanceOfSatisfying(BusinessRuleException.class,
                excecao -> assertThat(excecao.getErrorCode()).isEqualTo(esperado));
    }

    private ConvitePromotor convitePara(Usuario destinatario, String token) {
        ConvitePromotor convite = new ConvitePromotor();
        convite.setId(UUID.randomUUID());
        convite.setMarca(marca);
        convite.setEmail(destinatario.getEmail());
        convite.setEmailCanonico(destinatario.getEmailCanonico());
        convite.setTokenHash(OpaqueTokens.hash(token));
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setCriadoEm(AGORA.minusSeconds(60));
        convite.setExpiraEm(AGORA.plusSeconds(3600));
        lenient().when(conviteRepository.findByTokenHash(OpaqueTokens.hash(token))).thenReturn(Optional.of(convite));
        return convite;
    }

    @Test
    void convidarCriaOConviteNormalizadoComTokenEExpiracaoEPublicaOEmail() {
        ConvitePromotor criado = service.convidar(gestor, marca.getId(), "  Nova.Pessoa@Exemplo.com ");

        ArgumentCaptor<ConviteEmailRequested> evento = ArgumentCaptor.forClass(ConviteEmailRequested.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(criado.getEmail()).isEqualTo("nova.pessoa@exemplo.com");
        assertThat(criado.getEmailCanonico()).isEqualTo("nova.pessoa@exemplo.com");
        assertThat(criado.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(criado.getCriadoEm()).isEqualTo(AGORA);
        assertThat(criado.getExpiraEm()).isEqualTo(AGORA.plusSeconds(7 * 24 * 3600));
        assertThat(criado.getMarca()).isSameAs(marca);
        assertThat(evento.getValue().to()).isEqualTo("nova.pessoa@exemplo.com");
        assertThat(evento.getValue().gestorNome()).isEqualTo("Gestor");
        assertThat(evento.getValue().marcaNome()).isEqualTo("Marca Alfa");
        assertThat(criado.getTokenHash()).isEqualTo(OpaqueTokens.hash(evento.getValue().token()));
        assertThat(criado.getTokenHash()).isNotEqualTo(evento.getValue().token());
    }

    @Test
    void convidarTravaAMarcaAntesDeContar() {
        service.convidar(gestor, marca.getId(), "nova@exemplo.com");

        InOrder ordem = inOrder(marcaRepository, promotorRepository, conviteRepository);
        ordem.verify(marcaRepository).findByIdAndGestorIdForUpdate(marca.getId(), gestor.getId());
        ordem.verify(promotorRepository).countByMarcaId(marca.getId());
        ordem.verify(conviteRepository).save(any(ConvitePromotor.class));
    }

    @Test
    void convidarOProprioEmailLancaErroENaoEnvia() {
        assertThatThrownBy(() -> service.convidar(gestor, marca.getId(), "Gestor@Exemplo.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.CONVITE_PROPRIO_EMAIL));

        verify(conviteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void convidarUmAliasDoProprioEmailTambemEhRejeitado() {
        gestor.setEmailCanonico("gestor@gmail.com");

        assertThatThrownBy(() -> service.convidar(gestor, marca.getId(), "g.estor+marca@gmail.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.CONVITE_PROPRIO_EMAIL));
    }

    @Test
    void convidarQuemJaEPromotorDaMarcaLancaPromotorJaVinculado() {
        when(usuarioRepository.findByEmailCanonico("convidada@exemplo.com")).thenReturn(Optional.of(convidado));
        when(promotorRepository.existsByMarcaIdAndUsuarioId(marca.getId(), convidado.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.convidar(gestor, marca.getId(), "convidada@exemplo.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.PROMOTOR_JA_VINCULADO));

        verify(conviteRepository, never()).save(any());
    }

    @Test
    void convidarComContaExistenteQueNaoEPromotorFunciona() {
        when(usuarioRepository.findByEmailCanonico("convidada@exemplo.com")).thenReturn(Optional.of(convidado));
        when(promotorRepository.existsByMarcaIdAndUsuarioId(marca.getId(), convidado.getId())).thenReturn(false);

        assertThat(service.convidar(gestor, marca.getId(), "convidada@exemplo.com")).isNotNull();
    }

    @Test
    void convidarComConvitePendenteParaOMesmoEmailLancaConviteJaPendente() {
        when(conviteRepository.existsByMarcaIdAndEmailCanonicoAndStatusAndExpiraEmAfter(
                marca.getId(), "convidada@exemplo.com", StatusConvite.PENDENTE, AGORA)).thenReturn(true);

        assertThatThrownBy(() -> service.convidar(gestor, marca.getId(), "convidada@exemplo.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.CONVITE_JA_PENDENTE));

        verify(conviteRepository, never()).save(any());
    }

    @Test
    void convidarContaPromotoresEConvitesPendentesNoLimite() {
        when(promotorRepository.countByMarcaId(marca.getId())).thenReturn(3L);
        when(conviteRepository.countByMarcaIdAndStatusAndExpiraEmAfter(marca.getId(), StatusConvite.PENDENTE, AGORA)).thenReturn(2L);

        assertThatThrownBy(() -> service.convidar(gestor, marca.getId(), "nova@exemplo.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.LIMITE_PROMOTORES_ATINGIDO));

        verify(conviteRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void convidarPermiteAUltimaVagaDentroDoLimite() {
        when(promotorRepository.countByMarcaId(marca.getId())).thenReturn(3L);
        when(conviteRepository.countByMarcaIdAndStatusAndExpiraEmAfter(marca.getId(), StatusConvite.PENDENTE, AGORA)).thenReturn(1L);

        assertThat(service.convidar(gestor, marca.getId(), "nova@exemplo.com")).isNotNull();
    }

    @Test
    void convidarEmMarcaDeOutroGestorLancaNaoEncontradoENaoConta() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorIdForUpdate(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convidar(gestor, marcaAlheia, "nova@exemplo.com"))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(promotorRepository, never()).countByMarcaId(any());
        verify(conviteRepository, never()).save(any());
    }

    @Test
    void cancelarRemoveOConviteDaMarca() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        when(conviteRepository.findByIdAndMarcaId(convite.getId(), marca.getId())).thenReturn(Optional.of(convite));

        service.cancelar(gestor, marca.getId(), convite.getId());

        verify(conviteRepository).delete(convite);
    }

    @Test
    void cancelarConviteInexistenteOuDeOutraMarcaLancaNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(conviteRepository.findByIdAndMarcaId(id, marca.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(gestor, marca.getId(), id))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(conviteRepository, never()).delete(any());
    }

    @Test
    void cancelarEmMarcaDeOutroGestorLancaNaoEncontrado() {
        UUID marcaAlheia = UUID.randomUUID();
        when(marcaRepository.findByIdAndGestorId(marcaAlheia, gestor.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(gestor, marcaAlheia, UUID.randomUUID()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void consultarDevolveOConviteValidoDoDestinatario() {
        convitePara(convidado, "tok");

        ConviteRecebidoDTO recebido = service.consultar(convidado, "tok");

        assertThat(recebido.marcaNome()).isEqualTo("Marca Alfa");
        assertThat(recebido.gestorNome()).isEqualTo("Gestor");
        assertThat(recebido.email()).isEqualTo("convidada@exemplo.com");
        assertThat(recebido.expiraEm()).isEqualTo(AGORA.plusSeconds(3600));
    }

    @Test
    void consultarComTokenDesconhecidoLancaConviteInvalido() {
        when(conviteRepository.findByTokenHash(OpaqueTokens.hash("lixo"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(convidado, "lixo"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void consultarConviteExpiradoLancaConviteInvalido() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        convite.setExpiraEm(AGORA);

        assertThatThrownBy(() -> service.consultar(convidado, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void consultarConviteJaRespondidoLancaConviteInvalido() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        convite.setStatus(StatusConvite.RECUSADO);

        assertThatThrownBy(() -> service.consultar(convidado, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void consultarConviteEnviadoParaOutroEmailLancaConviteInvalido() {
        convitePara(convidado, "tok");
        Usuario intruso = usuario("Intruso", "intruso@exemplo.com");

        assertThatThrownBy(() -> service.consultar(intruso, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void consultarPeloProprioGestorDaMarcaLancaConviteInvalido() {
        convitePara(gestor, "tok");

        assertThatThrownBy(() -> service.consultar(gestor, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void aceitarMarcaOConviteComoAceitoECriaOPromotor() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        when(conviteRepository.responderSePendente(convite.getId(), StatusConvite.ACEITO, StatusConvite.PENDENTE, AGORA)).thenReturn(1);
        when(promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), convidado.getId())).thenReturn(Optional.empty());

        VinculoPromotorDTO vinculo = service.aceitar(convidado, "tok");

        ArgumentCaptor<Promotor> salvo = ArgumentCaptor.forClass(Promotor.class);
        verify(promotorRepository).save(salvo.capture());
        assertThat(salvo.getValue().getMarca()).isSameAs(marca);
        assertThat(salvo.getValue().getUsuario()).isSameAs(convidado);
        assertThat(salvo.getValue().getEntrouEm()).isEqualTo(AGORA);
        assertThat(vinculo.marcaId()).isEqualTo(marca.getId());
        assertThat(vinculo.marcaNome()).isEqualTo("Marca Alfa");
        assertThat(vinculo.gestorNome()).isEqualTo("Gestor");
        assertThat(vinculo.entrouEm()).isEqualTo(AGORA);
    }

    @Test
    void aceitarSemConseguirMarcarOConviteComoPendenteLancaConviteInvalidoENaoCriaPromotor() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        when(conviteRepository.responderSePendente(eq(convite.getId()), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.aceitar(convidado, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));

        verify(promotorRepository, never()).save(any());
    }

    @Test
    void aceitarNaoDuplicaOPromotorJaExistente() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        Promotor existente = new Promotor();
        existente.setMarca(marca);
        existente.setUsuario(convidado);
        existente.setEntrouEm(AGORA.minusSeconds(500));
        when(conviteRepository.responderSePendente(eq(convite.getId()), any(), any(), any())).thenReturn(1);
        when(promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), convidado.getId())).thenReturn(Optional.of(existente));

        assertThat(service.aceitar(convidado, "tok").marcaId()).isEqualTo(marca.getId());

        verify(promotorRepository, never()).save(any());
    }

    @Test
    void aceitarConviteDeOutroEmailLancaConviteInvalido() {
        convitePara(convidado, "tok");

        assertThatThrownBy(() -> service.aceitar(usuario("Intruso", "intruso@exemplo.com"), "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));

        verify(conviteRepository, never()).responderSePendente(any(), any(), any(), any());
        verify(promotorRepository, never()).save(any());
    }

    @Test
    void recusarMarcaOConviteComoRecusadoENaoCriaPromotor() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        when(conviteRepository.responderSePendente(convite.getId(), StatusConvite.RECUSADO, StatusConvite.PENDENTE, AGORA)).thenReturn(1);

        service.recusar(convidado, "tok");

        verify(conviteRepository).responderSePendente(convite.getId(), StatusConvite.RECUSADO, StatusConvite.PENDENTE, AGORA);
        verify(promotorRepository, never()).save(any());
    }

    @Test
    void recusarConviteJaRespondidoLancaConviteInvalido() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        when(conviteRepository.responderSePendente(eq(convite.getId()), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.recusar(convidado, "tok"))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));
    }

    @Test
    void listarRecebidosDevolveSoOsConvitesPendentesDoEmailDoUsuarioSemOsDeMarcasPropriasDele() {
        ConvitePromotor valido = convitePara(convidado, "tok");
        Marca marcaDoProprioConvidado = new Marca();
        marcaDoProprioConvidado.setId(UUID.randomUUID());
        marcaDoProprioConvidado.setNome("Minha marca");
        marcaDoProprioConvidado.setGestor(convidado);
        ConvitePromotor invalido = convitePara(convidado, "outro");
        invalido.setMarca(marcaDoProprioConvidado);
        when(conviteRepository.findAllByEmailCanonicoAndStatusAndExpiraEmAfterOrderByCriadoEmDesc(
                convidado.getEmailCanonico(), StatusConvite.PENDENTE, AGORA)).thenReturn(List.of(valido, invalido));

        List<ConvitePendenteDTO> recebidos = service.listarRecebidos(convidado);

        assertThat(recebidos).hasSize(1);
        assertThat(recebidos.get(0).id()).isEqualTo(valido.getId());
        assertThat(recebidos.get(0).marcaNome()).isEqualTo("Marca Alfa");
        assertThat(recebidos.get(0).gestorNome()).isEqualTo("Gestor");
        assertThat(recebidos.get(0).expiraEm()).isEqualTo(AGORA.plusSeconds(3600));
    }

    @Test
    void listarRecebidosSemConvitesDevolveListaVazia() {
        assertThat(service.listarRecebidos(convidado)).isEmpty();
    }

    @Test
    void aceitarPorIdCriaOPromotorEMarcaOConviteComoAceito() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        lenient().when(conviteRepository.findById(convite.getId())).thenReturn(Optional.of(convite));
        when(conviteRepository.responderSePendente(convite.getId(), StatusConvite.ACEITO, StatusConvite.PENDENTE, AGORA)).thenReturn(1);
        when(promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), convidado.getId())).thenReturn(Optional.empty());

        VinculoPromotorDTO vinculo = service.aceitarPorId(convidado, convite.getId());

        ArgumentCaptor<Promotor> salvo = ArgumentCaptor.forClass(Promotor.class);
        verify(promotorRepository).save(salvo.capture());
        assertThat(salvo.getValue().getUsuario()).isSameAs(convidado);
        assertThat(salvo.getValue().getMarca()).isSameAs(marca);
        assertThat(vinculo.marcaId()).isEqualTo(marca.getId());
        assertThat(vinculo.marcaNome()).isEqualTo("Marca Alfa");
    }

    @Test
    void aceitarPorIdDeConviteDeOutroEmailLancaNaoEncontradoENaoAltera() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        lenient().when(conviteRepository.findById(convite.getId())).thenReturn(Optional.of(convite));

        assertThatThrownBy(() -> service.aceitarPorId(usuario("Intruso", "intruso@exemplo.com"), convite.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        verify(conviteRepository, never()).responderSePendente(any(), any(), any(), any());
        verify(promotorRepository, never()).save(any());
    }

    @Test
    void aceitarPorIdDeConviteInexistenteExpiradoOuRespondidoLancaNaoEncontrado() {
        UUID desconhecido = UUID.randomUUID();
        when(conviteRepository.findById(desconhecido)).thenReturn(Optional.empty());
        ConvitePromotor expirado = convitePara(convidado, "exp");
        expirado.setExpiraEm(AGORA);
        ConvitePromotor respondido = convitePara(convidado, "resp");
        respondido.setStatus(StatusConvite.ACEITO);
        lenient().when(conviteRepository.findById(expirado.getId())).thenReturn(Optional.of(expirado));
        lenient().when(conviteRepository.findById(respondido.getId())).thenReturn(Optional.of(respondido));

        for (UUID id : List.of(desconhecido, expirado.getId(), respondido.getId())) {
            assertThatThrownBy(() -> service.aceitarPorId(convidado, id))
                    .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
        }
        verify(promotorRepository, never()).save(any());
    }

    @Test
    void aceitarPorIdQuandoACorridaJaRespondeuOConviteLancaConviteInvalido() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        lenient().when(conviteRepository.findById(convite.getId())).thenReturn(Optional.of(convite));
        when(conviteRepository.responderSePendente(eq(convite.getId()), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.aceitarPorId(convidado, convite.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.TOKEN_CONVITE_INVALIDO));

        verify(promotorRepository, never()).save(any());
    }

    @Test
    void recusarPorIdMarcaOConviteComoRecusado() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        lenient().when(conviteRepository.findById(convite.getId())).thenReturn(Optional.of(convite));
        when(conviteRepository.responderSePendente(convite.getId(), StatusConvite.RECUSADO, StatusConvite.PENDENTE, AGORA)).thenReturn(1);

        service.recusarPorId(convidado, convite.getId());

        verify(conviteRepository).responderSePendente(convite.getId(), StatusConvite.RECUSADO, StatusConvite.PENDENTE, AGORA);
        verify(promotorRepository, never()).save(any());
    }

    @Test
    void recusarPorIdDeConviteAlheioOuJaRespondidoLancaNaoEncontrado() {
        ConvitePromotor convite = convitePara(convidado, "tok");
        lenient().when(conviteRepository.findById(convite.getId())).thenReturn(Optional.of(convite));

        assertThatThrownBy(() -> service.recusarPorId(usuario("Intruso", "intruso@exemplo.com"), convite.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));

        when(conviteRepository.responderSePendente(eq(convite.getId()), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.recusarPorId(convidado, convite.getId()))
                .satisfies(erro -> assertErro(erro, ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void naoUsaContadorQuandoOConviteEhInvalido() {
        when(conviteRepository.findByTokenHash(OpaqueTokens.hash("lixo"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aceitar(convidado, "lixo")).isInstanceOf(BusinessRuleException.class);

        verify(promotorRepository, never()).countByMarcaId(any());
        verify(conviteRepository, never()).countByMarcaIdAndStatusAndExpiraEmAfter(any(), any(), any());
    }
}
