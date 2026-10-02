package br.com.qsoft.governance.iam.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class RbacWebConfigTest {

    @Test
    void deve_registrarPermissionInterceptor() {
        PermissionInterceptor interceptor = mock(PermissionInterceptor.class);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        RbacWebConfig config = new RbacWebConfig(interceptor);
        config.addInterceptors(registry);

        verify(registry).addInterceptor(interceptor);
    }
}
