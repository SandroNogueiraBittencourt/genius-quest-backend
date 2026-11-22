package br.com.geniusquest.dto.partida;

import br.com.geniusquest.entity.DificuldadePergunta;
import java.util.List;
import java.util.UUID;

/**
 * Visão somente leitura de uma candidata aprovada para o jogador.
 * Não incluir gabarito, fonte, dados de moderação ou identificadores de alternativas.
 */
public record PerguntaQuizDTO(
        UUID id,
        String enunciado,
        List<String> alternativas,
        DificuldadePergunta dificuldadeInicial
) {}
