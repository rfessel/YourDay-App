package com.yourday.app.core.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cliente HTTP compartilhado (background). */
public final class HttpSupport {

    public static final ExecutorService EXEC = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "yourday-http");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(EXEC)
            .build();

    private HttpSupport() {
    }

    /** GET síncrono (usar em thread de fundo). */
    public static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "YourDay/0.1")
                .GET()
                .build();
        HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = resp.body()) {
            byte[] bytes = in.readAllBytes();
            if (resp.statusCode() >= 400) {
                throw new IOException("HTTP " + resp.statusCode() + " em " + url);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** GET assíncrono (callback na thread de fundo). */
    public static void getAsync(String url, java.util.function.Consumer<String> onOk,
                                java.util.function.Consumer<Exception> onError) {
        EXEC.execute(() -> {
            try {
                onOk.accept(get(url));
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }
}