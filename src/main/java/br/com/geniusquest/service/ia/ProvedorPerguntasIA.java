package br.com.geniusquest.service.ia;

/** Porta para trocar o provedor sem alterar persistência, regras ou segurança. */
public interface ProvedorPerguntasIA {
    LoteGeradoIA gerar(ContextoGeracaoIA contexto, int quantidade);
}
