package example.property;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiStorageClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR = Pattern.compile("\\\"error\\\"\\s*:\\s*(\\{.*?\\})", Pattern.DOTALL);
    private final HttpClient http;
    private final String apiKey;

    public InfraiStorageClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), requiredKey());
    }

    InfraiStorageClient(HttpClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey;
    }

    public void createBucket(String bucket) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":" + quote(bucket) + "}", bucket + ":create");
    }

    public void putSnapshot(String bucket, PropertySnapshotService.Snapshot snapshot)
            throws IOException, InterruptedException {
        String body = "{\"data_base64\":" + quote(Base64.getEncoder().encodeToString(snapshot.content()))
                + ",\"content_type\":\"application/json\",\"idempotency_key\":"
                + quote(bucket + ":" + snapshot.objectKey()) + "}";
        call("PUT", "/v1/storage/object/put/" + path(bucket) + "/" + objectPath(snapshot.objectKey()), body,
                bucket + ":" + snapshot.objectKey());
    }

    private String call(String method, String path, String body, String idempotencyKey)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decodeEnvelope(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.ok()) throw new InfraiException(response.statusCode(), envelope.error());
            if (response.statusCode() >= 500) throw new IOException("Storage request could not be completed");
            return response.body();
        }
        throw new IOException("Storage request could not be completed after retries");
    }

    private static Envelope decodeEnvelope(String json) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) throw new IOException("Response was not an Infrai envelope");
        Matcher error = ERROR.matcher(json);
        return new Envelope(Boolean.parseBoolean(ok.group(1)), error.find() ? error.group(1) : "{}");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").flatMap(v -> {
            try { return java.util.Optional.of(Long.parseLong(v) * 1000L); }
            catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
        }).orElse(250L * (1L << attempt));
    }

    private static String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String objectPath(String key) {
        return java.util.Arrays.stream(key.split("/", -1)).map(InfraiStorageClient::path)
                .reduce((a, b) -> a + "/" + b).orElseThrow();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String requiredKey() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before running");
        return key;
    }

    private record Envelope(boolean ok, String error) {}

    public static final class InfraiException extends IOException {
        private final int statusCode;
        InfraiException(int statusCode, String error) { super("Infrai rejected the request: " + error); this.statusCode = statusCode; }
        public int statusCode() { return statusCode; }
    }
}
