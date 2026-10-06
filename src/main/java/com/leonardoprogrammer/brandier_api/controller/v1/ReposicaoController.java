package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.PaginaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestReposicaoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService;
import com.leonardoprogrammer.brandier_api.service.ReposicaoService.ItemEntrada;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/reposicoes")
public class ReposicaoController {

    private final ReposicaoService reposicaoService;
    private final CurrentUserResolver currentUserResolver;

    public ReposicaoController(ReposicaoService reposicaoService, CurrentUserResolver currentUserResolver) {
        this.reposicaoService = reposicaoService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<PaginaDTO<ReposicaoDTO>> listar(@PathVariable UUID marcaId,
                                                          @RequestParam(defaultValue = "0") int pagina,
                                                          @RequestParam(defaultValue = "20") int tamanho,
                                                          Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(reposicaoService.listarDaMarca(usuario, marcaId, pagina, tamanho));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReposicaoDTO> buscar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                               Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(reposicaoService.buscar(usuario, marcaId, id));
    }

    @PostMapping
    public ResponseEntity<ReposicaoDTO> criar(@PathVariable UUID marcaId,
                                              @Valid @RequestBody RequestReposicaoDTO requisicao,
                                              Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        ReposicaoDTO criada = reposicaoService.criar(usuario, marcaId, requisicao.lojaId(), requisicao.repostoEm(),
                itens(requisicao), requisicao.observacoes());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReposicaoDTO> atualizar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                                  @Valid @RequestBody RequestReposicaoDTO requisicao,
                                                  Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(reposicaoService.atualizar(usuario, marcaId, id, requisicao.lojaId(),
                requisicao.repostoEm(), itens(requisicao), requisicao.observacoes()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID marcaId, @PathVariable UUID id,
                                        Authentication authentication) {
        Usuario usuario = currentUserResolver.resolve(authentication);
        reposicaoService.excluir(usuario, marcaId, id);
        return ResponseEntity.noContent().build();
    }

    private static List<ItemEntrada> itens(RequestReposicaoDTO requisicao) {
        return requisicao.itens().stream()
                .map(item -> new ItemEntrada(item.produtoId(), item.quantidade()))
                .toList();
    }
}
