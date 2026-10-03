package br.com.geniusquest.controller;

import br.com.geniusquest.dto.usuario.UsuarioCadastroDTO;
import br.com.geniusquest.dto.usuario.UsuarioResponseDTO;
import br.com.geniusquest.service.UsuarioService;

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
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService service;

	public UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@Operation(summary = "Cadastrar um novo usuário")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Usuário cadastrado com sucesso"),
			@ApiResponse(responseCode = "400", description = "Dados inválidos"),
			@ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
	})
	@PostMapping
	public ResponseEntity<UsuarioResponseDTO> cadastrar(
			@Valid @RequestBody UsuarioCadastroDTO dto) {

		UsuarioResponseDTO usuario = service.cadastrar(dto);

		URI location = URI.create(
				"/api/usuarios/" + usuario.id());

		return ResponseEntity
				.created(location)
				.body(usuario);
	}

	@Operation(summary = "Listar usuários")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Usuários listados com sucesso")
	})
	@GetMapping
	public ResponseEntity<List<UsuarioResponseDTO>> listar() {

		return ResponseEntity.ok(
				service.listar());
	}

	@Operation(summary = "Buscar usuário por ID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Usuário encontrado"),
			@ApiResponse(responseCode = "404", description = "Usuário não encontrado")
	})
	@GetMapping("/{id}")
	public ResponseEntity<UsuarioResponseDTO> buscarPorId(
			@PathVariable UUID id) {

		return ResponseEntity.ok(
				service.buscarPorId(id));
	}
}
