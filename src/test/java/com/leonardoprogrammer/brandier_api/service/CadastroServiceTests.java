package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.email.DisposableEmailDomains;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.event.VerificationEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class CadastroServiceTests {

    private static final Instant AGORA = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private DisposableEmailDomains disposableEmailDomains;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CadastroService cadastroService;

    @BeforeEach
    void preparar() {
        cadastroService = new CadastroService(usuarioRepository, passwordEncoder, emailVerificationService,
                disposableEmailDomains, eventPublisher, Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(passwordEncoder.encode(any())).thenReturn("hash-da-senha");
    }

    @Test
    void cadastrarEmailNovoCriaUsuarioPendenteComSenhaEmHashEEnviaVerificacao() {
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.empty());
        when(emailVerificationService.issue(any())).thenReturn(Optional.of("token-bruto"));

        cadastroService.cadastrar("  Pessoa Teste ", "  Pessoa@Exemplo.COM ", "Senha1234");

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        Usuario salvo = capturado.getValue();
        assertThat(salvo.getNome()).isEqualTo("Pessoa Teste");
        assertThat(salvo.getEmail()).isEqualTo("pessoa@exemplo.com");
        assertThat(salvo.getEmailCanonico()).isEqualTo("pessoa@exemplo.com");
        assertThat(salvo.getSenha()).isEqualTo("hash-da-senha").isNotEqualTo("Senha1234");
        assertThat(salvo.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(salvo.getTermosAceitosEm()).isEqualTo(AGORA);

        ArgumentCaptor<VerificationEmailRequested> evento = ArgumentCaptor.forClass(VerificationEmailRequested.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().to()).isEqualTo("pessoa@exemplo.com");
        assertThat(evento.getValue().nome()).isEqualTo("Pessoa Teste");
        assertThat(evento.getValue().token()).isEqualTo("token-bruto");
    }

    @Test
    void cadastrarEmailDeContaAtivaNaoAlteraNadaENaoEnviaEmail() {
        Usuario ativo = usuario(StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(ativo));

        cadastroService.cadastrar("Outro Nome", "pessoa@exemplo.com", "OutraSenha1");

        verify(usuarioRepository, never()).save(any());
        verify(emailVerificationService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertThat(ativo.getSenha()).isEqualTo("hash-antigo");
    }

    @Test
    void cadastrarEmailDeContaPendenteAtualizaOsDadosEReenviaAVerificacao() {
        Usuario pendente = usuario(StatusUsuario.PENDENTE);
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(pendente));
        when(emailVerificationService.issue(pendente)).thenReturn(Optional.of("novo-token"));

        cadastroService.cadastrar("Nome Novo", "pessoa@exemplo.com", "SenhaNova1");

        assertThat(pendente.getNome()).isEqualTo("Nome Novo");
        assertThat(pendente.getSenha()).isEqualTo("hash-da-senha");
        assertThat(pendente.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        verify(usuarioRepository).save(pendente);
        verify(eventPublisher).publishEvent(any(VerificationEmailRequested.class));
    }

    @Test
    void cadastrarEmailDeContaPendenteDentroDoIntervaloNaoAlteraNadaENaoEnviaEmail() {
        Usuario pendente = usuario(StatusUsuario.PENDENTE);
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(pendente));
        when(emailVerificationService.issue(pendente)).thenReturn(Optional.empty());

        cadastroService.cadastrar("Nome Novo", "pessoa@exemplo.com", "SenhaNova1");

        assertThat(pendente.getNome()).isEqualTo("Pessoa Antiga");
        assertThat(pendente.getSenha()).isEqualTo("hash-antigo");
        verify(usuarioRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cadastrarEmailNovoDentroDoIntervaloSalvaUsuarioMasNaoPublicaEvento() {
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.empty());
        when(emailVerificationService.issue(any())).thenReturn(Optional.empty());

        cadastroService.cadastrar("Pessoa", "pessoa@exemplo.com", "Senha1234");

        verify(usuarioRepository).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void verificarEmailAtivaOUsuario() {
        Usuario pendente = usuario(StatusUsuario.PENDENTE);
        when(emailVerificationService.consume("token-valido")).thenReturn(pendente);

        cadastroService.verificarEmail("token-valido");

        assertThat(pendente.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        verify(usuarioRepository).save(pendente);
    }

    @Test
    void verificarEmailComTokenInvalidoPropagaOErroENaoSalvaNada() {
        when(emailVerificationService.consume("token-ruim"))
                .thenThrow(new BusinessRuleException(ErrorCode.TOKEN_VERIFICACAO_INVALIDO));

        assertThatThrownBy(() -> cadastroService.verificarEmail("token-ruim"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.TOKEN_VERIFICACAO_INVALIDO));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void reenviarVerificacaoParaContaPendentePublicaOEvento() {
        Usuario pendente = usuario(StatusUsuario.PENDENTE);
        when(usuarioRepository.findByEmailCanonico("pessoa@exemplo.com")).thenReturn(Optional.of(pendente));
        when(emailVerificationService.issue(pendente)).thenReturn(Optional.of("token-reenviado"));

        cadastroService.reenviarVerificacao("  PESSOA@exemplo.com ");

        ArgumentCaptor<VerificationEmailRequested> evento = ArgumentCaptor.forClass(VerificationEmailRequested.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().token()).isEqualTo("token-reenviado");
    }

    @Test
    void reenviarVerificacaoParaContaAtivaOuInexistenteNaoFazNada() {
        when(usuarioRepository.findByEmailCanonico("ativa@exemplo.com")).thenReturn(Optional.of(usuario(StatusUsuario.ATIVO)));
        when(usuarioRepository.findByEmailCanonico("nao-existe@exemplo.com")).thenReturn(Optional.empty());

        cadastroService.reenviarVerificacao("ativa@exemplo.com");
        cadastroService.reenviarVerificacao("nao-existe@exemplo.com");

        verify(emailVerificationService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cadastrarComEmailDescartavelLancaErroENaoSalvaNemEnviaNada() {
        when(disposableEmailDomains.isDisposable("pessoa@mailinator.com")).thenReturn(true);

        assertThatThrownBy(() -> cadastroService.cadastrar("Pessoa", " Pessoa@Mailinator.com ", "Senha1234"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DESCARTAVEL));

        verify(usuarioRepository, never()).findByEmailCanonico(any());
        verify(usuarioRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void cadastrarComAliasDoGmailGuardaOEmailDigitadoEOCanonico() {
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.empty());
        when(emailVerificationService.issue(any())).thenReturn(Optional.of("token-bruto"));

        cadastroService.cadastrar("Pessoa", "Pe.ssoa+brandier@GoogleMail.com", "Senha1234");

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertThat(capturado.getValue().getEmail()).isEqualTo("pe.ssoa+brandier@googlemail.com");
        assertThat(capturado.getValue().getEmailCanonico()).isEqualTo("pessoa@gmail.com");
    }

    @Test
    void cadastrarComAliasDeContaAtivaNaoCriaOutraContaNemEnviaEmail() {
        Usuario ativo = usuario(StatusUsuario.ATIVO);
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(ativo));

        cadastroService.cadastrar("Outra Pessoa", "p.e.s.s.o.a+outra@gmail.com", "OutraSenha1");

        verify(usuarioRepository, never()).save(any());
        verify(emailVerificationService, never()).issue(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void reenviarVerificacaoEncontraContaPendentePorAlias() {
        Usuario pendente = usuario(StatusUsuario.PENDENTE);
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(pendente));
        when(emailVerificationService.issue(pendente)).thenReturn(Optional.of("token-reenviado"));

        cadastroService.reenviarVerificacao("pessoa+teste@gmail.com");

        verify(eventPublisher).publishEvent(any(VerificationEmailRequested.class));
    }

    private Usuario usuario(StatusUsuario status) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Pessoa Antiga");
        usuario.setEmail("pessoa@exemplo.com");
        usuario.setSenha("hash-antigo");
        usuario.setStatus(status);
        return usuario;
    }
}
