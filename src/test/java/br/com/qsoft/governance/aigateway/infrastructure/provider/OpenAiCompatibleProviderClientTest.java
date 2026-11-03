package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolSpec;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.ProviderCall;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.ProviderResponse;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient.Usage;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.ProviderTimeoutException;
import br.com.qsoft.governance.aigateway.domain.provider.ProviderKind;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * TR12: {@link OpenAiCompatibleProviderClient} contra um HTTP local; valida o
 * request, o mapeamento de {@code usage} e que o segredo nunca é logado.
 */
class OpenAiCompatibleProviderClientTest {

    private static final String SECRET = "sk-test-secret";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> openCodeSession = new AtomicReference<>();
    private final AtomicReference<String> userAgent = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        authorization.set(null);
        requestBody.set(null);
        openCodeSession.set(null);
        userAgent.set(null);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            openCodeSession.set(exchange.getRequestHeaders().getFirst("x-opencode-session"));
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            byte[] response = """
                {
                  "choices": [{
                    "message": {
                      "content": "resposta",
                      "tool_calls": [{"id": "call-1",
                        "function": {"name": "buscar_politicas", "arguments": "{}"}}]
                    }
                  }],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 7}
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void enviaRequestCorretoEMapeiaUso() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            ProviderResponse response = client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, baseUrl, "OPENAI_KEY", 5000, "gpt-x",
                List.of(CompletionMessage.user("olá")),
                List.of(new ToolSpec("buscar_politicas", "busca",
                    "{\"type\":\"object\",\"properties\":{}}"))));

            assertThat(authorization.get()).isEqualTo("Bearer " + SECRET);
            assertThat(openCodeSession.get()).isNull();
            assertThat(requestBody.get())
                .contains("\"model\":\"gpt-x\"")
                .contains("\"role\":\"user\"")
                .contains("\"tools\"")
                .contains("\"type\":\"function\"");

            assertThat(response.content()).isEqualTo("resposta");
            assertThat(response.toolCalls()).hasSize(1);
            assertThat(response.toolCalls().get(0).name()).isEqualTo("buscar_politicas");
            assertThat(response.usage()).isInstanceOfSatisfying(Usage.Known.class, usage -> {
                assertThat(usage.inputTokens()).isEqualTo(11);
                assertThat(usage.outputTokens()).isEqualTo(7);
            });

            assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains(SECRET));
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void enviaSessaoEUserAgentParaOpenCodeEPreservaCaminhoDaBaseUrl() throws IOException {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> session = new AtomicReference<>();
        AtomicReference<String> agent = new AtomicReference<>();
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            session.set(exchange.getRequestHeaders().getFirst("x-opencode-session"));
            agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = """
                {"choices":[{"message":{"content":"ok"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        other.start();
        try {
            String url = "http://127.0.0.1:" + other.getAddress().getPort() + "/opencode.ai";
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            ProviderResponse response = client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, url, "OPENAI_KEY", 5000, "gpt-x",
                List.of(CompletionMessage.user("oi")), List.of()));

            assertThat(response.content()).isEqualTo("ok");
            assertThat(path.get()).isEqualTo("/opencode.ai/chat/completions");
            assertThat(session.get()).isNotBlank();
            assertThat(agent.get()).isEqualTo("atlas-qsoft-governance/1.0");
        } finally {
            other.stop(0);
        }
    }

    @Test
    void semUsageNoCorpoDevolveUnknown() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/chat/completions", exchange -> {
            byte[] response = """
                {"choices":[{"message":{"content":"ok"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        other.start();
        try {
            String url = "http://127.0.0.1:" + other.getAddress().getPort();
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            ProviderResponse response = client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, url, "OPENAI_KEY", 5000, "gpt-x",
                List.of(CompletionMessage.user("oi")), List.of()));

            assertThat(response.usage()).isInstanceOf(Usage.Unknown.class);
        } finally {
            other.stop(0);
        }
    }

    @Test
    void erroHttpViraProviderFailureComCodigo() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/chat/completions", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        other.start();
        try {
            String url = "http://127.0.0.1:" + other.getAddress().getPort();
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            assertThatThrownBy(() -> client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, url, "OPENAI_KEY", 5000, "gpt-x",
                List.of(CompletionMessage.user("oi")), List.of())))
                .isInstanceOfSatisfying(ProviderFailureException.class,
                    ex -> assertThat(ex.errorCode()).isEqualTo("HTTP_429"));
        } finally {
            other.stop(0);
        }
    }

    @Test
    void semCredentialRefNaoEnviaAuthorization() {
        OpenAiCompatibleProviderClient client = new OpenAiCompatibleProviderClient(
            reference -> {
                throw new AssertionError("resolver não deveria ser chamado");
            }, new ObjectMapper());

        ProviderResponse response = client.call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, baseUrl, null, 5000, "gpt-x",
            List.of(CompletionMessage.user("oi")), List.of()));

        assertThat(response.content()).isEqualTo("resposta");
        assertThat(authorization.get()).isNull();
    }

    @Test
    void credencialAusenteViraCredentialMissing() {
        OpenAiCompatibleProviderClient client =
            new OpenAiCompatibleProviderClient(new EnvCredentialResolver(), new ObjectMapper());

        assertThatThrownBy(() -> client.call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, baseUrl, "QSOFT_NAO_EXISTE_TESTE", 5000, "gpt-x",
            List.of(CompletionMessage.user("oi")), List.of())))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo("CREDENTIAL_MISSING"));
    }

    @Test
    void erro500ViraProviderFailure() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/chat/completions", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        other.start();
        try {
            String url = "http://127.0.0.1:" + other.getAddress().getPort();
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            assertThatThrownBy(() -> client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, url, "OPENAI_KEY", 5000, "gpt-x",
                List.of(CompletionMessage.user("oi")), List.of())))
                .isInstanceOfSatisfying(ProviderFailureException.class,
                    ex -> assertThat(ex.errorCode()).isEqualTo("HTTP_500"));
        } finally {
            other.stop(0);
        }
    }

    @Test
    void timeoutViraProviderTimeout() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        other.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        other.start();
        try {
            String url = "http://127.0.0.1:" + other.getAddress().getPort();
            OpenAiCompatibleProviderClient client =
                new OpenAiCompatibleProviderClient(reference -> SECRET, new ObjectMapper());

            assertThatThrownBy(() -> client.call(new ProviderCall(
                ProviderKind.OPENAI_COMPATIBLE, url, "OPENAI_KEY", 1000, "gpt-x",
                List.of(CompletionMessage.user("oi")), List.of())))
                .isInstanceOf(ProviderTimeoutException.class);
        } finally {
            other.stop(0);
        }
    }
}
