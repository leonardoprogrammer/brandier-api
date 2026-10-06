package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.LojaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.LojasDaMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestLojaDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.LojaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/lojas")
public class LojaController {

    private final LojaService lojaService;
    private final CurrentUserResolver currentUserResolver;

    public LojaController(LojaService lojaService, CurrentUserResolver currentUserResolver) {
        this.lojaService = lojaService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<LojasDaMarcaDTO> listar(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        List<LojaDTO> lojas = lojaService.listar(gestor, marcaId).stream().map(LojaDTO::de).toList();
        return ResponseEntity.ok(new LojasDaMarcaDTO(lojas, lojaService.limiteDeLojas()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LojaDTO> buscar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                          Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(LojaDTO.de(lojaService.buscar(gestor, marcaId, id)));
    }

    @PostMapping
    public ResponseEntity<LojaDTO> criar(@PathVariable UUID marcaId,
                                         @Valid @RequestBody RequestLojaDTO requestLojaDTO,
                                         Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Loja loja = lojaService.criar(gestor, marcaId, requestLojaDTO.nome(), requestLojaDTO.cidade(),
                requestLojaDTO.uf(), requestLojaDTO.endereco());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(loja.getId()).toUri();
        return ResponseEntity.created(local).body(LojaDTO.de(loja));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LojaDTO> atualizar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                             @Valid @RequestBody RequestLojaDTO requestLojaDTO,
                                             Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Loja loja = lojaService.atualizar(gestor, marcaId, id, requestLojaDTO.nome(), requestLojaDTO.cidade(),
                requestLojaDTO.uf(), requestLojaDTO.endereco());
        return ResponseEntity.ok(LojaDTO.de(loja));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID marcaId, @PathVariable UUID id,
                                        Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        lojaService.excluir(gestor, marcaId, id);
        return ResponseEntity.noContent().build();
    }
}
