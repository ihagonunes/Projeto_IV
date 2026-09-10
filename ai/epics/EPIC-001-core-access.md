# EPIC-001 — Core da Plataforma e Gestão de Acessos
## Objetivo
Estabelecer Tenant, User, Department/Area, autenticação, autorização e isolamento multi-tenant.
## Módulos
`iam`, `department`, `sharedkernel`
## Critérios arquiteturais
- tenant-aware;
- JWT/BCrypt conforme ADR-30;
- sem regra de hierarquia duplicada fora do IAM;
- testes Tenant A × Tenant B.
