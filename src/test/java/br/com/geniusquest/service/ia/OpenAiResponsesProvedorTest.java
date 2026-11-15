package br.com.geniusquest.service.ia;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesProvedorTest {
    private final OpenAiResponsesProvedor client = new OpenAiResponsesProvedor(
            JsonMapper.builder().build(), "chave-falsa-nao-utilizada", "modelo-de-teste");

    @Test
    void interpretaMensagemEstruturadaSemFazerRequisicao() {
        String payload = """
                {"perguntas":[{"enunciado":"Qual planeta é conhecido como planeta vermelho?",
                "alternativas":["Marte","Vênus","Júpiter","Saturno"],
                "respostaCorretaIndice":0,"dificuldade":"FACIL","fonteReferencia":null}]}
                """;
        String resposta = """
                {"status":"completed","output":[{"type":"message","content":[
                  {"type":"output_text","text":%s}
                ]}]}
                """.formatted(JsonMapper.builder().build().writeValueAsString(payload));
        var lote = client.interpretar(resposta, 1);
        assertEquals("modelo-de-teste", lote.modelo());
        assertEquals(1, lote.perguntas().size());
        assertEquals(0, lote.perguntas().get(0).respostaCorretaIndice());
        assertNull(lote.perguntas().get(0).fonteReferencia());
    }

    @Test
    void recusaRespostaIncompletaEContagemErrada() {
        assertThrows(IllegalStateException.class,
                () -> client.interpretar("{\"status\":\"incomplete\",\"output\":[]}", 1));
        assertThrows(IllegalStateException.class,
                () -> client.interpretar("{\"status\":\"completed\",\"output\":[]}", 1));
    }
}
