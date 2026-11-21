# Genius Quest — homologação da curadoria (entrada manual)

## Objetivo

Permitir testar a fila de curadoria sem pagar por geração de IA. O backend já
tem os endpoints de revisão, aprovação, rejeição, bloqueio e histórico, mas
o banco de produção começou esta etapa sem nenhuma candidata. Esta alteração
adiciona uma forma ADMIN de criar uma candidata manual, sempre **PENDENTE**.

## Novo contrato

`POST /api/admin/perguntas`, JWT ADMIN obrigatório.

```json
{
  "temaId": "UUID_DO_TEMA_APROVADO",
  "enunciado": "Qual planeta do Sistema Solar é conhecido como planeta vermelho?",
  "alternativas": ["Marte", "Vênus", "Júpiter", "Saturno"],
  "respostaCorretaIndice": 0,
  "dificuldadeInicial": "FACIL",
  "fonteReferencia": "Material didático de revisão"
}
```

Resposta HTTP 201: `{"id":"UUID_DA_PERGUNTA"}`. Não retorna gabarito.

Regras: tema precisa estar aprovado; quatro alternativas válidas; enunciado
normalizado sem duplicata ativa por tema; candidatos `MANUAL/PENDENTE`;
nenhuma publicação automática no jogo.

Outros status esperados: 401 não autenticado; 403 sem ADMIN; 400 entrada
inválida; 404 tema inexistente; 409 tema não aprovado ou pergunta duplicada;
422 problema estrutural do enunciado/alternativas.

## Verificação recomendada

1. CI do backend em PR: testes Maven e build Docker.
2. Em ambiente de homologação, ADMIN cria uma candidata com tema aprovado.
3. Abrir `/?view=questions-admin`, filtrar pendentes e verificar a pergunta.
4. Testar uma revisão (motivo obrigatório), aprovação e histórico.
5. Criar **outras** candidatas independentes para rejeitar (com motivo) e
   bloquear (com motivo). Não é permitido aprovar pergunta rejeitada.
6. Verificar que contas comuns recebem 403 e não acessam gabarito/auditoria.

## Segurança e implantação

- Este recurso **não faz chamada à OpenAI** e não exige `OPENAI_API_KEY`.
- A geração automática segue sem endpoint e desativada por padrão.
- Não introduz nova migration.
- **Não inserir dados de homologação em produção sem autorização explícita.**
- A criação é um endpoint mutável: nunca usar como sonda de health check.
- Não incluir senha, JWT ou chave API em commits, prints ou relatórios.
