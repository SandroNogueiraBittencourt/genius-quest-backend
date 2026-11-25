package br.com.geniusquest.service;

import br.com.geniusquest.service.ia.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeracaoPerguntasIAServiceTest {
    final ProvedorPerguntasIA provedor = mock(ProvedorPerguntasIA.class);
    final PerguntasPersistenciaService persistencia = mock(PerguntasPersistenciaService.class);
    final GeracaoPerguntasIAService service = new GeracaoPerguntasIAService(provedor, persistencia);
    final UUID id = UUID.randomUUID();

    @Test
    void validaQuantidadeAntesDeConsultarOuConsumirIA() {
        assertThrows(IllegalArgumentException.class, () -> service.gerarCandidatas(id, 0));
        assertThrows(IllegalArgumentException.class, () -> service.gerarCandidatas(id, 11));
        verifyNoInteractions(provedor, persistencia);
    }

    @Test
    void falhaDoProvedorNaoPersisteNada() {
        var contexto = new ContextoGeracaoIA(id, "Tema", "Categoria", List.of());
        when(persistencia.contexto(id)).thenReturn(contexto);
        when(provedor.gerar(contexto, 2)).thenThrow(new IllegalStateException("IA indisponível"));
        assertThrows(IllegalStateException.class, () -> service.gerarCandidatas(id, 2));
        verify(persistencia, never()).salvarCandidatas(any(), any(), anyInt());
    }

    @Test
    void quantidadeIncorretaNaoPersisteLote() {
        var contexto = new ContextoGeracaoIA(id, "Tema", "Categoria", List.of());
        when(persistencia.contexto(id)).thenReturn(contexto);
        when(provedor.gerar(contexto, 2)).thenReturn(new LoteGeradoIA("modelo", List.of()));
        assertThrows(IllegalStateException.class, () -> service.gerarCandidatas(id, 2));
        verify(persistencia, never()).salvarCandidatas(any(), any(), anyInt());
    }

    @Test
    void somentePersistenciaRetornaIdsDepoisDaGeracao() {
        var contexto = new ContextoGeracaoIA(id, "Tema", "Categoria",
                List.of("Qual planeta é conhecido como planeta vermelho?"));
        var candidato = new CandidataGeradaIA("Enunciado longo o suficiente para passar",
                List.of("um", "dois", "três", "quatro"), 0,
                br.com.geniusquest.entity.DificuldadePergunta.FACIL, null);
        var lote = new LoteGeradoIA("modelo", List.of(candidato));
        var idPergunta = UUID.randomUUID();
        when(persistencia.contexto(id)).thenReturn(contexto);
        when(provedor.gerar(contexto, 1)).thenReturn(lote);
        when(persistencia.salvarCandidatas(id, lote, 1)).thenReturn(List.of(idPergunta));
        assertEquals(List.of(idPergunta), service.gerarCandidatas(id, 1));
        verify(persistencia).salvarCandidatas(id, lote, 1);
    }
}
