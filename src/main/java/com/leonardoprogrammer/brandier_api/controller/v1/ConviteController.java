package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestConviteDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ConviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Convites", description = "Convites de promotor enviados pelo gestor de uma marca (ver \"Convites recebidos\" para o lado do convidado).")
@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/convites")
public class ConviteController {

    private final ConviteService conviteService;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    public ConviteController(ConviteService conviteService, CurrentUserResolver currentUserResolver, Clock clock) {
        this.conviteService = conviteService;
        this.currentUserResolver = currentUserResolver;
        this.clock = clock;
    }

    @Operation(summary = "Convida um e-mail para ser promotor da marca", description = "Só o gestor. O token do convite nunca volta na resposta; o e-mail é enviado de forma assíncrona, exista ou não conta para esse e-mail. O gestor não pode convidar o próprio e-mail.")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "LIMITE_PROMOTORES_ATINGIDO, CONVITE_JA_PENDENTE ou PROMOTOR_JA_VINCULADO"),
            @ApiResponse(responseCode = "400", description = "CONVITE_PROPRIO_EMAIL"),
    })
    @PostMapping
    public ResponseEntity<ConviteDTO> convidar(@PathVariable UUID marcaId,
                                               @Valid @RequestBody RequestConviteDTO requestConviteDTO,
                                               Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        ConvitePromotor convite = conviteService.convidar(gestor, marcaId, requestConviteDTO.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConviteDTO.de(convite, Instant.now(clock)));
    }

    @Operation(summary = "Cancela um convite pendente", description = "Só o gestor.")
    @DeleteMapping("/{conviteId}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID marcaId, @PathVariable UUID conviteId,
                                         Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        conviteService.cancelar(gestor, marcaId, conviteId);
        return ResponseEntity.noContent().build();
    }
}
