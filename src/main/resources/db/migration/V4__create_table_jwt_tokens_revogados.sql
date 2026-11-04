CREATE TABLE jwt_tokens_revogados (
    token_hash VARCHAR(64) PRIMARY KEY,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jwt_tokens_revogados_expira_em
    ON jwt_tokens_revogados (expira_em);
