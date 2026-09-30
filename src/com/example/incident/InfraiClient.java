package com.example.incident;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String apiKey;

    public InfraiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String request(String method, String path, String body) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (!envelope.contains("\"ok\":true")) {
                    throw new InfraiException(envelope, response.statusCode());
                }
                return envelope;
            } catch (InfraiException e) {
                throw e;
            } catch (IOException | InterruptedException e) {
                if (attempt == 2) throw new InfraiException(e.getMessage(), 503);
                sleep(attempt);
            }
        }
        throw new InfraiException("request failed", 503);
    }

    private void sleep(int attempt) {
        try { Thread.sleep(200L * (1L << attempt)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
