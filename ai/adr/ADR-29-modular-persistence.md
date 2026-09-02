# ADR-29 — Modular Persistence

## Status

ACCEPTED

## Contexto

Um único PostgreSQL precisa preservar ownership dos módulos e ser reproduzível em ambientes diferentes.

## Decisão

Cada módulo possui tabelas/repositories próprios; FKs apenas intramodule; cross-module por IDs/ports. Flyway gerencia schema. PostgreSQL é o único SGBD suportado. Execução pode ser local (Spring Boot + PostgreSQL local) ou Docker Compose (backend + PostgreSQL); H2 não é banco oficial.

## Alternativas consideradas

FKs cross-module; schema gerado por Hibernate update; H2 como banco alternativo oficial; banco por módulo.

## Consequências

Mantém semântica real do PostgreSQL em dev/teste e reduz acoplamento. Docker continua opcional no desenvolvimento local.
