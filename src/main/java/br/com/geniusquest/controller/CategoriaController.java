package br.com.geniusquest.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geniusquest.dto.categoria.CategoriaDTO;
import br.com.geniusquest.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {
    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @Operation(summary = "Cadastrar categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Categoria já cadastrada")
    })
    @PostMapping
    public ResponseEntity<CategoriaDTO> cadastrar(@RequestBody CategoriaDTO dto) {
        CategoriaDTO criada = service.cadastrar(dto);

        return ResponseEntity
                .created(URI.create("/api/categorias/" + criada.id()))
                .body(criada);
    }

    @Operation(summary = "Listar todas as categorias")
    @ApiResponse(responseCode = "200", description = "Lista de categorias")
    @GetMapping
    public PagedModel<CategoriaDTO> listar(@PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listar(pageable));
    }

    @Operation(summary = "Listar categorias aprovadas")
    @ApiResponse(responseCode = "200", description = "Lista de categorias aprovadas")
    @GetMapping("/aprovadas")
    public PagedModel<CategoriaDTO> listarAprovadas(@PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listarAprovadas(pageable));
    }

    @Operation(summary = "Listar categorias pendentes de moderação")
    @ApiResponse(responseCode = "200", description = "Lista de categorias pendentes")
    @GetMapping("/pendentes")
    public PagedModel<CategoriaDTO> listarPendentes(@PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listarPendentes(pageable));
    }

    @Operation(summary = "Listar categorias removidas")
    @ApiResponse(responseCode = "200", description = "Lista de categorias removidas")
    @GetMapping("/removidas")
    public PagedModel<CategoriaDTO> listarRemovidas(@PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return new PagedModel<>(service.listarRemovidas(pageable));
    }

    @Operation(summary = "Buscar categoria por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @Operation(summary = "Atualizar categoria (parcial)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "409", description = "Nome já utilizado por outra categoria")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<CategoriaDTO> atualizar(
            @PathVariable UUID id,
            @RequestBody CategoriaDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }
}
