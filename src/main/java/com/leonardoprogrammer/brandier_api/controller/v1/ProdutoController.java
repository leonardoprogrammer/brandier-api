package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.ProdutoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ProdutosDaMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestProdutoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ProdutoService;
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
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final CurrentUserResolver currentUserResolver;

    public ProdutoController(ProdutoService produtoService, CurrentUserResolver currentUserResolver) {
        this.produtoService = produtoService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public ResponseEntity<ProdutosDaMarcaDTO> listar(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        List<ProdutoDTO> produtos = produtoService.listar(gestor, marcaId).stream().map(ProdutoDTO::de).toList();
        return ResponseEntity.ok(new ProdutosDaMarcaDTO(produtos, produtoService.limiteDeProdutos()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDTO> buscar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                             Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(ProdutoDTO.de(produtoService.buscar(gestor, marcaId, id)));
    }

    @PostMapping
    public ResponseEntity<ProdutoDTO> criar(@PathVariable UUID marcaId,
                                            @Valid @RequestBody RequestProdutoDTO requestProdutoDTO,
                                            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Produto produto = produtoService.criar(gestor, marcaId, requestProdutoDTO.nome(), requestProdutoDTO.codigo());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(produto.getId()).toUri();
        return ResponseEntity.created(local).body(ProdutoDTO.de(produto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDTO> atualizar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                                @Valid @RequestBody RequestProdutoDTO requestProdutoDTO,
                                                Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Produto produto = produtoService.atualizar(gestor, marcaId, id, requestProdutoDTO.nome(),
                requestProdutoDTO.codigo());
        return ResponseEntity.ok(ProdutoDTO.de(produto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID marcaId, @PathVariable UUID id,
                                        Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        produtoService.excluir(gestor, marcaId, id);
        return ResponseEntity.noContent().build();
    }
}
