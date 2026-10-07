package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteRecebidoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ConvitesRecebidosDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestTokenConviteDTO;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ConviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Convites recebidos", description = "Convites de promotor do ponto de vista de quem foi convidado, por token (link do e-mail) ou pela lista de pendentes do próprio e-mail.")
@RestController
@RequestMapping(ApiVersion.V1 + "/convites")
public class ConviteRecebidoController {

    private final ConviteService conviteService;
    private final CurrentUserResolver currentUserResolver;

    public ConviteRecebidoController(ConviteService conviteService, CurrentUserResolver currentUserResolver) {
        this.conviteService = conviteService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista os convites pendentes do meu e-mail", description = "Só os pendentes, não expirados e de marcas que não sejam do próprio usuário; nunca devolve o token.")
    @GetMapping("/recebidos")
    public ResponseEntity<ConvitesRecebidosDTO> recebidos(Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(new ConvitesRecebidosDTO(conviteService.listarRecebidos(usuario)));
    }

    @Operation(summary = "Aceita um convite pela lista de pendentes", description = "Convite inexistente, de outro e-mail, expirado ou já respondido responde 404, como qualquer recurso de outro usuário.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "TOKEN_CONVITE_INVALIDO (corrida de dois cliques)"),
    })
    @PostMapping("/{id}/aceitar")
    public ResponseEntity<VinculoPromotorDTO> aceitarPorId(@PathVariable UUID id, Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.aceitarPorId(usuario, id));
    }

    @Operation(summary = "Recusa um convite pela lista de pendentes")
    @PostMapping("/{id}/recusar")
    public ResponseEntity<Void> recusarPorId(@PathVariable UUID id, Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        conviteService.recusarPorId(usuario, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consulta um convite pelo token", description = "Usado pela tela que abre a partir do link do e-mail. O token sempre vai no corpo, nunca na URL. O convite só vale para a conta cujo e-mail canônico é o do convite.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "TOKEN_CONVITE_INVALIDO"),
    })
    @PostMapping("/consultar")
    public ResponseEntity<ConviteRecebidoDTO> consultar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                                        Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.consultar(usuario, requestDTO.token()));
    }

    @Operation(summary = "Aceita um convite pelo token")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "TOKEN_CONVITE_INVALIDO"),
    })
    @PostMapping("/aceitar")
    public ResponseEntity<VinculoPromotorDTO> aceitar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                                      Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.aceitar(usuario, requestDTO.token()));
    }

    @Operation(summary = "Recusa um convite pelo token")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "TOKEN_CONVITE_INVALIDO"),
    })
    @PostMapping("/recusar")
    public ResponseEntity<Void> recusar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                        Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        conviteService.recusar(usuario, requestDTO.token());
        return ResponseEntity.noContent().build();
    }
}
