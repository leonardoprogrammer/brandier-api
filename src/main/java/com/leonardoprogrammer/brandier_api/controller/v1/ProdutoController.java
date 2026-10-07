package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.ProdutoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.ProdutosDaMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestProdutoDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Produto;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Produtos", description = "Catálogo de produtos de uma marca (limite de 10 ativos por marca; só o gestor escreve, promotores da marca podem ler os ativos).")
@RestController
@RequestMapping(ApiVersion.V1 + "/marcas/{marcaId}/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;
    private final CurrentUserResolver currentUserResolver;

    public ProdutoController(ProdutoService produtoService, CurrentUserResolver currentUserResolver) {
        this.produtoService = produtoService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista os produtos da marca", description = "Promotores só enxergam os produtos ativos; o gestor vê todos, inclusive inativos.")
    @GetMapping
    public ResponseEntity<ProdutosDaMarcaDTO> listar(@PathVariable UUID marcaId, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        List<ProdutoDTO> produtos = produtoService.listar(gestor, marcaId).stream().map(ProdutoDTO::de).toList();
        return ResponseEntity.ok(new ProdutosDaMarcaDTO(produtos, produtoService.limiteDeProdutos()));
    }

    @Operation(summary = "Busca um produto da marca")
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDTO> buscar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                             Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(ProdutoDTO.de(produtoService.buscar(gestor, marcaId, id)));
    }

    @Operation(summary = "Cria um produto no catálogo", description = "Só o gestor da marca. Respeita o limite de produtos ativos por marca; o nome é único por marca; a cor é opcional (hex `#RRGGBB`).")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "LIMITE_PRODUTOS_ATINGIDO ou PRODUTO_NOME_DUPLICADO"),
    })
    @PostMapping
    public ResponseEntity<ProdutoDTO> criar(@PathVariable UUID marcaId,
                                            @Valid @RequestBody RequestProdutoDTO requestProdutoDTO,
                                            Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Produto produto = produtoService.criar(gestor, marcaId, requestProdutoDTO.nome(), requestProdutoDTO.codigo(),
                requestProdutoDTO.cor());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(produto.getId()).toUri();
        return ResponseEntity.created(local).body(ProdutoDTO.de(produto));
    }

    @Operation(summary = "Atualiza um produto, podendo reativá-lo", description = "Só o gestor da marca. Enviar `ativo: true` reativa um produto inativado, respeitando o limite (que conta só ativos).")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "PRODUTO_NOME_DUPLICADO ou LIMITE_PRODUTOS_ATINGIDO (ao reativar)"),
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDTO> atualizar(@PathVariable UUID marcaId, @PathVariable UUID id,
                                                @Valid @RequestBody RequestProdutoDTO requestProdutoDTO,
                                                Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Produto produto = produtoService.atualizar(gestor, marcaId, id, requestProdutoDTO.nome(),
                requestProdutoDTO.codigo(), requestProdutoDTO.cor(), requestProdutoDTO.ativo());
        return ResponseEntity.ok(ProdutoDTO.de(produto));
    }

    @Operation(summary = "Exclui ou inativa um produto", description = "Só o gestor da marca. Produto sem reposições é apagado de verdade (204); produto com histórico é só inativado e volta no corpo (200), para preservar o histórico das reposições já registradas.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ProdutoDTO> excluir(@PathVariable UUID marcaId, @PathVariable UUID id,
                                              Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return produtoService.excluir(gestor, marcaId, id)
                .map(inativado -> ResponseEntity.ok(ProdutoDTO.de(inativado)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
