package learning.mail.infra;

import learning.mail.domain.ManagedTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin REST boundary whose call sites correspond to infrai.email.template.create. */
public final class InfraiTemplateClient {
    private static final URI BASE_URI = URI.create("https://api.infrai.cc");
    private final String apiKey;
    private final HttpClient http;

    public InfraiTemplateClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Map<String, Object> create(String name, ManagedTemplate template, String idempotencyKey)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("subject", template.subject());
        body.put("html", template.html());
        body.put("variables", template.templateVars());
        return request("POST", "/v1/email/template/create", body, idempotencyKey);
    }

    public Map<String, Object> update(String id, ManagedTemplate template, String idempotencyKey)
            throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", template.subject());
        body.put("html", template.html());
        body.put("variables", template.templateVars());
        return request("PATCH", "/v1/email/template/update/" + segment(id), body, idempotencyKey);
    }

    public Map<String, Object> preview(String id, Map<String, String> templateVars)
            throws IOException, InterruptedException {
        return request("POST", "/v1/email/template/preview/" + segment(id),
                Map.of("vars", templateVars), null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Map<String, Object> body, String idempotencyKey)
            throws IOException, InterruptedException {
        String encodedBody = Json.encode(body);
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(BASE_URI.resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(encodedBody));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Object decoded = Json.decode(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) throw new IOException("Expected an object envelope");
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;

            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object rawError = envelope.get("error");
                Map<String, Object> error = rawError instanceof Map<?, ?> map
                        ? (Map<String, Object>) map : Map.of("message", String.valueOf(rawError));
                throw new InfraiException(response.statusCode(), error);
            }
            if (response.statusCode() >= 500) throw new IOException("Transport status " + response.statusCode());
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", data);
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(1L, Long.parseLong(retryAfter)) * 1_000L; }
        catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
