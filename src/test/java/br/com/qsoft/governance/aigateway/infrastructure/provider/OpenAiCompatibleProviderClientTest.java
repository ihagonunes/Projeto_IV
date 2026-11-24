package br.com.qsoft.governance.aigateway.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.qsoft.governance.aigateway.api.completion.CompletionMessage;
import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
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
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private final AtomicReference<String> responseOverride = new AtomicReference<>();

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
            String standardResponse = """
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
                """;
            String override = responseOverride.get();
            byte[] response = (override == null ? standardResponse : override).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"choices\":[1]}", "{invalid json"})
    void respostaInvalidaViraFalhaDeParse(String response) {
        responseOverride.set(response);
        assertThatThrownBy(() -> clientWithoutCredential().call(localCall(List.of(), List.of())))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo("PARSE"));
    }

    @Test
    void aceitaCamposOpcionaisEIgnoraFerramentasMalformadas() {
        responseOverride.set("""
            {"choices":[{"message":{"tool_calls":[true,{},{"function":{}},
              {"function":{"name":"lookup"}},{"id":"call-2","function":{"name":"search","arguments":"{}"}}]}}],
              "usage":{"prompt_tokens":"unknown"}}
            """);

        ProviderResponse response = clientWithoutCredential().call(localCall(
            List.of(new CompletionMessage("TOOL", null, "call-1")),
            List.of(new ToolSpec("lookup", null, "{}"))));

        assertThat(authorization.get()).isNull();
        assertThat(requestBody.get()).contains("\"role\":\"tool\"", "\"content\":\"\"",
            "\"tool_call_id\":\"call-1\"", "\"description\":\"\"");
        assertThat(response.content()).isNull();
        assertThat(response.usage()).isInstanceOf(Usage.Unknown.class);
        assertThat(response.toolCalls()).containsExactly(
            new ToolCall("lookup", "lookup", "{}"),
            new ToolCall("call-2", "search", "{}"));
    }

    @Test
    void deepSeekFlashDisablesThinkingWithoutIncreasingOutputBudget() throws Exception {
        clientWithoutCredential().call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, baseUrl, null, 5000, "deepseek-v4.1-flash",
            List.of(CompletionMessage.user("Mensagem local de teste")), List.of(), 2048));

        var body = new ObjectMapper().readTree(requestBody.get());
        assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(2048);
    }

    @Test
    void otherModelsKeepTheirExistingThinkingBehavior() throws Exception {
        clientWithoutCredential().call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, baseUrl, null, 5000, "gpt-x",
            List.of(CompletionMessage.user("Mensagem local de teste")), List.of(), 2048));

        var body = new ObjectMapper().readTree(requestBody.get());
        assertThat(body.has("thinking")).isFalse();
        assertThat(body.path("max_tokens").asInt()).isEqualTo(2048);
    }

    @Test
    void reasoningOnlyResponseIsNeverUsedAsFinalAnswer() {
        responseOverride.set("""
            {"choices":[{"finish_reason":"length","message":{"content":null,
              "reasoning_content":"raciocínio de teste que não é a resposta"}}],
              "usage":{"prompt_tokens":10,"completion_tokens":2048}}
            """);

        ProviderResponse response = clientWithoutCredential().call(localCall(List.of(), List.of()));
        assertThat(response.content()).isNull();
        assertThat(response.usage()).isEqualTo(new Usage.Known(10, 2048));
    }

    @Test
    void aceitaChoiceSemMensagemComoRespostaSemConteudo() {
        responseOverride.set("{\"choices\":[{}]}");
        ProviderResponse response = clientWithoutCredential().call(localCall(List.of(), List.of()));
        assertThat(response.content()).isNull();
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.usage()).isInstanceOf(Usage.Unknown.class);
    }

    @Test
    void rejeitaSchemaDeFerramentaInvalidoAntesDoEnvio() {
        assertThatThrownBy(() -> clientWithoutCredential().call(localCall(
            List.of(), List.of(new ToolSpec("lookup", "Busca", "{invalid json")))))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo("PARSE"));
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void conexaoEncerradaAntesDosHeadersViraFalhaDeConexao() {
        server.createContext("/disconnect/chat/completions", HttpExchange::close);

        assertThatThrownBy(() -> clientWithoutCredential().call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, baseUrl + "/disconnect", null, 5000, "gpt-x",
            List.of(), List.of())))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo("CONNECT"));
    }

    @Test
    void urlAusenteFalhaSemEnviarRequisicao() {
        assertThatThrownBy(() -> clientWithoutCredential().call(new ProviderCall(
            ProviderKind.OPENAI_COMPATIBLE, null, null, 5000, "gpt-x", List.of(), List.of())))
            .isInstanceOfSatisfying(ProviderFailureException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo("PARSE"));
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void reconheceTimeoutDeSocketEncapsuladoPeloClienteHttp() {
        assertThat(OpenAiCompatibleProviderClient.isTimeout(
            new IOException("Falha de transporte", new SocketTimeoutException()))).isTrue();
    }

    private ProviderCall localCall(List<CompletionMessage> messages, List<ToolSpec> tools) {
        return new ProviderCall(ProviderKind.OPENAI_COMPATIBLE, baseUrl, " ", 5000, "gpt-x", messages, tools);
    }

    private static OpenAiCompatibleProviderClient clientWithoutCredential() {
        return new OpenAiCompatibleProviderClient(reference -> {
            throw new AssertionError("resolver não deveria ser chamado");
        }, new ObjectMapper());
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
