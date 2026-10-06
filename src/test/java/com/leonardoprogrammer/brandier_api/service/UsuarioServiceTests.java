package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTests {

    @Mock
    private UsuarioRepository usuarioRepository;

    private UsuarioService usuarioService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarioService = new UsuarioService(usuarioRepository);
        usuario = new Usuario();
        usuario.setNome("Pessoa");
        usuario.setEmail("pe.ssoa@gmail.com");
        usuario.setEmailCanonico("pessoa@gmail.com");
        usuario.setSenha("hash-da-senha");
    }

    @Test
    void loadUserByUsernameEncontraAContaPorQualquerAliasDoMesmoEndereco() {
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(usuario));

        UserDetails porAlias = usuarioService.loadUserByUsername("  Pessoa+Brandier@GMAIL.com ");
        UserDetails porOutraGrafia = usuarioService.loadUserByUsername("p.e.s.s.o.a@gmail.com");

        assertThat(porAlias.getUsername()).isEqualTo("pe.ssoa@gmail.com");
        assertThat(porAlias.getPassword()).isEqualTo("hash-da-senha");
        assertThat(porOutraGrafia.getUsername()).isEqualTo("pe.ssoa@gmail.com");
    }

    @Test
    void loadUserByUsernameLancaExcecaoQuandoNaoExiste() {
        when(usuarioRepository.findByEmailCanonico("ninguem@exemplo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.loadUserByUsername("ninguem@exemplo.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void getUsuarioByEmailUsaOEmailCanonico() {
        when(usuarioRepository.findByEmailCanonico("pessoa@gmail.com")).thenReturn(Optional.of(usuario));

        assertThat(usuarioService.getUsuarioByEmail("PESSOA+x@googlemail.com")).isSameAs(usuario);
    }

    @Test
    void getUsuarioByEmailDevolveNuloQuandoNaoExiste() {
        when(usuarioRepository.findByEmailCanonico("ninguem@exemplo.com")).thenReturn(Optional.empty());

        assertThat(usuarioService.getUsuarioByEmail("ninguem@exemplo.com")).isNull();
    }
}
