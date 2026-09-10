package br.com.qsoft.governance.iam.api.model;

/**
 * Escopo organizacional de autorização derivado do IAM (§6.3 / §14.6).
 */
public enum AuthorizationScope {
    SELF,
    DEPARTMENT,
    AREA,
    TENANT
}
