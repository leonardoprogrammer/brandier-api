package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteRecebidoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ConvitesRecebidosDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestTokenConviteDTO;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ConviteService;
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

@RestController
@RequestMapping(ApiVersion.V1 + "/convites")
public class ConviteRecebidoController {

    private final ConviteService conviteService;
    private final CurrentUserResolver currentUserResolver;

    public ConviteRecebidoController(ConviteService conviteService, CurrentUserResolver currentUserResolver) {
        this.conviteService = conviteService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/recebidos")
    public ResponseEntity<ConvitesRecebidosDTO> recebidos(Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(new ConvitesRecebidosDTO(conviteService.listarRecebidos(usuario)));
    }

    @PostMapping("/{id}/aceitar")
    public ResponseEntity<VinculoPromotorDTO> aceitarPorId(@PathVariable UUID id, Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.aceitarPorId(usuario, id));
    }

    @PostMapping("/{id}/recusar")
    public ResponseEntity<Void> recusarPorId(@PathVariable UUID id, Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        conviteService.recusarPorId(usuario, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/consultar")
    public ResponseEntity<ConviteRecebidoDTO> consultar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                                        Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.consultar(usuario, requestDTO.token()));
    }

    @PostMapping("/aceitar")
    public ResponseEntity<VinculoPromotorDTO> aceitar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                                      Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(conviteService.aceitar(usuario, requestDTO.token()));
    }

    @PostMapping("/recusar")
    public ResponseEntity<Void> recusar(@Valid @RequestBody RequestTokenConviteDTO requestDTO,
                                        Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        conviteService.recusar(usuario, requestDTO.token());
        return ResponseEntity.noContent().build();
    }
}
