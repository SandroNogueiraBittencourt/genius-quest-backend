package br.com.geniusquest.controller;

import br.com.geniusquest.service.GeracaoPerguntasIAService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GeracaoPerguntasIADesabilitadaIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean GeracaoPerguntasIAService geracao;

    @Test
    void endpointDesabilitadoPorPadraoNaoChamaProvedor() throws Exception {
        mvc.perform(get("/api/admin/perguntas/geracao/status")
                .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitada").value(false));
        mvc.perform(post("/api/admin/perguntas/geracao")
                .with(user("admin@example.test").roles("ADMIN"))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"temaId\":\"" + UUID.randomUUID() + "\",\"quantidade\":1}"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(geracao);
    }
}
