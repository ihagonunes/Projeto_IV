# ADR-21 — Missing Score Data

## Status

ACCEPTED

## Contexto

Ausência de evidência não pode ser confundida com redistribuição silenciosa de pesos.

## Decisão

Dimensão sem fonte/dados suficientes recebe score 0 + DataStatus=INSUFFICIENT_DATA. Pesos não são redistribuídos.

## Alternativas consideradas

Ignorar dimensão; reponderar automaticamente; usar valor neutro.

## Consequências

Score penaliza falta de evidência, mas o breakdown torna explícita a diferença entre ausência de dados e desempenho ruim.
