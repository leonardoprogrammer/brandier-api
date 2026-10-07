package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusUsuario;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import com.leonardoprogrammer.brandier_api.security.GoogleTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleLoginServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private GoogleTokenService googleTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private GoogleLoginService service;

    @BeforeEach
    void preparar() {
        service = new GoogleLoginService(usuarioRepository, googleTokenService, passwordEncoder,
                new LimitsProperties(2, 5, 10, 5, 100));
    }

    @Test
    void comContaExistenteEntraSemCriarNovoUsuario() {
        Usuario existente = new Usuario();
        existente.setId(UUID.randomUUID());
        existente.setEmailCanonico("pessoa@gmail.com");
        when(googleTokenService.verificar("credencial"))
                .thenReturn(new GoogleTokenService.GooglePerfil("pessoa@gmail.com", "Pessoa Teste", true));
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(existente));

        Usuario resultado = service.entrarOuCriar("credencial");

        assertThat(resultado).isSameAs(existente);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void semContaExistenteCriaUmaNovaContaAtivaSemSenhaDefinida() {
        when(googleTokenService.verificar("credencial"))
                .thenReturn(new GoogleTokenService.GooglePerfil("pessoa@gmail.com", "Pessoa Teste", true));
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.empty());
        when(usuarioRepository.countByAtivoTrue()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hash-aleatorio");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Usuario resultado = service.entrarOuCriar("credencial");

        assertThat(resultado.getNome()).isEqualTo("Pessoa Teste");
        assertThat(resultado.getEmail()).isEqualTo("pessoa@gmail.com");
        assertThat(resultado.getEmailCanonico()).isEqualTo("pessoa@gmail.com");
        assertThat(resultado.isSenhaDefinida()).isFalse();
        assertThat(resultado.getStatus()).isEqualTo(StatusUsuario.ATIVO);
        assertThat(resultado.getTermosAceitosEm()).isNull();
    }

    @Test
    void comEmailNaoVerificadoNoGoogleLancaGoogleTokenInvalido() {
        when(googleTokenService.verificar("credencial"))
                .thenReturn(new GoogleTokenService.GooglePerfil("pessoa@gmail.com", "Pessoa Teste", false));

        assertThatThrownBy(() -> service.entrarOuCriar("credencial"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_INVALIDO));

        verify(usuarioRepository, never()).findByEmailCanonico(any());
    }

    @Test
    void comLimiteDeContasAtingidoNaoCriaNovaConta() {
        when(googleTokenService.verificar("credencial"))
                .thenReturn(new GoogleTokenService.GooglePerfil("pessoa@gmail.com", "Pessoa Teste", true));
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.empty());
        when(usuarioRepository.countByAtivoTrue()).thenReturn(100L);

        assertThatThrownBy(() -> service.entrarOuCriar("credencial"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.LIMITE_CONTAS_ATINGIDO));

        verify(usuarioRepository, never()).save(any());
    }
}
