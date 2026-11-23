package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Jornada HTTP real: login com JWT assinado -> sala -> entrada -> votacao
 * -> 10 perguntas -> respostas -> continuacao -> encerramento.
 *
 * Somente usa o Postgres de testes (@ActiveProfiles("test"), CI postgres:18)
 * e dados sinteticos criados em transacao com rollback. Nunca usa Render,
 * servico de email, geracao IA, ou credenciais reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalaMultiplayerJwtFluxoCompletoIntegrationTest {
    private static final String SENHA = "SenhaApenasTeste123!";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired PerguntaRepository perguntas;
    @Autowired PasswordEncoder codificador;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Usuario anfitriao;
    private Usuario convidado;
    private Usuario intruso;
    private Tema tema;
    private String jwtAnfitriao;
    private String jwtConvidado;
    private String jwtIntruso;

    @BeforeEach
    void prepararCenario() throws Exception {
        anfitriao = criarUsuario("anfitriao");
        convidado = criarUsuario("convidado");
        intruso = criarUsuario("intruso");

        Categoria categoria = new Categoria();
        categoria.setNome("Teste multiplayer " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        tema = new Tema();
        tema.setTitulo("Quiz sintético de segurança " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setAutor(anfitriao);
        tema.setCategoria(categoria);
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);

        for (int numero = 1; numero <= 10; numero++) {
            Pergunta pergunta = new Pergunta();
            String enunciado = "Questão sintética " + numero + " " + UUID.randomUUID();
            pergunta.setTema(tema);
            pergunta.setEnunciado(enunciado);
            pergunta.setEnunciadoNormalizado(enunciado.toLowerCase(java.util.Locale.ROOT));
            pergunta.setRespostaCorretaIndice(2);
            pergunta.setStatus(StatusPergunta.APROVADA);
            pergunta.setOrigem(OrigemPergunta.MANUAL);
            pergunta.setDificuldadeInicial(DificuldadePergunta.MEDIA);
            for (int i = 0; i < 4; i++) {
                pergunta.adicionarAlternativa(i, "Alternativa " + i);
            }
            perguntas.saveAndFlush(pergunta);
        }

        jwtAnfitriao = login(anfitriao);
        jwtConvidado = login(convidado);
        jwtIntruso = login(intruso);
    }

    @Test
    void doisJogadoresComJwtRealConcluemPartidaEReabremVotacao() throws Exception {
        // O anfitriao cria o lobby; o terceiro usuario nao pode bisbilhotar.
        String sala = criarSala();
        mvc.perform(get(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isNotFound());
        mvc.perform(get(sala)).andExpect(status().isUnauthorized());

        mvc.perform(post(sala + "/entrada")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(2));
        mvc.perform(post(sala + "/entrada")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(2));

        String temaJson = "{\"temaId\":\"" + tema.getId() + "\"}";
        String votacao = sala + "/votacao";
        mvc.perform(post(votacao).header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isForbidden());
        mvc.perform(post(votacao).header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberta").value(true));
        mvc.perform(put(votacao + "/voto")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso))
                .contentType(MediaType.APPLICATION_JSON).content(temaJson))
                .andExpect(status().isNotFound());
        mvc.perform(put(votacao + "/voto")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao))
                .contentType(MediaType.APPLICATION_JSON).content(temaJson))
                .andExpect(status().isOk());
        mvc.perform(put(votacao + "/voto")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado))
                .contentType(MediaType.APPLICATION_JSON).content(temaJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais[0].votos").value(2));

        mvc.perform(post(votacao + "/encerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isForbidden());
        String partidaId = parse(mvc.perform(post(votacao + "/encerrar")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temaId").value(tema.getId().toString()))
                .andExpect(jsonPath("$.status").value("PREPARACAO"))
                .andReturn().getResponse().getContentAsString()).get("partidaId").asText();
        assertNotNull(partidaId);

        mvc.perform(post(sala + "/partida/iniciar")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isForbidden());
        mvc.perform(post(sala + "/partida/iniciar")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.totalPerguntas").value(10))
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist());

        mvc.perform(get(sala + "/partida/pergunta-atual")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist());
        mvc.perform(get(sala + "/partida/pergunta-atual")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isNotFound());
        mvc.perform(post(sala + "/partida/respostas")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso))
                .contentType(MediaType.APPLICATION_JSON).content(resposta(1, 2)))
                .andExpect(status().isNotFound());

        for (int numero = 1; numero <= 10; numero++) {
            mvc.perform(post(sala + "/partida/respostas")
                    .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao))
                    .contentType(MediaType.APPLICATION_JSON).content(resposta(numero, 2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.perguntaAtual").value(numero));
            if (numero == 1) {
                mvc.perform(post(sala + "/partida/respostas")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao))
                        .contentType(MediaType.APPLICATION_JSON).content(resposta(numero, 2)))
                        .andExpect(status().isConflict());
            }
            mvc.perform(post(sala + "/partida/respostas")
                    .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado))
                    .contentType(MediaType.APPLICATION_JSON).content(resposta(numero, 0)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status")
                            .value(numero == 10 ? "CONCLUIDA" : "EM_ANDAMENTO"));
        }

        mvc.perform(get(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mvc.perform(get(sala + "/partida/pergunta-atual")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isConflict());

        assertEquals(20, jdbc.queryForObject(
                "SELECT count(*) FROM partida_respostas WHERE partida_id = ?",
                Integer.class, UUID.fromString(partidaId)));
        assertEquals(10, jdbc.queryForObject(
                "SELECT count(*) FROM partida_respostas WHERE partida_id = ? AND acertou",
                Integer.class, UUID.fromString(partidaId)));

        entityManager.flush();
        String continuacao = sala + "/partidas/" + partidaId + "/continuacao";
        mvc.perform(put(continuacao)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao))
                .contentType(MediaType.APPLICATION_JSON).content("{\"continuar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO"));
        mvc.perform(put(continuacao)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado))
                .contentType(MediaType.APPLICATION_JSON).content("{\"continuar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
        mvc.perform(get(votacao).header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aberta").value(true));
        mvc.perform(get(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").doesNotExist());

        mvc.perform(delete(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtConvidado)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isNoContent());
        mvc.perform(get(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrada").value(true));
        mvc.perform(post(sala + "/entrada")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isConflict());
    }

    @Test
    void bloqueiaCriacaoEVotoSemJwtEProtegeLobbyDeIntruso() throws Exception {
        mvc.perform(post("/api/salas")).andExpect(status().isUnauthorized());
        String sala = criarSala();

        mvc.perform(post(sala + "/entrada")).andExpect(status().isUnauthorized());
        mvc.perform(get(sala + "/votacao")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isNotFound());
        mvc.perform(post(sala + "/votacao")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(sala)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtIntruso)))
                .andExpect(status().isNotFound());
        mvc.perform(get(sala).header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(1));
    }

    private Usuario criarUsuario(String prefixo) {
        Usuario usuario = new Usuario();
        usuario.setNome(prefixo);
        usuario.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha(codificador.encode(SENHA));
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(usuario);
    }

    private String login(Usuario usuario) throws Exception {
        String payload = JSON.writeValueAsString(
                java.util.Map.of("email", usuario.getEmail(), "senha", SENHA));
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return parse(body).get("token").asText();
    }

    private String criarSala() throws Exception {
        String body = mvc.perform(post("/api/salas")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtAnfitriao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participantes.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return "/api/salas/" + parse(body).get("codigo").asText();
    }

    private static JsonNode parse(String json) throws Exception {
        return JSON.readTree(json);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String resposta(int numero, int alternativa) {
        return "{\"numero\":" + numero + ",\"alternativaIndice\":" + alternativa + "}";
    }
}
