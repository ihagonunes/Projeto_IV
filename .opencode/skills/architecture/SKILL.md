---
name: architecture
description: Valida mudanças contra o Monólito Modular QSOFT e ADRs.
---

# Architecture

Antes de mudar arquitetura:

1. leia `ai/architecture.md`;
2. localize ADR em `ai/decisions.md`;
3. identifique módulo proprietário;
4. confirme allowlist e superfície `api.*`;
5. verifique tenant/persistência/segurança;
6. prefira abstração existente.

Exige ADR quando a mudança altera estilo, ownership, persistência, segurança, contratos públicos, dependências cross-module ou infraestrutura mandatória.

Nunca introduza microservice, H2, broker, RAG obrigatório, provider SDK ou storage novo somente por possibilidade futura.
