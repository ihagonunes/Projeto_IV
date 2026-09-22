package br.com.qsoft.governance.iam.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link PermissionInterceptor} para todas as rotas; o interceptor
 * só atua nos handlers anotados com {@link RequiresPermission}.
 */
@Configuration
public class RbacWebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    public RbacWebConfig(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor);
    }
}
