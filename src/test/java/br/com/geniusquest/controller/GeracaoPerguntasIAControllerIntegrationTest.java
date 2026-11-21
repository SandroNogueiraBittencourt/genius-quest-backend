package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.service.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.ia.enabled=true", "app.ia.http-enabled=true",
        "app.cors.allowed-origins=http://localhost:5173"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class GeracaoPerguntasIAControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired GeracaoIAQuotaService cotas;
    @MockitoBean GeracaoPerguntasIAService geracao;

    private Usuario admin;
    private Usuario comum;
    private UUID temaId;

    @BeforeEach
    void dados() {
        admin = usuario("admin", PerfilUsuario.ADMIN);
        comum = usuario("comum", PerfilUsuario.USUARIO);

        Categoria categoria = new Categoria();
        categoria.setNome("Categoria IA " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        Tema tema = new Tema();
        tema.setCategoria(categoria);
        tema.setAutor(admin);
        tema.setTitulo("Tema IA " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setStatus(StatusTema.APROVADO);
        temaId = temas.saveAndFlush(tema).getId();
    }

    private Usuario usuario(String nome, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(nome + "-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha("hash-falso");
        usuario.setPerfil(perfil);
        return usuarios.saveAndFlush(usuario);
    }

    private String pedido(int quantidade) {
        return "{\"temaId\":\"" + temaId + "\",\"quantidade\":" + quantidade + "}";
    }

    @Test
    void somenteAdminPodeConsultarOuGerarSemGabarito() throws Exception {
        UUID chave = UUID.randomUUID();
        mvc.perform(get("/api/admin/perguntas/geracao/status"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/perguntas/geracao/status")
                .with(user(comum.getEmail()).roles("USUARIO")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/perguntas/geracao")
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(comum.getEmail()).roles("USUARIO"))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/perguntas/geracao/status")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(true))
                .andExpect(jsonPath("$.maximoPorSolicitacao").value(3));

        UUID candidata = UUID.randomUUID();
        when(geracao.gerarCandidatas(temaId, 1)).thenReturn(List.of(candidata));
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.ids[0]").value(candidata.toString()))
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist());
        verify(geracao, times(1)).gerarCandidatas(temaId, 1);
    }

    @Test
    void chaveRepetidaNaoGeraNovoCustoNemNovoLote() throws Exception {
        UUID chave = UUID.randomUUID();
        when(geracao.gerarCandidatas(temaId, 1)).thenReturn(List.of(UUID.randomUUID()));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/admin/perguntas/geracao")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .header("Idempotency-Key", chave)
                    .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                    .andExpect(i == 0 ? status().isCreated() : status().isConflict());
        }
        assertEquals(1, cotas.usoHoje().requisicoes());
        assertEquals(1, cotas.usoHoje().perguntas());
        verify(geracao, times(1)).gerarCandidatas(temaId, 1);
    }

    @Test
    void cotasDiariasContamFalhasEImpedemQuintaChamada() throws Exception {
        when(geracao.gerarCandidatas(eq(temaId), anyInt()))
                .thenAnswer(invocation -> {
                    int n = invocation.getArgument(1);
                    List<UUID> ids = new ArrayList<>();
                    for (int i = 0; i < n; i++) ids.add(UUID.randomUUID());
                    return ids;
                });
        for (int quantidade : List.of(3, 3, 3, 1)) {
            mvc.perform(post("/api/admin/perguntas/geracao")
                    .with(user(admin.getEmail()).roles("ADMIN"))
                    .header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(pedido(quantidade)))
                    .andExpect(status().isCreated());
        }
        assertEquals(4, cotas.usoHoje().requisicoes());
        assertEquals(10, cotas.usoHoje().perguntas());
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isTooManyRequests());
        verify(geracao, times(4)).gerarCandidatas(eq(temaId), anyInt());
    }

    @Test
    void pedidoInvalidoNaoReservaOrcamento() throws Exception {
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(pedido(4)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isBadRequest());
        assertEquals(0, cotas.usoHoje().requisicoes());
        verifyNoInteractions(geracao);
    }

    @Test
    void falhaNaoRepeteAutomaticamenteENaoDevolveRespostaDoProvedor() throws Exception {
        UUID chave = UUID.randomUUID();
        when(geracao.gerarCandidatas(temaId, 1))
                .thenThrow(new IllegalStateException("chave-secreta-nao-deve-vazar"));
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensagem")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("chave-secreta"))));
        assertEquals(1, cotas.usoHoje().requisicoes());
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isConflict());
        verify(geracao, times(1)).gerarCandidatas(temaId, 1);
    }
    @Test
    void preflightPermiteCabecalhoDeIdempotenciaParaFrontend() throws Exception {
        mvc.perform(options("/api/admin/perguntas/geracao")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers",
                        "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
        verifyNoInteractions(geracao);
    }

    @Test
    void temaInexistenteOuSuspensoNaoConsomeQuotaNemChamaIA() throws Exception {
        UUID inexistente = UUID.randomUUID();
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\\"temaId\\":\\"" + inexistente + "\\",\\"quantidade\\":1}"))
                .andExpect(status().isNotFound());

        Tema tema = temas.findById(temaId).orElseThrow();
        tema.setStatus(StatusTema.SUSPENSO);
        temas.saveAndFlush(tema);
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(pedido(1)))
                .andExpect(status().isConflict());

        assertEquals(0, cotas.usoHoje().requisicoes());
        assertEquals(0, cotas.usoHoje().perguntas());
        verifyNoInteractions(geracao);
    }

}
