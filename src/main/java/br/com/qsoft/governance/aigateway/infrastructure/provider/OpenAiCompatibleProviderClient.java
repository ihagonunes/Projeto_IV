package br.com.qsoft.governance.aigateway.infrastructure.provider;

import br.com.qsoft.governance.aigateway.api.completion.ToolCall;
import br.com.qsoft.governance.aigateway.application.port.CredentialResolver;
import br.com.qsoft.governance.aigateway.application.port.ProviderClient;
import br.com.qsoft.governance.aigateway.application.port.ProviderFailureException;
import br.com.qsoft.governance.aigateway.application.port.ProviderTimeoutException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * Provider compatível com a API OpenAI ({@code POST /chat/completions}) via
 * {@code RestClient} do Spring — <b>sem SDK e sem dependência nova</b> (ADR-38).
 * <p>
 * O segredo vem de {@link CredentialResolver} e <b>nunca</b> é logado; corpo e
 * header também não são logados.
 */
@Component
public class OpenAiCompatibleProviderClient implements ProviderClient {

    private final CredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProviderClient(CredentialResolver credentialResolver,
                                          ObjectMapper objectMapper) {
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderResponse call(ProviderCall call) {
        // credentialRef nulo/em branco = provider local sem chave (ex.: Ollama).
        String secret = null;
        if (call.credentialRef() != null && !call.credentialRef().isBlank()) {
            secret = credentialResolver.resolve(call.credentialRef());
        }
        RestClient client = restClient(call.baseUrl(), call.timeoutMs());
        try {
            RestClient.RequestBodySpec request = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON);
            if (secret != null) {
                request = request.header("Authorization", "Bearer " + secret);
            }
            Map<?, ?> response = request
                .body(requestBody(call))
                .retrieve()
                .body(Map.class);
            return toProviderResponse(response);
        } catch (RestClientResponseException ex) {
            throw new ProviderFailureException("HTTP_" + ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new ProviderTimeoutException();
            }
            throw new ProviderFailureException("CONNECT");
        } catch (ProviderTimeoutException | ProviderFailureException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProviderFailureException("PARSE");
        }
    }

    private static RestClient restClient(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private Map<String, Object> requestBody(ProviderCall call) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", call.externalModelName());
        body.put("messages", call.messages().stream().map(message -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", message.role().toLowerCase(Locale.ROOT));
            map.put("content", message.content() == null ? "" : message.content());
            if (message.toolCallId() != null) {
                map.put("tool_call_id", message.toolCallId());
            }
            return map;
        }).toList());
        if (!call.tools().isEmpty()) {
            body.put("tools", call.tools().stream().map(tool -> Map.of(
                "type", "function",
                "function", Map.of(
                    "name", tool.name(),
                    "description", tool.description() == null ? "" : tool.description(),
                    "parameters", readJson(tool.jsonSchemaParameters())))).toList());
        }
        return body;
    }

    private Object readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException ex) {
            throw new ProviderFailureException("PARSE");
        }
    }

    private ProviderResponse toProviderResponse(Map<?, ?> response) {
        if (response == null) {
            throw new ProviderFailureException("PARSE");
        }
        List<?> choices = response.get("choices") instanceof List<?> list ? list : List.of();
        if (choices.isEmpty() || !(choices.get(0) instanceof Map<?, ?> choice)) {
            throw new ProviderFailureException("PARSE");
        }
        Map<?, ?> message = choice.get("message") instanceof Map<?, ?> map ? map : Map.of();
        String content = message.get("content") instanceof String text ? text : null;
        List<ToolCall> toolCalls = toolCalls(message);
        return new ProviderResponse(content, toolCalls, usage(response), false);
    }

    private static List<ToolCall> toolCalls(Map<?, ?> message) {
        List<ToolCall> calls = new ArrayList<>();
        if (message.get("tool_calls") instanceof List<?> rawCalls) {
            for (Object raw : rawCalls) {
                if (!(raw instanceof Map<?, ?> toolCall)) {
                    continue;
                }
                Map<?, ?> function = toolCall.get("function") instanceof Map<?, ?> map ? map : Map.of();
                String name = function.get("name") instanceof String text ? text : null;
                if (name == null) {
                    continue;
                }
                String id = toolCall.get("id") instanceof String text ? text : name;
                String arguments = function.get("arguments") instanceof String text ? text : "{}";
                calls.add(new ToolCall(id, name, arguments));
            }
        }
        return calls;
    }

    private static Usage usage(Map<?, ?> response) {
        if (!(response.get("usage") instanceof Map<?, ?> usage)) {
            return new Usage.Unknown();
        }
        return new Usage.Known(number(usage.get("prompt_tokens")), number(usage.get("completion_tokens")));
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
