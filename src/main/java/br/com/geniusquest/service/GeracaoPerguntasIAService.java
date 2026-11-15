package br.com.geniusquest.service;

import br.com.geniusquest.service.ia.LoteGeradoIA;
import br.com.geniusquest.service.ia.ProvedorPerguntasIA;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Aplicação: não exposta por HTTP nesta subetapa. ADMIN será imposto na 2.2.3. */
@Service
public class GeracaoPerguntasIAService {
    private final ProvedorPerguntasIA provedor;
    private final PerguntasPersistenciaService persistencia;

    public GeracaoPerguntasIAService(ProvedorPerguntasIA provedor,
            PerguntasPersistenciaService persistencia) {
        this.provedor = provedor;
        this.persistencia = persistencia;
    }

    public List<UUID> gerarCandidatas(UUID temaId, int quantidade) {
        if (quantidade < 1 || quantidade > 10) {
            throw new IllegalArgumentException("Quantidade entre 1 e 10.");
        }
        var contexto = persistencia.contexto(temaId);
        LoteGeradoIA lote = provedor.gerar(contexto, quantidade);
        if (lote == null || lote.perguntas() == null || lote.perguntas().size() != quantidade) {
            throw new IllegalStateException("Provedor não gerou a quantidade solicitada.");
        }
        return persistencia.salvarCandidatas(temaId, lote, quantidade);
    }
}
