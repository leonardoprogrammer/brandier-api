package com.leonardoprogrammer.brandier_api.controller.v1;

import com.leonardoprogrammer.brandier_api.config.ApiVersion;
import com.leonardoprogrammer.brandier_api.model.dto.MarcaDTO;
import com.leonardoprogrammer.brandier_api.model.dto.MinhasMarcasDTO;
import com.leonardoprogrammer.brandier_api.model.dto.RequestMarcaDTO;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.security.CurrentUserResolver;
import com.leonardoprogrammer.brandier_api.service.MarcaService;
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

@Tag(name = "Marcas", description = "CRUD de marcas do gestor autenticado (limite de 2 por conta).")
@RestController
@RequestMapping(ApiVersion.V1 + "/marcas")
public class MarcaController {

    private final MarcaService marcaService;
    private final CurrentUserResolver currentUserResolver;

    public MarcaController(MarcaService marcaService, CurrentUserResolver currentUserResolver) {
        this.marcaService = marcaService;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "Lista as marcas do gestor", description = "Devolve só as marcas geridas pelo usuário autenticado, com o limite configurado.")
    @GetMapping
    public ResponseEntity<MinhasMarcasDTO> listar(Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        List<MarcaDTO> marcas = marcaService.listar(gestor).stream().map(MarcaDTO::de).toList();
        return ResponseEntity.ok(new MinhasMarcasDTO(marcas, marcaService.limiteDeMarcas()));
    }

    @Operation(summary = "Busca uma marca do gestor", description = "Marca de outro gestor responde 404, para não revelar que existe.")
    @GetMapping("/{id}")
    public ResponseEntity<MarcaDTO> buscar(@PathVariable UUID id, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(MarcaDTO.de(marcaService.buscar(gestor, id)));
    }

    @Operation(summary = "Cria uma marca", description = "Respeita o limite de marcas por gestor; o nome é único por gestor (sem diferenciar maiúsculas/minúsculas).")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "LIMITE_MARCAS_ATINGIDO ou MARCA_NOME_DUPLICADO"),
    })
    @PostMapping
    public ResponseEntity<MarcaDTO> criar(@Valid @RequestBody RequestMarcaDTO requestMarcaDTO,
                                          Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Marca marca = marcaService.criar(gestor, requestMarcaDTO.nome(), requestMarcaDTO.descricao());
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(marca.getId()).toUri();
        return ResponseEntity.created(local).body(MarcaDTO.de(marca));
    }

    @Operation(summary = "Atualiza o nome/descrição de uma marca")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "MARCA_NOME_DUPLICADO"),
    })
    @PutMapping("/{id}")
    public ResponseEntity<MarcaDTO> atualizar(@PathVariable UUID id,
                                              @Valid @RequestBody RequestMarcaDTO requestMarcaDTO,
                                              Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        Marca marca = marcaService.atualizar(gestor, id, requestMarcaDTO.nome(), requestMarcaDTO.descricao());
        return ResponseEntity.ok(MarcaDTO.de(marca));
    }

    @Operation(summary = "Exclui uma marca", description = "Marca sem nenhuma reposição é apagada de verdade; marca com histórico só é inativada, para preservar os relatórios de quem promoveu nela.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, Authentication authentication) {
        Usuario gestor = currentUserResolver.resolve(authentication);
        marcaService.excluir(gestor, id);
        return ResponseEntity.noContent().build();
    }
}
