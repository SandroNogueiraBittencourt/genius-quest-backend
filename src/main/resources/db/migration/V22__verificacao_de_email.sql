ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE codigos_verificacao_email (
    usuario_id UUID PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    codigo_hash CHAR(64) NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL
);
