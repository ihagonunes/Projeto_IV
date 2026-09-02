-- V003 - Módulo IAM: adiciona o hash BCrypt da senha ao usuário (ADR-30).
-- Usuários criados sem senha ainda terão password_hash nulo.

ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(255);
