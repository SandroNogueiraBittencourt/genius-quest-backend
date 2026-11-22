CREATE TABLE token_recuperacao_senha (
    id          UUID PRIMARY KEY,
    usuario_id  UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expira_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    usado       BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL
);
