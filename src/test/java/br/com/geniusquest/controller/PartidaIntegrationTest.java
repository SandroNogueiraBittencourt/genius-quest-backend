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
import org.springframework.security.test.context.support.WithMockUser;
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
