package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcaAcessoServiceTests {

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private PromotorRepository promotorRepository;

    private MarcaAcessoService service;
    private Usuario gestor;
    private Marca marca;

    @BeforeEach
    void preparar() {
        service = new MarcaAcessoService(marcaRepository, promotorRepository);
        gestor = usuario();
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setGestor(gestor);
    }

    private static Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        return usuario;
    }

    @Test
    void gestorDaMarcaTemAcessoDeGestorSemConsultarPromotores() {
        when(marcaRepository.findById(marca.getId())).thenReturn(Optional.of(marca));

        MarcaAcessoService.Acesso acesso = service.exigir(gestor, marca.getId());

        assertThat(acesso.gestor()).isTrue();
        assertThat(acesso.marca()).isSameAs(marca);
        verify(promotorRepository, never()).existsByMarcaIdAndUsuarioId(any(), any());
    }

    @Test
    void promotorDaMarcaTemAcessoSemSerGestor() {
        Usuario promotor = usuario();
        when(marcaRepository.findById(marca.getId())).thenReturn(Optional.of(marca));
        when(promotorRepository.existsByMarcaIdAndUsuarioId(marca.getId(), promotor.getId())).thenReturn(true);

        MarcaAcessoService.Acesso acesso = service.exigir(promotor, marca.getId());

        assertThat(acesso.gestor()).isFalse();
        assertThat(acesso.marca()).isSameAs(marca);
    }

    @Test
    void usuarioSemVinculoRecebeNaoEncontrado() {
        Usuario estranho = usuario();
        when(marcaRepository.findById(marca.getId())).thenReturn(Optional.of(marca));
        when(promotorRepository.existsByMarcaIdAndUsuarioId(marca.getId(), estranho.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.exigir(estranho, marca.getId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }

    @Test
    void marcaInexistenteRecebeNaoEncontrado() {
        UUID id = UUID.randomUUID();
        when(marcaRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exigir(gestor, id))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        erro -> assertThat(erro.getErrorCode()).isEqualTo(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }
}
