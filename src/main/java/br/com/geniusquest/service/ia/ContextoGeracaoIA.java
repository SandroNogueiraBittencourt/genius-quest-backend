package br.com.geniusquest.service.ia;

import java.util.UUID;

/** Apenas o assunto aprovado, sem dados pessoais, é enviado ao provedor externo. */
public record ContextoGeracaoIA(UUID temaId, String tituloTema, String nomeCategoria) { }
