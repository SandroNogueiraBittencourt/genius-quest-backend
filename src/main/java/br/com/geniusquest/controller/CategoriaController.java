package br.com.geniusquest.controller;

import br.com.geniusquest.dto.categoria.CategoriaAtualizacaoDTO;
import br.com.geniusquest.dto.categoria.CategoriaCadastroDTO;
import br.com.geniusquest.dto.categoria.CategoriaResponseDTO;
import br.com.geniusquest.service.CategoriaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @Operation(summary = "Cadastrar uma nova categoria")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Categoria cadastrada com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Categoria já cadastrada"
            )
    })
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> cadastrar(
            @Valid @RequestBody CategoriaCadastroDTO dto) {

        CategoriaResponseDTO categoria =
                service.cadastrar(dto);

        URI location = URI.create(
                "/api/categorias/" + categoria.id()
        );

        return ResponseEntity
                .created(location)
                .body(categoria);
    }

    @Operation(summary = "Listar todas as categorias")
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar() {

        return ResponseEntity.ok(
                service.listar()
        );
    }

    @Operation(summary = "Listar categorias aprovadas")
    @GetMapping("/aprovadas")
    public ResponseEntity<List<CategoriaResponseDTO>> listarAprovadas() {

        return ResponseEntity.ok(
                service.listarAprovadas()
        );
    }

    @Operation(summary = "Listar categorias pendentes")
    @GetMapping("/pendentes")
    public ResponseEntity<List<CategoriaResponseDTO>> listarPendentes() {

        return ResponseEntity.ok(
                service.listarPendentes()
        );
    }

    @Operation(summary = "Listar categorias removidas")
    @GetMapping("/removidas")
    public ResponseEntity<List<CategoriaResponseDTO>> listarRemovidas() {

        return ResponseEntity.ok(
                service.listarRemovidas()
        );
    }

    @Operation(summary = "Buscar categoria por ID")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoria encontrada"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoria não encontrada"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                service.buscarPorId(id)
        );
    }

    @Operation(summary = "Atualizar uma categoria")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoria atualizada com sucesso"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoria não encontrada"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Categoria já cadastrada"
            )
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CategoriaAtualizacaoDTO dto) {

        return ResponseEntity.ok(
                service.atualizar(id, dto)
        );
    }

    @Operation(summary = "Aprovar uma categoria")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoria aprovada com sucesso"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoria não encontrada"
            )
    })
    @PatchMapping("/{id}/aprovar")
    public ResponseEntity<CategoriaResponseDTO> aprovar(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                service.aprovar(id)
        );
    }

    @Operation(summary = "Remover uma categoria")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Categoria removida com sucesso"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Categoria não encontrada"
            )
    })
    @PatchMapping("/{id}/remover")
    public ResponseEntity<CategoriaResponseDTO> remover(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                service.remover(id)
        );
    }
}
