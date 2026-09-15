# Genius Quest Backend

API REST do **Genius Quest**, desenvolvida com Java, Spring Boot, PostgreSQL, Flyway e Swagger/OpenAPI.

## Requisitos

- Java 17+
- Docker e Docker Compose

## 1. Configurar ambiente

```bash
cp .env.example .env
```

Altere principalmente `POSTGRES_PASSWORD` e `DB_PASSWORD` para o mesmo valor.

## 2. Subir PostgreSQL

```bash
docker compose up -d
```

## 3. Exportar variaveis para execucao local

O Spring nao carrega `.env` automaticamente ao executar diretamente pelo Maven. Exporte as variaveis antes de iniciar:

```bash
set -a
source .env
set +a
```

## 4. Executar backend

```bash
chmod +x mvnw
./mvnw spring-boot:run
```

## Endpoints iniciais

- Health: http://localhost:8080/api/health
- Swagger: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Docker da aplicacao

```bash
docker build -t genius-quest-backend .
```

Ao executar o container, informe as variaveis `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD`.

## Estrutura

```text
src/main/java/br/com/geniusquest/
├── config
├── controller
├── dto
├── entity
├── exception
├── mapper
├── repository
├── security
└── service
```

> As migrations do banco devem ser criadas em `src/main/resources/db/migration`.
