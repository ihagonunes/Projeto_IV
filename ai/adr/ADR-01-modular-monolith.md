# ADR-01 — Modular Monolith

## Status

ACCEPTED

## Contexto

O MVP precisa demonstrar modularidade, baixo acoplamento e responsabilidade clara sem assumir o custo operacional de microservices.

## Decisão

Implementar um único backend Java/Spring Boot como Monólito Modular, com um deploy lógico e PostgreSQL compartilhado. As fronteiras de módulo são explícitas e independentes de processo.

## Alternativas consideradas

Microservices desde o início; monólito sem fronteiras.

## Consequências

Simplifica deploy e transações locais, mas exige disciplina de dependências e testes arquiteturais para impedir erosão modular.
