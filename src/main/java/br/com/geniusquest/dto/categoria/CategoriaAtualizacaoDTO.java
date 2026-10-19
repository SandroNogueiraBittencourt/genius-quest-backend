package br.com.geniusquest.dto.categoria;

import jakarta.validation.constraints.Size;

public record CategoriaAtualizacaoDTO(
   @Size (
      min = 2, 
      max = 80,
      message = "O nome da categoria deve possuir entre 2 e 80 caracteres")
      String nome,
   
   @Size (
      max = 255,
      message = "A descrição da categoria deve possuir no máximo 255 caracteres")
      String descricao,

      Boolean ativo
   ) {

    }
