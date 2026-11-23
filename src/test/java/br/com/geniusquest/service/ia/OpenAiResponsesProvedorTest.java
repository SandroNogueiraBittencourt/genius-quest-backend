package br.com.geniusquest.service.ia;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesProvedorTest {
    private final OpenAiResponsesProvedor client = new OpenAiResponsesProvedor(
            JsonMapper.builder().build(), "chave-falsa-nao-utilizada", "modelo-de-teste");

    @Test
    void preparaRequisicaoResponsesParaQuizSemChamadaPaga() {
        String enunciado = "Qual planeta é chamado de \"planeta vermelho\"?\nConsidere o Sistema Solar.";
        var contexto = new ContextoGeracaoIA(
                UUID.randomUUID(), "Astronomia", "Ciências", List.of(enunciado));
        JsonMapper json = JsonMapper.builder().build();
        JsonNode requisicao = json.readTree(client.montarRequisicao(contexto, 1));

        assertEquals("modelo-de-teste", requisicao.path("model").asString());
        assertEquals("none", requisicao.path("reasoning").path("effort").asString());
        assertTrue(requisicao.has("store"));
        assertFalse(requisicao.path("store").asBoolean());
        assertEquals(4500, requisicao.path("max_output_tokens").asInt());
        JsonNode formato = requisicao.path("text").path("format");
        assertEquals("json_schema", formato.path("type").asString());
        assertTrue(formato.path("strict").asBoolean());
        assertTrue(formato.path("schema").has("additionalProperties"));
        assertFalse(formato.path("schema").path("additionalProperties").asBoolean());
        assertTrue(formato.path("schema").path("properties")
                .path("perguntas").path("items").has("additionalProperties"));
        assertFalse(formato.path("schema").path("properties")
                .path("perguntas").path("items").path("additionalProperties").asBoolean());
        JsonNode entrada = json.readTree(requisicao.path("input").asString());
        assertEquals(4, entrada.size());
        assertEquals("Astronomia", entrada.path("tema").asString());
        assertEquals("Ciências", entrada.path("categoria").asString());
        assertEquals(1, entrada.path("quantidade").asInt());
        assertEquals(json.valueToTree(List.of(enunciado)), entrada.path("enunciadosExistentes"));
    }

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

    @Test
    void extraiSomenteCodigosDeErroConhecidosParaDiagnostico() {
        assertEquals("insufficient_quota",
                client.codigoErroSeguro("""
                        {"error":{"code":"insufficient_quota","message":"detalhe privado"}}
                        """));
        assertEquals("project_spend_limit_exceeded",
                client.codigoErroSeguro("""
                        {"error":{"type":"insufficient_quota","code":"project_spend_limit_exceeded"}}
                        """));
        assertEquals("desconhecido",
                client.codigoErroSeguro("""
                        {"error":{"code":"dado privado inesperado"}}
                        """));
        assertEquals("desconhecido", client.codigoErroSeguro("corpo invalido"));
        assertEquals("desconhecido", client.codigoErroSeguro(""));
        assertEquals("desconhecido", client.codigoErroSeguro("{\"error\":{\"code\":null}}"));
    }
}
