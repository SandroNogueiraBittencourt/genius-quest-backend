package br.com.geniusquest.dto.partida;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PartidaCadastroDTO(@NotNull UUID cicloId) {}
