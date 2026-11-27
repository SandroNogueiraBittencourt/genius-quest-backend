package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PartidaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired CicloVotacaoRepository ciclos;
    @Autowired ResultadoTemaCicloRepository resultados;
    @Autowired PartidaRepository partidas;
    @Autowired PerguntaRepository perguntas;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Usuario primeiro;
    private Usuario segundo;
    private Tema tema;
    private CicloVotacao ciclo;

    @BeforeEach
    void preparar() {
        primeiro = usuario("criador");
        segundo = usuario("outro");
        Categoria categoria = new Categoria();
        categoria.setNome("Animais " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        tema = new Tema();
        tema.setTitulo("Animais Selvagens");
        tema.setTituloNormalizado("animais selvagens");
        tema.setCategoria(categoria);
        tema.setAutor(primeiro);
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);

        ciclo = ciclo("Votação Animais", StatusCicloVotacao.ENCERRADO, true);
        resultados.saveAndFlush(new ResultadoTemaCiclo(
                ciclo.getId(), tema.getId(), 1, "Animais Selvagens", 3, 3, false));
    }

    private Usuario usuario(String prefixo) {
        Usuario usuario = new Usuario();
        usuario.setNome(prefixo);
        usuario.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha("hash-para-teste");
        usuario.setPerfil(PerfilUsuario.USUARIO);
        return usuarios.saveAndFlush(usuario);
    }

    private CicloVotacao ciclo(String nome, StatusCicloVotacao status, boolean comVencedor) {
        CicloVotacao novo = new CicloVotacao();
        novo.setNome(nome + " " + UUID.randomUUID());
        novo.setInicioEm(Instant.now().minusSeconds(7200));
        novo.setFimEm(Instant.now().minusSeconds(3600));
        novo.setStatus(status);
        if (status == StatusCicloVotacao.ENCERRADO) novo.setEncerradoEm(Instant.now());
        if (comVencedor) novo.setVencedor(tema);
        return ciclos.saveAndFlush(novo);
    }

    private String pedido(UUID id) {
        return "{\"cicloId\":\"" + id + "\"}";
    }

    @Test
    void endpointsExigemAutenticacao() throws Exception {
        mvc.perform(post("/api/partidas").contentType(MediaType.APPLICATION_JSON)
                .content(pedido(ciclo.getId()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/minhas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/minhas").param("page", "0"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/partidas/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criaPartidaComTemaDefinidoPeloServidorEStatusPreparacao() throws Exception {
        mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/partidas/")))
                .andExpect(jsonPath("$.cicloId").value(ciclo.getId().toString()))
                .andExpect(jsonPath("$.temaId").value(tema.getId().toString()))
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"))
                .andExpect(jsonPath("$.status").value("PREPARACAO"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void bloqueiaCicloInexistenteAbertoOuSemVencedor() throws Exception {
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        CicloVotacao aberto = ciclo("Aberto", StatusCicloVotacao.ABERTO, false);
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(aberto.getId())))
                .andExpect(status().isConflict());

        CicloVotacao semVencedor = ciclo("Sem vencedor", StatusCicloVotacao.ENCERRADO, false);
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(semVencedor.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void validaPayloadDePartida() throws Exception {
        mvc.perform(post("/api/partidas").with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protegeConsultasDePartidaPorCriador() throws Exception {
        String id = criarPartidaDoPrimeiroUsuario();

        mvc.perform(get("/api/partidas/{id}", id)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"));

        mvc.perform(get("/api/partidas/{id}", id)
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/partidas/minhas")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(get("/api/partidas/minhas")
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void paginaApenasPartidasDoCriador() throws Exception {
        for (Usuario criador : new Usuario[] {primeiro, primeiro, segundo}) {
            mvc.perform(post("/api/partidas")
                    .with(user(criador.getEmail()).roles("USUARIO"))
                    .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/partidas/minhas").param("page", "1").param("size", "1")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/partidas/minhas").param("page", "1").param("size", "1")
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void rejeitaTamanhoDePaginaZero() throws Exception {
        mvc.perform(get("/api/partidas/minhas").param("page", "0").param("size", "0")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaTamanhoDePaginaAcimaDoLimite() throws Exception {
        mvc.perform(get("/api/partidas/minhas").param("page", "0").param("size", "101")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginaPartidasAlemDoLimiteDaListaComOrdemEstavel() throws Exception {
        for (int i = 0; i < 25; i++) {
            criarPartidaDoPrimeiroUsuario();
        }
        entityManager.flush();
        jdbc.update("UPDATE partidas SET criado_em = CURRENT_TIMESTAMP WHERE criador_id = ?", primeiro.getId());
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM partidas WHERE criador_id = ? ORDER BY criado_em DESC, id DESC",
                UUID.class, primeiro.getId());
        for (int pagina = 0; pagina < 3; pagina++) {
            int inicio = pagina * 10;
            int quantidade = Math.min(10, ids.size() - inicio);
            var resposta = mvc.perform(get("/api/partidas/minhas").param("page", String.valueOf(pagina))
                    .param("incluirSalas", "true").with(user(primeiro.getEmail()).roles("USUARIO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(quantidade))
                    .andExpect(jsonPath("$.page.totalElements").value(25))
                    .andExpect(jsonPath("$.page.totalPages").value(3));
            for (int i = 0; i < quantidade; i++) {
                resposta.andExpect(jsonPath("$.content[" + i + "].id").value(ids.get(inicio + i).toString()));
            }
        }
    }

    @Test
    void paginaPartidaPeloVinculoComASalaSemResultado() throws Exception {
        String rota = criarSalaComPartida();
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        String codigo = rota.substring(rota.lastIndexOf('/') + 1);
        UUID partidaId = jdbc.queryForObject("SELECT partida_id FROM salas WHERE codigo = ?",
                UUID.class, UUID.fromString(codigo));
        validarPartidaPaginada(segundo, partidaId.toString(), codigo);
        mvc.perform(get("/api/partidas/minhas").param("page", "0")
                .param("incluirSalas", "false").with(convidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    private void validarPartidaPaginada(Usuario jogador, String partidaId, String codigoSala) throws Exception {
        mvc.perform(get("/api/partidas/minhas").param("page", "0").param("size", "1")
                .param("incluirSalas", "true").with(user(jogador.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(partidaId))
                .andExpect(jsonPath("$.content[0].codigoSala").value(codigoSala))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void registraRespostasUmaVezEAvancaAteConcluirPartida() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        for (int numero = 1; numero <= 10; numero++) {
            Pergunta pergunta = criarPergunta("Pergunta " + numero, StatusPergunta.APROVADA, 4);
            pergunta.setDificuldadeInicial(numero <= 4 ? DificuldadePergunta.FACIL
                    : numero <= 7 ? DificuldadePergunta.MEDIA : DificuldadePergunta.DIFICIL);
            perguntas.saveAndFlush(pergunta);
        }
        var inicio = mvc.perform(post(rota + "/partida/iniciar").with(anfitriao))
                .andExpect(status().isOk()).andReturn();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        UUID partidaId = UUID.fromString(json.readTree(inicio.getResponse().getContentAsString())
                .get("partidaId").asText());
        String respostas = rota + "/partida/respostas";
        String atual = rota + "/partida/pergunta-atual";
        String resultado = rota + "/partidas/" + partidaId + "/resultado";
        mvc.perform(get(resultado).with(convidado)).andExpect(status().isConflict());
        mvc.perform(get(resultado).with(user(usuario("fora").getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        mvc.perform(post(respostas).with(anfitriao).contentType(MediaType.APPLICATION_JSON)
                .content(pedidoResposta(1, 4))).andExpect(status().isBadRequest());
        mvc.perform(post(respostas).with(anfitriao).contentType(MediaType.APPLICATION_JSON)
                .content(pedidoResposta(1, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perguntaAtual").value(2))
                .andExpect(jsonPath("$.acertou").doesNotExist());
        mvc.perform(post(respostas).with(anfitriao).contentType(MediaType.APPLICATION_JSON)
                .content(pedidoResposta(1, 0))).andExpect(status().isConflict());
        mvc.perform(post(respostas).with(convidado).contentType(MediaType.APPLICATION_JSON)
                .content(pedidoResposta(1, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perguntaAtual").value(2));
        mvc.perform(post(respostas).with(convidado).contentType(MediaType.APPLICATION_JSON)
                .content(pedidoResposta(1, 0))).andExpect(status().isConflict());
        mvc.perform(get(atual).with(convidado)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2));
        mvc.perform(get(atual).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(2));

        for (int numero = 2; numero <= 10; numero++) {
            mvc.perform(post(respostas).with(anfitriao).contentType(MediaType.APPLICATION_JSON)
                    .content(pedidoResposta(numero, 2))).andExpect(status().isOk());
            mvc.perform(post(respostas).with(convidado).contentType(MediaType.APPLICATION_JSON)
                    .content(pedidoResposta(numero, 0))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(numero == 10 ? "CONCLUIDA" : "EM_ANDAMENTO"));
        }

        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mvc.perform(get(atual).with(convidado)).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(20, jdbc.queryForObject(
                "SELECT COUNT(*) FROM partida_respostas WHERE partida_id = ?", Integer.class, partidaId));
        org.junit.jupiter.api.Assertions.assertEquals(10, jdbc.queryForObject(
                "SELECT COUNT(*) FROM partida_respostas WHERE partida_id = ? AND acertou", Integer.class, partidaId));

        var placar = mvc.perform(get(resultado).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacao.length()").value(2))
                .andExpect(jsonPath("$.classificacao[0].id").value(primeiro.getId().toString()))
                .andExpect(jsonPath("$.classificacao[0].pontos").value(190))
                .andExpect(jsonPath("$.classificacao[0].acertos").value(10))
                .andExpect(jsonPath("$.classificacao[0].erros").value(0))
                .andExpect(jsonPath("$.classificacao[0].posicao").value(1))
                .andExpect(jsonPath("$.classificacao[1].id").value(segundo.getId().toString()))
                .andExpect(jsonPath("$.classificacao[1].pontos").value(0))
                .andExpect(jsonPath("$.classificacao[1].erros").value(10))
                .andExpect(jsonPath("$.classificacao[1].posicao").value(2))
                .andReturn();
        String placarFinal = placar.getResponse().getContentAsString();
        mvc.perform(get(resultado).with(convidado)).andExpect(status().isOk())
                .andExpect(content().json(placarFinal));
        String codigoSala = rota.substring(rota.lastIndexOf('/') + 1);
        mvc.perform(get("/api/partidas/minhas").param("incluirSalas", "true").with(convidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(partidaId.toString()))
                .andExpect(jsonPath("$[0].codigoSala").value(codigoSala))
                .andExpect(jsonPath("$[0].status").value("CONCLUIDA"));

        validarPartidaPaginada(primeiro, partidaId.toString(), codigoSala);
        validarPartidaPaginada(segundo, partidaId.toString(), codigoSala);
        mvc.perform(get("/api/partidas/minhas").param("page", "0").param("incluirSalas", "true")
                .with(user(usuario("sem-vinculo").getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        entityManager.flush();
        String continuacao = rota + "/partidas/" + partidaId + "/continuacao";
        mvc.perform(put(continuacao).with(anfitriao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"continuar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AGUARDANDO"));
        mvc.perform(put(continuacao).with(convidado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"continuar\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        mvc.perform(get(rota).with(convidado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partidaId").doesNotExist())
                .andExpect(jsonPath("$.participantes.length()").value(2));
        mvc.perform(get(rota + "/votacao").with(convidado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aberta").value(true))
                .andExpect(jsonPath("$.totais").isEmpty());
        mvc.perform(get("/api/partidas/{id}", partidaId).with(anfitriao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mvc.perform(get(continuacao).with(convidado)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.favoraveis").value(2));
        mvc.perform(get("/api/partidas/minhas").param("incluirSalas", "true").with(convidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(partidaId.toString()))
                .andExpect(jsonPath("$[0].status").value("CONCLUIDA"));
        validarPartidaPaginada(segundo, partidaId.toString(), codigoSala);
        mvc.perform(get(resultado).with(convidado)).andExpect(status().isOk())
                .andExpect(content().json(placarFinal));
    }

    @Test
    void jogadoresEmpatadosFicamNaMesmaPosicao() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        for (int numero = 1; numero <= 10; numero++) {
            criarPergunta("Pergunta " + numero, StatusPergunta.APROVADA, 4);
        }
        var inicio = mvc.perform(post(rota + "/partida/iniciar").with(anfitriao))
                .andExpect(status().isOk()).andReturn();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        String partidaId = json.readTree(inicio.getResponse().getContentAsString()).get("partidaId").asText();
        for (int numero = 1; numero <= 10; numero++) {
            for (var jogador : List.of(anfitriao, convidado)) {
                mvc.perform(post(rota + "/partida/respostas").with(jogador)
                        .contentType(MediaType.APPLICATION_JSON).content(pedidoResposta(numero, 2)))
                        .andExpect(status().isOk());
            }
        }
        mvc.perform(get(rota + "/partidas/" + partidaId + "/resultado").with(convidado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacao[0].pontos").value(200))
                .andExpect(jsonPath("$.classificacao[1].pontos").value(200))
                .andExpect(jsonPath("$.classificacao[0].posicao").value(1))
                .andExpect(jsonPath("$.classificacao[1].posicao").value(1));
    }

    @Test
    void saidaNaoAlteraProgressoIndividualEAnfitriaoPodeFecharSala() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        mvc.perform(delete(rota).with(convidado)).andExpect(status().isForbidden());
        for (int numero = 1; numero <= 10; numero++) {
            criarPergunta("Pergunta " + numero, StatusPergunta.APROVADA, 4);
        }
        mvc.perform(post(rota + "/partida/iniciar").with(anfitriao)).andExpect(status().isOk());
        mvc.perform(post(rota + "/partida/respostas").with(anfitriao)
                .contentType(MediaType.APPLICATION_JSON).content(pedidoResposta(1, 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perguntaAtual").value(2));
        mvc.perform(delete(rota + "/entrada").with(convidado)).andExpect(status().isNoContent());
        mvc.perform(get(rota).with(convidado)).andExpect(status().isNotFound());
        mvc.perform(get(rota + "/partida/pergunta-atual").with(anfitriao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(2));

        mvc.perform(delete(rota).with(anfitriao)).andExpect(status().isNoContent());
        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrada").value(true))
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isConflict());
    }

    @Test
    void concluiPartidaQuandoConvidadoPendenteSaiEAnfitriaoJaTerminou() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        for (int numero = 1; numero <= 10; numero++) {
            criarPergunta("Pergunta " + numero, StatusPergunta.APROVADA, 4);
        }
        var inicio = mvc.perform(post(rota + "/partida/iniciar").with(anfitriao))
                .andExpect(status().isOk()).andReturn();
        String partidaId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(inicio.getResponse().getContentAsString()).get("partidaId").asText();
        for (int numero = 1; numero <= 10; numero++) {
            mvc.perform(post(rota + "/partida/respostas").with(anfitriao)
                    .contentType(MediaType.APPLICATION_JSON).content(pedidoResposta(numero, 2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
        }

        mvc.perform(delete(rota + "/entrada").with(convidado)).andExpect(status().isNoContent());
        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mvc.perform(get(rota + "/partidas/" + partidaId + "/resultado").with(anfitriao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classificacao.length()").value(1))
                .andExpect(jsonPath("$.classificacao[0].acertos").value(10));
    }

    @Test
    void encerraVotacaoSemFecharSalaQuandoNaoHaMaioria() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        UUID codigo = UUID.fromString(rota.substring(rota.lastIndexOf('/') + 1));
        UUID partidaId = jdbc.queryForObject("SELECT partida_id FROM salas WHERE codigo = ?", UUID.class, codigo);
        String continuacao = rota + "/partidas/" + partidaId + "/continuacao";
        mvc.perform(get(continuacao).with(convidado)).andExpect(status().isConflict());
        jdbc.update("UPDATE partidas SET status = 'CONCLUIDA' WHERE id = ?", partidaId);
        entityManager.clear();

        mvc.perform(put(continuacao).with(convidado).contentType(MediaType.APPLICATION_JSON)
                .content("{\"continuar\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENCERRADA"));
        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value(partidaId.toString()))
                .andExpect(jsonPath("$.encerrada").value(false))
                .andExpect(jsonPath("$.participantes.length()").value(2))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
        mvc.perform(delete(rota + "/entrada").with(convidado)).andExpect(status().isNoContent());
        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrada").value(false))
                .andExpect(jsonPath("$.participantes.length()").value(1));
        mvc.perform(delete(rota).with(anfitriao)).andExpect(status().isNoContent());
        mvc.perform(get(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrada").value(true))
                .andExpect(jsonPath("$.status").value("CONCLUIDA"));
    }

    private String pedidoResposta(int numero, int alternativaIndice) {
        return "{\"numero\":" + numero + ",\"alternativaIndice\":" + alternativaIndice + "}";
    }

    @Test
    void iniciaComDezPerguntasAprovadasEServeAMesmaPerguntaAosParticipantes() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        criarPergunta("Pendente", StatusPergunta.PENDENTE, 4);
        criarPergunta("Aprovada incompleta", StatusPergunta.APROVADA, 3);
        Pergunta primeira = criarPergunta("Primeira pergunta válida", StatusPergunta.APROVADA, 4);
        for (int numero = 2; numero <= 11; numero++) {
            criarPergunta("Pergunta válida " + numero, StatusPergunta.APROVADA, 4);
        }
        jdbc.update("UPDATE perguntas SET criado_em = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")), primeira.getId());

        String iniciar = rota + "/partida/iniciar";
        String atual = rota + "/partida/pergunta-atual";
        mvc.perform(delete(rota + "/entrada").with(convidado)).andExpect(status().isNoContent());
        mvc.perform(post(iniciar).with(anfitriao)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("São necessários pelo menos 2 jogadores para iniciar a partida."));
        mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk());
        mvc.perform(get(atual).with(convidado)).andExpect(status().isConflict());
        mvc.perform(post(iniciar).with(convidado)).andExpect(status().isForbidden());
        var resposta = mvc.perform(post(iniciar).with(anfitriao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.totalPerguntas").value(10))
                .andExpect(jsonPath("$.enunciado").value("Primeira pergunta válida"))
                .andExpect(jsonPath("$.alternativas[0]").value("Opção 1"))
                .andExpect(jsonPath("$.alternativas.length()").value(4))
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist())
                .andReturn();

        primeira.setEnunciado("Pergunta alterada após o início");
        perguntas.saveAndFlush(primeira);
        String perguntaPublica = resposta.getResponse().getContentAsString();
        mvc.perform(get(atual).with(convidado))
                .andExpect(status().isOk()).andExpect(content().json(perguntaPublica));
        mvc.perform(post(iniciar).with(anfitriao))
                .andExpect(status().isOk()).andExpect(content().json(perguntaPublica));
        mvc.perform(get(atual).with(user("fora@geniusquest.test").roles("USUARIO")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(rota).with(convidado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    void mantemPartidaEmPreparacaoQuandoFaltamPerguntasValidas() throws Exception {
        String rota = criarSalaComPartida();
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk());
        for (int numero = 1; numero <= 9; numero++) {
            criarPergunta("Pergunta válida " + numero, StatusPergunta.APROVADA, 4);
        }
        criarPergunta("Pendente", StatusPergunta.PENDENTE, 4);

        mvc.perform(post(rota + "/partida/iniciar").with(anfitriao)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(
                        "São necessárias pelo menos 10 perguntas aprovadas e válidas para iniciar a partida"));
        mvc.perform(get(rota).with(anfitriao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PREPARACAO"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("SELECT COUNT(*) FROM partida_perguntas", Integer.class));
    }

    private String criarSalaComPartida() throws Exception {
        String partidaId = criarPartidaDoPrimeiroUsuario();
        var sala = mvc.perform(post("/api/partidas/{partidaId}/sala", partidaId)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk()).andReturn();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        return "/api/salas/" + json.readTree(sala.getResponse().getContentAsString()).get("codigo").asText();
    }

    private Pergunta criarPergunta(String enunciado, StatusPergunta status, int totalAlternativas) {
        Pergunta pergunta = new Pergunta();
        pergunta.setTema(tema);
        pergunta.setEnunciado(enunciado);
        pergunta.setEnunciadoNormalizado(enunciado.toLowerCase(java.util.Locale.ROOT));
        pergunta.setStatus(status);
        pergunta.setOrigem(OrigemPergunta.MANUAL);
        pergunta.setDificuldadeInicial(DificuldadePergunta.MEDIA);
        pergunta.setRespostaCorretaIndice(2);
        for (int posicao = 0; posicao < totalAlternativas; posicao++) {
            pergunta.adicionarAlternativa(posicao, "Opção " + (posicao + 1));
        }
        return perguntas.saveAndFlush(pergunta);
    }

    @Test
    void votacaoDaSalaPermiteTrocarRetirarEDesempatar() throws Exception {
        var anfitriao = user(primeiro.getEmail()).roles("USUARIO");
        var convidado = user(segundo.getEmail()).roles("USUARIO");
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        var sala = mvc.perform(post("/api/salas").with(anfitriao))
                .andExpect(status().isCreated()).andReturn();
        String codigo = json.readTree(sala.getResponse().getContentAsString()).get("codigo").asText();
        String rota = "/api/salas/" + codigo + "/votacao";
        mvc.perform(post("/api/salas/{codigo}/entrada", codigo).with(convidado))
                .andExpect(status().isOk());
        mvc.perform(post(rota).with(convidado)).andExpect(status().isForbidden());
        mvc.perform(post(rota).with(anfitriao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aberta").value(true));

        Tema outro = new Tema();
        outro.setTitulo("Oceanos");
        outro.setTituloNormalizado("oceanos");
        outro.setAutor(primeiro);
        outro.setCategoria(tema.getCategoria());
        outro.setStatus(StatusTema.APROVADO);
        temas.saveAndFlush(outro);

        String escolha = json.writeValueAsString(java.util.Map.of("temaId", tema.getId()));
        String outraEscolha = json.writeValueAsString(java.util.Map.of("temaId", outro.getId()));
        mvc.perform(put(rota + "/voto").with(anfitriao).contentType(MediaType.APPLICATION_JSON).content(escolha))
                .andExpect(status().isOk());
        mvc.perform(put(rota + "/voto").with(convidado).contentType(MediaType.APPLICATION_JSON).content(escolha))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totais[0].votos").value(2));
        mvc.perform(delete(rota + "/voto").with(convidado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totais[0].votos").value(1));
        mvc.perform(put(rota + "/voto").with(convidado).contentType(MediaType.APPLICATION_JSON).content(outraEscolha))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totais.length()").value(2));
        mvc.perform(post(rota + "/encerrar").with(anfitriao)).andExpect(status().isConflict());
        mvc.perform(post(rota + "/encerrar").with(anfitriao).param("desempate", outro.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temaId").value(outro.getId().toString()));
        mvc.perform(delete(rota + "/voto").with(convidado)).andExpect(status().isConflict());
    }

    @Test
    void criaSalaSemPartidaEAnfitriaoEscolheTemaAprovadoSemVotacao() throws Exception {
        var criacao = mvc.perform(post("/api/salas").with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partidaId").doesNotExist())
                .andExpect(jsonPath("$.temaId").doesNotExist())
                .andReturn();
        var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        String codigo = json.readTree(criacao.getResponse().getContentAsString()).get("codigo").asText();

        mvc.perform(post("/api/salas/{codigo}/entrada", codigo).with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").doesNotExist())
                .andExpect(jsonPath("$.participantes.length()").value(2));

        String escolha = "{\"temaId\":\"" + tema.getId() + "\"}";
        mvc.perform(put("/api/salas/{codigo}/tema", codigo).with(user(segundo.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(escolha))
                .andExpect(status().isForbidden());

        var vinculacao = mvc.perform(put("/api/salas/{codigo}/tema", codigo)
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(escolha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temaId").value(tema.getId().toString()))
                .andReturn();
        String partidaId = json.readTree(vinculacao.getResponse().getContentAsString()).get("partidaId").asText();

        mvc.perform(put("/api/salas/{codigo}/tema", codigo).with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(escolha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value(partidaId));
        mvc.perform(get("/api/salas/{codigo}", codigo).with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value(partidaId));
        mvc.perform(get("/api/partidas/{id}", partidaId).with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cicloId").doesNotExist())
                .andExpect(jsonPath("$.temaId").value(tema.getId().toString()));
        mvc.perform(get("/api/partidas/minhas").with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/partidas/minhas").param("incluirSalas", "true")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(partidaId))
                .andExpect(jsonPath("$[0].cicloId").doesNotExist());
        validarPartidaPaginada(primeiro, partidaId, codigo);
        mvc.perform(get("/api/partidas/minhas").param("page", "0")
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(post("/api/partidas/{id}/iniciar", partidaId)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isConflict());
    }

    @Test
    void compartilhaLobbyEntreDuasContasSemDuplicarParticipantes() throws Exception {
        String partidaId = criarPartidaDoPrimeiroUsuario();
        String rotaCriacao = "/api/partidas/" + partidaId + "/sala";
        mvc.perform(post(rotaCriacao)).andExpect(status().isUnauthorized());
        mvc.perform(post(rotaCriacao).with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());

        var criacao = mvc.perform(post(rotaCriacao).with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value(partidaId))
                .andExpect(jsonPath("$.anfitriaoId").value(primeiro.getId().toString()))
                .andExpect(jsonPath("$.status").value("PREPARACAO"))
                .andExpect(jsonPath("$.participantes[0].id").value(primeiro.getId().toString()))
                .andReturn();
        String codigo = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(criacao.getResponse().getContentAsString()).get("codigo").asText();
        mvc.perform(post(rotaCriacao).with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo));

        String rotaSala = "/api/salas/" + codigo;
        mvc.perform(get(rotaSala)).andExpect(status().isUnauthorized());
        mvc.perform(get(rotaSala).with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
        mvc.perform(post(rotaSala + "/entrada")).andExpect(status().isUnauthorized());

        for (int tentativa = 0; tentativa < 2; tentativa++) {
            mvc.perform(post(rotaSala + "/entrada").with(user(segundo.getEmail()).roles("USUARIO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantes.length()").value(2));
        }

        for (Usuario jogador : List.of(primeiro, segundo)) {
            mvc.perform(get(rotaSala).with(user(jogador.getEmail()).roles("USUARIO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantes[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(
                            primeiro.getId().toString(), segundo.getId().toString())));
        }
    }

    @Test
    void partidaEmAndamentoBloqueiaNovasEntradasEMantemAnfitriao() throws Exception {
        String partidaId = criarPartidaDoPrimeiroUsuario();
        var criacao = mvc.perform(post("/api/partidas/{partidaId}/sala", partidaId)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andReturn();
        String codigo = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(criacao.getResponse().getContentAsString()).get("codigo").asText();

        jdbc.update("UPDATE partidas SET status = ? WHERE id = ?",
                StatusPartida.EM_ANDAMENTO.name(), UUID.fromString(partidaId));
        entityManager.clear();

        mvc.perform(post("/api/salas/{codigo}/entrada", codigo)
                .with(user(segundo.getEmail()).roles("USUARIO")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/salas/{codigo}/entrada", codigo)
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.participantes.length()").value(1));
    }

    @Test
    void limitaSalaA14JogadoresEPreservaReconexao() throws Exception {
        String rota = criarSalaComPartida();
        var primeiroConvidado = user(segundo.getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(primeiroConvidado)).andExpect(status().isOk());
        for (int total = 3; total <= 14; total++) {
            var convidado = user(usuario("convidado-" + total).getEmail()).roles("USUARIO");
            mvc.perform(post(rota + "/entrada").with(convidado)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.participantes.length()").value(total));
        }
        var excedente = user(usuario("excedente").getEmail()).roles("USUARIO");
        mvc.perform(post(rota + "/entrada").with(excedente)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("A sala está cheia. O limite é de 14 jogadores."));
        mvc.perform(post(rota + "/entrada").with(primeiroConvidado)).andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(14));

        mvc.perform(delete(rota + "/entrada").with(primeiroConvidado)).andExpect(status().isNoContent());
        mvc.perform(post(rota + "/entrada").with(excedente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(14));
    }

    @Test
    void rejeitaEntradaComCodigoInexistente() throws Exception {
        mvc.perform(post("/api/salas/{codigo}/entrada", UUID.randomUUID())
                .with(user(primeiro.getEmail()).roles("USUARIO")))
                .andExpect(status().isNotFound());
    }

    private String criarPartidaDoPrimeiroUsuario() throws Exception {
        var resultado = mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(resultado.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void preservaTituloHistoricoMesmoAposAlteracaoDoTema() throws Exception {
        tema.setTitulo("Título alterado");
        temas.saveAndFlush(tema);

        mvc.perform(post("/api/partidas")
                .with(user(primeiro.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(ciclo.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temaTitulo").value("Animais Selvagens"));
    }
}
