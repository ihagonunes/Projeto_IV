# ADR-24 — Project

## Status

ACCEPTED

## Contexto

Project aparece nos dados e é útil como dimensão analítica, mas não há requisito de gestão completa de projetos.

## Decisão

Manter módulo Project próprio com cadastro/ciclo de vida mínimo e Department opcional quando aplicável. Não modelar tasks, members, managers ou workflow no MVP.

## Alternativas consideradas

Project dentro de Department; módulo completo de gestão de projetos; remover Project.

## Consequências

Preserva identidade e segmentação sem inflar escopo.
