package br.com.qsoft.governance.sharedkernel.tenant;

import br.com.qsoft.governance.sharedkernel.id.TenantId;

/**
 * Portador do TenantContext para o thread atual.
 * NUNCA deve ser preenchido a partir de dados enviados pelo cliente;
 * somente após a validação criptográfica do JWT (ADR-03/ADR-30).
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext get() {
        return CONTEXT.get();
    }

    public static TenantContext require() {
        TenantContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("TenantContext não estabelecido para o request atual.");
        }
        return context;
    }

    public static TenantId requireTenantId() {
        return require().tenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
