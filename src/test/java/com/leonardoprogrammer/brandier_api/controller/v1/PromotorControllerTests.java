package com.leonardoprogrammer.brandier_api.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.exception.GlobalExceptionHandler;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.PromotorService;
import com.leonardoprogrammer.brandier_api.service.PromotorService.PromotoresDaMarca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PromotorControllerTests {

    private static final Instant AGORA = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private PromotorService promotorService;

    @Mock
    private CurrentUserResolver currentUserResolver;

    private MockMvc mockMvc;
    private Usuario gestor;
    private UsernamePasswordAuthenticationToken autenticacao;
    private Marca marca;
    private String base;

    @BeforeEach
    void preparar() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        MappingJackson2HttpMessageConverter conversor = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PromotorController(promotorService, currentUserResolver),
                        new PromotorEuController(promotorService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(conversor)
                .build();
        gestor = new Usuario();
        gestor.setId(UUID.randomUUID());
        gestor.setNome("Gestor Silva");
        autenticacao = UsernamePasswordAuthenticationToken.authenticated("gestor@exemplo.com", null, List.of());
        lenient().when(currentUserResolver.resolve(any())).thenReturn(gestor);
        marca = new Marca();
        marca.setId(UUID.randomUUID());
        marca.setNome("Marca Alfa");
        marca.setGestor(gestor);
        base = "/v1/marcas/" + marca.getId() + "/promotores";
    }

    private Promotor promotor(String nome, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha("hash-da-senha");
        Promotor promotor = new Promotor();
        promotor.setId(UUID.randomUUID());
        promotor.setMarca(marca);
        promotor.setUsuario(usuario);
        promotor.setEntrouEm(AGORA);
        return promotor;
    }

    private ConvitePromotor convite(String email, StatusConvite status, Instant expiraEm) {
        ConvitePromotor convite = new ConvitePromotor();
        convite.setId(UUID.randomUUID());
        convite.setEmail(email);
        convite.setTokenHash("hash-secreto");
        convite.setStatus(status);
        convite.setCriadoEm(AGORA.minusSeconds(10));
        convite.setExpiraEm(expiraEm);
        return convite;
    }

    @Test
    void listarDevolvePromotoresConvitesComStatusEfetivoELimite() throws Exception {
        PromotoresDaMarca dados = new PromotoresDaMarca(
                List.of(promotor("Ana", "ana@exemplo.com")),
                List.of(convite("novo@exemplo.com", StatusConvite.PENDENTE, AGORA.plusSeconds(60)),
                        convite("velho@exemplo.com", StatusConvite.PENDENTE, AGORA.minusSeconds(60)),
                        convite("nao@exemplo.com", StatusConvite.RECUSADO, AGORA.plusSeconds(60))),
                AGORA, 2, 5);
        when(promotorService.listarDaMarca(gestor, marca.getId())).thenReturn(dados);

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(5))
                .andExpect(jsonPath("$.usadas").value(2))
                .andExpect(jsonPath("$.promotores[0].usuarioId").exists())
                .andExpect(jsonPath("$.promotores[0].nome").value("Ana"))
                .andExpect(jsonPath("$.promotores[0].email").value("ana@exemplo.com"))
                .andExpect(jsonPath("$.promotores[0].entrouEm").value("2026-03-01T12:00:00Z"))
                .andExpect(jsonPath("$.promotores[0].senha").doesNotExist())
                .andExpect(jsonPath("$.convites[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.convites[1].status").value("EXPIRADO"))
                .andExpect(jsonPath("$.convites[2].status").value("RECUSADO"))
                .andExpect(jsonPath("$.convites[0].tokenHash").doesNotExist());
    }

    @Test
    void listarMarcaDeOutroGestorRetorna404() throws Exception {
        when(promotorService.listarDaMarca(gestor, marca.getId()))
                .thenThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO));

        mockMvc.perform(get(base).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void removerRetorna204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(promotorService).remover(gestor, marca.getId(), id);
    }

    @Test
    void removerPromotorInexistenteRetorna404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO)).when(promotorService).remover(gestor, marca.getId(), id);

        mockMvc.perform(delete(base + "/" + id).principal(autenticacao))
                .andExpect(status().isNotFound());
    }

    @Test
    void meusVinculosDevolveAsMarcasOndeOUsuarioEPromotor() throws Exception {
        when(promotorService.meusVinculos(gestor)).thenReturn(List.of(new VinculoPromotorDTO(
                marca.getId(), "Marca Alfa", "Gestor Silva", AGORA)));

        mockMvc.perform(get("/v1/promotores/eu").principal(autenticacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vinculos.length()").value(1))
                .andExpect(jsonPath("$.vinculos[0].marcaId").value(marca.getId().toString()))
                .andExpect(jsonPath("$.vinculos[0].marcaNome").value("Marca Alfa"))
                .andExpect(jsonPath("$.vinculos[0].gestorNome").value("Gestor Silva"))
                .andExpect(jsonPath("$.vinculos[0].entrouEm").value("2026-03-01T12:00:00Z"));
    }

    @Test
    void sairRetorna204() throws Exception {
        mockMvc.perform(delete("/v1/promotores/eu/" + marca.getId()).principal(autenticacao))
                .andExpect(status().isNoContent());

        verify(promotorService).sair(gestor, marca.getId());
    }

    @Test
    void sairDeMarcaSemVinculoRetorna404() throws Exception {
        doThrow(new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO)).when(promotorService).sair(gestor, marca.getId());

        mockMvc.perform(delete("/v1/promotores/eu/" + marca.getId()).principal(autenticacao))
                .andExpect(status().isNotFound());
    }
}
