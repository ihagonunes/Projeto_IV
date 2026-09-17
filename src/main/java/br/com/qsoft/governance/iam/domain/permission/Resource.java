package br.com.qsoft.governance.iam.domain.permission;

/**
 * Recursos protegidos pela matriz de permissões por nível hierárquico (POPROJ-52).
 * Catálogo inicial; expandir conforme os endpoints reais entram (Sprint 2).
 */
public enum Resource {
    TENANT,
    USER,
    MODEL,
    MODEL_POLICY,
    USAGE_RECORD,
    PERMISSION_MATRIX
}