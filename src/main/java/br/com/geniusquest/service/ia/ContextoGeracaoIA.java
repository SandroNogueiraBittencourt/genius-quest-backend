package br.com.geniusquest.service.ia;

import java.util.List;
import java.util.UUID;

/** Contexto do tema aprovado e enunciados usados para evitar repetições. */
public record ContextoGeracaoIA(
        UUID temaId, String tituloTema, String nomeCategoria, List<String> enunciadosExistentes) {
    public ContextoGeracaoIA {
        enunciadosExistentes = List.copyOf(enunciadosExistentes);
    }
}
