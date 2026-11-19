-- Controle de sessoes: toda redefinicao incrementa a versao das credenciais.
-- Tokens JWT anteriores passam a ser rejeitados sem guardar JWTs em claro.
ALTER TABLE usuarios
    ADD COLUMN versao_credenciais BIGINT NOT NULL DEFAULT 0;

ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_versao_credenciais
        CHECK (versao_credenciais >= 0);

-- Contadores atomicos compartilhados entre instancias.
-- Chaves = HMAC-SHA256, evitando email/IP em texto claro.
CREATE TABLE limites_recuperacao_senha (
    chave_hash VARCHAR(64) NOT NULL,
    janela_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    tentativas INTEGER NOT NULL CHECK (tentativas > 0),
    PRIMARY KEY (chave_hash, janela_inicio)
);

CREATE INDEX idx_limites_recuperacao_senha_janela
    ON limites_recuperacao_senha (janela_inicio);
