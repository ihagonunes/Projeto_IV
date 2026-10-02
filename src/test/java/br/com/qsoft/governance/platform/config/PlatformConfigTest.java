package br.com.qsoft.governance.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.swagger.v3.oas.models.OpenAPI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

class PlatformConfigTest {

    @Test
    void testJwtSecurityProperties() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret("12345678901234567890123456789012");
        properties.setTtlMinutes(60);

        assertThat(properties.getSecret()).isEqualTo("12345678901234567890123456789012");
        assertThat(properties.getTtlMinutes()).isEqualTo(60);
    }

    @Test
    void testJwtConfig() {
        JwtConfig config = new JwtConfig();
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret("12345678901234567890123456789012");

        SecretKey secretKey = config.jwtSecretKey(properties);
        assertThat(secretKey).isNotNull();
        assertThat(secretKey.getAlgorithm()).isEqualTo("HmacSHA256");

        JwtEncoder encoder = config.jwtEncoder(secretKey);
        assertThat(encoder).isNotNull();

        JwtDecoder decoder = config.jwtDecoder(secretKey);
        assertThat(decoder).isNotNull();
    }

    @Test
    void testOpenApiConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.qsoftOpenAPI();
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("QSOFT AI Governance API");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }

    @Test
    void testSecurityBaselineConfigBeansAndExceptionHandlers() throws Exception {
        SecurityBaselineConfig config = new SecurityBaselineConfig();

        PasswordEncoder encoder = config.passwordEncoder();
        assertThat(encoder).isNotNull();
        assertThat(encoder.encode("password")).isNotBlank();

        org.springframework.security.config.annotation.web.builders.HttpSecurity http =
            mock(org.springframework.security.config.annotation.web.builders.HttpSecurity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        ObjectMapper objectMapper = new ObjectMapper();

        when(http.csrf(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.CsrfConfigurer.class));
            return http;
        });
        when(http.sessionManagement(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer.class));
            return http;
        });
        when(http.authorizeHttpRequests(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry registry =
                mock(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class);
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizedUrl authorizedUrl =
                mock(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
            when(registry.requestMatchers(any(String[].class))).thenReturn(authorizedUrl);
            when(registry.anyRequest()).thenReturn(authorizedUrl);
            when(authorizedUrl.permitAll()).thenReturn(registry);
            when(authorizedUrl.authenticated()).thenReturn(registry);
            c.customize(registry);
            return http;
        });


        when(http.oauth2ResourceServer(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer.class, org.mockito.Mockito.RETURNS_SELF));
            return http;
        });
        when(http.exceptionHandling(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            ExceptionHandlingConfigurer ehc = mock(ExceptionHandlingConfigurer.class, org.mockito.Mockito.RETURNS_SELF);
            c.customize(ehc);
            return http;
        });
        when(http.httpBasic(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer.class));
            return http;
        });
        when(http.formLogin(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer.class));
            return http;
        });
        when(http.logout(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.LogoutConfigurer.class));
            return http;
        });
        when(http.cors(any())).thenAnswer(inv -> {
            Customizer c = inv.getArgument(0);
            c.customize(mock(org.springframework.security.config.annotation.web.configurers.CorsConfigurer.class));
            return http;
        });


        config.securityFilterChain(http, objectMapper);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Customizer<ExceptionHandlingConfigurer<HttpSecurity>>> captor =
            ArgumentCaptor.forClass(Customizer.class);
        verify(http).exceptionHandling(captor.capture());

        ExceptionHandlingConfigurer<HttpSecurity> exceptionConfigurer = mock(ExceptionHandlingConfigurer.class);
        when(exceptionConfigurer.authenticationEntryPoint(any())).thenReturn(exceptionConfigurer);
        when(exceptionConfigurer.accessDeniedHandler(any())).thenReturn(exceptionConfigurer);

        captor.getValue().customize(exceptionConfigurer);


        ArgumentCaptor<AuthenticationEntryPoint> entryPointCaptor = ArgumentCaptor.forClass(AuthenticationEntryPoint.class);
        verify(exceptionConfigurer).authenticationEntryPoint(entryPointCaptor.capture());

        ArgumentCaptor<AccessDeniedHandler> accessDeniedCaptor = ArgumentCaptor.forClass(AccessDeniedHandler.class);
        verify(exceptionConfigurer).accessDeniedHandler(accessDeniedCaptor.capture());

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(sw));

        entryPointCaptor.getValue().commence(req, res, mock(AuthenticationException.class));
        verify(res).setStatus(401);
        assertThat(sw.toString()).contains("Não autenticado.");

        StringWriter sw2 = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(sw2));

        accessDeniedCaptor.getValue().handle(req, res, mock(AccessDeniedException.class));
        verify(res).setStatus(403);
        assertThat(sw2.toString()).contains("Acesso negado.");
    }
}
