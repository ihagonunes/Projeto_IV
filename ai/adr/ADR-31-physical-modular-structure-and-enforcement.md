# ADR-31 — Physical Modular Structure and Enforcement

## Status

ACCEPTED

## Contexto

A arquitetura lógica precisa ser refletida no código sem multiplicar builds/serviços desnecessariamente.

## Decisão

Um único projeto Maven com packages de primeiro nível por módulo. Cada módulo organiza api/domain/application/infrastructure; outro módulo importa somente api.*. platform contém bootstrap/config técnica. ArchUnit aplica allowlist, regras de camada e ausência de ciclos.

## Alternativas consideradas

Maven multi-module no MVP; package por camada global; confiança apenas em convenção humana.

## Consequências

Estrutura é simples para equipe acadêmica e possui enforcement automático; violações arquiteturais falham o build.
