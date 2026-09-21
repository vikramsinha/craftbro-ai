package com.example.hellomod;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class AiSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("live")) {
            try (var ai = new OllamaAssistant(); var telemetry = new ModTelemetry("http://127.0.0.1:4318")) {
                var span = telemetry.startAiRequest();
                span.setAttribute("test.synthetic", true);
                try {
                    System.out.println(ai.ask("What should I do next?", "Synthetic test: Overworld forest, daytime, health 20/20, food 20/20, inventory 6 oak logs.", span)
                        .get(125, TimeUnit.SECONDS));
                } finally { span.end(); telemetry.flush(); }
            }
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> request = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] response = "{\"model\":\"qwen3.5:4b\",\"prompt_eval_count\":42,\"eval_count\":6,\"eval_duration\":100000000,\"done_reason\":\"stop\",\"message\":{\"content\":\"Make a crafting table.\"}}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try { exchange.sendResponseHeaders(200, -1); } finally { exchange.close(); }
        });
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(200, 1); exchange.getResponseBody().write('!'); exchange.close();
        });
        AtomicReference<String> exported = new AtomicReference<>("");
        server.createContext("/v1/", exchange -> {
            exported.updateAndGet(previous -> {
                try { return previous + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
                catch (java.io.IOException error) { throw new RuntimeException(error); }
            });
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var telemetry = new ModTelemetry(base)) {
            try (var ai = new OllamaAssistant(URI.create(base + "/api/chat"), Duration.ofSeconds(10))) {
                var span = telemetry.startAiRequest();
                var first = ai.ask("What next?", "6 oak logs", span);
                try { ai.ask("Another question", "").join(); throw new AssertionError("Concurrent request accepted"); }
                catch (java.util.concurrent.CompletionException expected) {
                    if (!OllamaAssistant.friendlyError(expected).contains("already")) throw expected;
                }
                release.countDown();
                if (!first.get(10, TimeUnit.SECONDS).equals("Make a crafting table.")) throw new AssertionError("Response parsing failed");
                span.end();
                telemetry.flush();
                for (String marker : new String[]{"gen_ai.input.messages", "gen_ai.output.messages", "6 oak logs",
                    "Make a crafting table.", "gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens",
                    "ollama.eval_duration_ms", "http.response.status_code", "request.sent", "response.received"}) {
                    if (!exported.get().contains(marker)) throw new AssertionError("Missing exported AI detail: " + marker);
                }
                var payload = JsonParser.parseString(request.get()).getAsJsonObject();
                if (payload.get("stream").getAsBoolean() || payload.get("think").getAsBoolean()
                    || !payload.get("model").getAsString().equals(OllamaAssistant.MODEL)
                    || !payload.get("messages").toString().contains("6 oak logs")) throw new AssertionError("Incorrect request");
                ai.ask("Again", "").get(10, TimeUnit.SECONDS);
            }
            for (String path : new String[]{"/missing", "/broken"}) {
                try (var ai = new OllamaAssistant(URI.create(base + path), Duration.ofSeconds(3))) {
                    var failureSpan = telemetry.startAiRequest();
                    try { ai.ask("Hello", "", failureSpan).join(); throw new AssertionError("Bad response accepted"); }
                    catch (java.util.concurrent.CompletionException expected) {
                        String message = OllamaAssistant.friendlyError(expected);
                        if (!(message.contains("Model missing") || message.contains("unreadable"))) throw expected;
                    } finally { failureSpan.end(); }
                }
            }
            telemetry.flush();
            if (!exported.get().contains("error.type") || !exported.get().contains("exception")) throw new AssertionError("Missing failure details");
            try (var ai = new OllamaAssistant(URI.create(base + "/slow"), Duration.ofMillis(50))) {
                try { ai.ask("Hello", "").join(); throw new AssertionError("Timeout not enforced"); }
                catch (java.util.concurrent.CompletionException expected) {
                    if (!OllamaAssistant.friendlyError(expected).contains("too long")) throw expected;
                }
            }
        } finally { release.countDown(); server.stop(0); }
        try (var ai = new OllamaAssistant(URI.create(base + "/api/chat"), Duration.ofSeconds(2))) {
            try { ai.ask("Hello", "").join(); throw new AssertionError("Offline request succeeded"); }
            catch (java.util.concurrent.CompletionException expected) {
                if (!OllamaAssistant.friendlyError(expected).contains("Cannot reach")) throw expected;
            }
        }
        System.out.println("PASS: local AI payload, response, concurrency limit, missing model, malformed response, timeout and offline handling.");
    }
}
