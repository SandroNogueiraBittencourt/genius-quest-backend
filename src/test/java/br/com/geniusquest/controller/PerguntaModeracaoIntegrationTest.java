package br.com.geniusquest.controller;

import br.com.geniusquest.entity.*;
import br.com.geniusquest.repository.*;
import br.com.geniusquest.validation.ValidadorPergunta;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PerguntaModeracaoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired TemaRepository temas;
    @Autowired PerguntaRepository perguntas;
    @Autowired EventoCuradoriaPerguntaRepository eventos;

    private Usuario admin;
    private Usuario comum;
    private Pergunta pendente;

    @BeforeEach
    void preparar() {
        admin = usuario("admin", PerfilUsuario.ADMIN);
        comum = usuario("comum", PerfilUsuario.USUARIO);

        Categoria categoria = new Categoria();
        categoria.setNome("Ciência " + UUID.randomUUID());
        categoria.setStatus(StatusCategoria.APROVADA);
        categoria = categorias.saveAndFlush(categoria);

        Tema tema = new Tema();
        tema.setCategoria(categoria);
        tema.setAutor(comum);
        tema.setTitulo("Sistema solar " + UUID.randomUUID());
        tema.setTituloNormalizado(tema.getTitulo().toLowerCase(java.util.Locale.ROOT));
        tema.setStatus(StatusTema.APROVADO);
        tema = temas.saveAndFlush(tema);

        pendente = pergunta(tema, "Qual planeta é chamado de planeta vermelho? " + UUID.randomUUID());
    }

    private Usuario usuario(String prefixo, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome(prefixo);
        usuario.setEmail(prefixo + "-" + UUID.randomUUID() + "@geniusquest.test");
        usuario.setSenha("hash-de-teste");
        usuario.setPerfil(perfil);
        return usuarios.saveAndFlush(usuario);
    }

    private Pergunta pergunta(Tema tema, String enunciado) {
        var valido = new ValidadorPergunta().validar(enunciado,
                List.of("Marte", "Vênus", "Júpiter", "Saturno"), 0);
        Pergunta p = new Pergunta();
        p.setTema(tema);
        p.setEnunciado(valido.enunciado());
        p.setEnunciadoNormalizado(valido.enunciadoNormalizado());
        p.setRespostaCorretaIndice(valido.respostaCorretaIndice());
        p.setOrigem(OrigemPergunta.IA);
        p.setDificuldadeInicial(DificuldadePergunta.MEDIA);
        p.setFonteReferencia("Material de teste revisado pela curadoria.");
        for (int i = 0; i < 4; i++) p.adicionarAlternativa(i, valido.alternativas().get(i));
        return perguntas.saveAndFlush(p);
    }

    private String revisao() {
        return """
                {"enunciado":"Qual planeta vermelho é o quarto a partir do Sol?",
                 "alternativas":["Júpiter","Marte","Vênus","Urano"],
                 "respostaCorretaIndice":1,"dificuldadeInicial":"FACIL",
                 "fonteReferencia":"Material didático revisado",
                 "motivo":"Correção editorial da alternativa"}
                """;
    }

    @Test
    void endpointsNaoRevelamGabaritoSemAutenticacaoOuSemRoleAdmin() throws Exception {
        mvc.perform(get("/api/admin/perguntas"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/perguntas/{id}", pendente.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/perguntas/{id}/historico", pendente.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/perguntas").with(user(comum.getEmail()).roles("USUARIO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/perguntas/{id}", pendente.getId())
                .with(user(comum.getEmail()).roles("USUARIO")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/perguntas/{id}/historico", pendente.getId())
                .with(user(comum.getEmail()).roles("USUARIO")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/perguntas/{id}/revisar", pendente.getId())
                .with(user(comum.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(revisao()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(comum.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminConsultaPaginacaoEVisualizaGabaritoSomenteNaRotaAdministrativa() throws Exception {
        mvc.perform(get("/api/admin/perguntas?status=PENDENTE&page=0&size=10")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pendente.getId().toString()))
                .andExpect(jsonPath("$.content[0].respostaCorretaIndice").value(0));
        mvc.perform(get("/api/admin/perguntas/{id}", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternativas.length()").value(4));
        mvc.perform(get("/api/admin/perguntas?page=-1")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revisaoEPosteriorAprovacaoGeramDoisEventosEGuardamResponsavel() throws Exception {
        mvc.perform(patch("/api/admin/perguntas/{id}/revisar", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(revisao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respostaCorretaIndice").value(1))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\",\"motivo\":\"Conferido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.revisorId").value(admin.getId().toString()));

        mvc.perform(get("/api/admin/perguntas/{id}/historico", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].acao").value("REVISAR"))
                .andExpect(jsonPath("$[1].acao").value("APROVAR"))
                .andExpect(jsonPath("$[1].depois", containsString("APROVADA")));
    }

    @Test
    void recusaAprovarPerguntaIaSemFonteAteRevisaoDaCuradoria() throws Exception {
        pendente.setFonteReferencia(null);
        perguntas.saveAndFlush(pendente);

        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/admin/perguntas/{id}", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.fonteReferencia").value(nullValue()));

        mvc.perform(get("/api/admin/perguntas/{id}/historico", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(patch("/api/admin/perguntas/{id}/revisar", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(revisao()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonteReferencia").value("Material didático revisado"));

        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
    }

    @Test
    void permiteAprovarPerguntaManualSemFonteNaRegraAtual() throws Exception {
        pendente.setOrigem(OrigemPergunta.MANUAL);
        pendente.setFonteReferencia(null);
        perguntas.saveAndFlush(pendente);

        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
    }

    @Test
    void impedeAprovarNovamenteEImpedeEditarDepoisDeAprovada() throws Exception {
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/perguntas/{id}/revisar", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(revisao()))
                .andExpect(status().isConflict());
    }

    @Test
    void bloqueiaPerguntaAprovadaComJustificativa() throws Exception {
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"APROVAR\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"BLOQUEAR\",\"motivo\":\"Resposta controversa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoRevisao").value("Resposta controversa"));
    }

    @Test
    void exigeMotivoParaRejeitarEProibeAcaoRevisarComoDecisao() throws Exception {
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"REJEITAR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"REVISAR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/perguntas/{id}/decisao", pendente.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"REJEITAR\",\"motivo\":\"Fonte insuficiente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"));
    }

    @Test
    void recusaConteudoDuplicadoNaRevisao() throws Exception {
        Pergunta outra = pergunta(pendente.getTema(),
                "Qual outro planeta é chamado de vermelho? " + UUID.randomUUID());
        String revisaoDuplicada = """
                {"enunciado":"%s",
                 "alternativas":["Marte","Vênus","Júpiter","Saturno"],
                 "respostaCorretaIndice":0,"dificuldadeInicial":"MEDIA",
                 "motivo":"Ajuste editorial"}
                """.formatted(pendente.getEnunciado());
        mvc.perform(patch("/api/admin/perguntas/{id}/revisar", outra.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(revisaoDuplicada))
                .andExpect(status().isConflict());
    }

    private String criacaoManual(String enunciado) {
        return """
                {"temaId":"%s",
                 "enunciado":"%s",
                 "alternativas":["Marte","Vênus","Júpiter","Saturno"],
                 "respostaCorretaIndice":0,
                 "dificuldadeInicial":"FACIL",
                 "fonteReferencia":"Material de revisão para homologação"}
                """.formatted(pendente.getTema().getId(), enunciado);
    }

    @Test
    void somenteAdminPodeCriarCandidataManualPendenteSemExporGabaritoNaCriacao() throws Exception {
        String body = criacaoManual("Qual planeta é conhecido como planeta vermelho? " + UUID.randomUUID());
        mvc.perform(post("/api/admin/perguntas")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/perguntas")
                .with(user(comum.getEmail()).roles("USUARIO"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/perguntas")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.respostaCorretaIndice").doesNotExist());

        mvc.perform(get("/api/admin/perguntas?status=PENDENTE&page=0&size=10")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[1].origem").value("MANUAL"))
                .andExpect(jsonPath("$.content[1].status").value("PENDENTE"));
    }

    @Test
    void criacaoManualRejeitaDuplicadaEAlternativasIncompletas() throws Exception {
        mvc.perform(post("/api/admin/perguntas")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(criacaoManual(pendente.getEnunciado())))
                .andExpect(status().isConflict());

        String invalida = criacaoManual("Pergunta de teste invalida " + UUID.randomUUID())
                .replace("\"Marte\",\"Vênus\",\"Júpiter\",\"Saturno\"",
                        "\"Marte\",\"Vênus\",\"Júpiter\"");
        mvc.perform(post("/api/admin/perguntas")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(invalida))
                .andExpect(status().isBadRequest());
    }

}
