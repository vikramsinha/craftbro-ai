package com.example.hellomod;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Exercises the real OTLP exporters against a local receiver, without Minecraft. */
public final class TelemetrySmokeTest {
    public static void main(String[] args) throws Exception {
        testMovementTracker();
        if (args.length > 0 && args[0].equals("dashboard")) {
            try (ModTelemetry telemetry = new ModTelemetry("http://127.0.0.1:4318")) {
                telemetry.log("Telemetry demo: simulated mod startup (not a live game session)");
                telemetry.playerJoined();
                telemetry.command(() -> 1);
                telemetry.playerMoved(-1, demoMovement());
                telemetry.flush();
            }
            System.out.println("Demo export attempted. Check minecraft-hello-mod in Aspire.");
            return;
        }
        Map<String, String> received = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.merge(exchange.getRequestURI().getPath(), body, String::concat);
            exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (ModTelemetry telemetry = new ModTelemetry("http://127.0.0.1:" + server.getAddress().getPort())) {
            telemetry.log("Hello Mod loaded successfully!");
            telemetry.playerJoined();
            telemetry.playerMoved(-1, demoMovement());
            if (telemetry.command(() -> 1) != 1) throw new AssertionError("Command result changed");
            try {
                telemetry.command(() -> { throw new IllegalStateException("test failure"); });
                throw new AssertionError("Command exception swallowed");
            } catch (IllegalStateException expected) {
                // Game command failures must retain their normal behavior.
            }
            telemetry.flush();
            require(received, "/v1/logs", "Hello Mod loaded successfully!", "Player joined", "/hellomod executed");
            require(received, "/v1/traces", "hellomod.command", "test failure", "player.move",
                "player.position.previous.x", "player.position.x", "player.position.y",
                "player.position.z", "player.dimension", "minecraft:overworld", "player.movement.distance");
            require(received, "/v1/metrics", "hellomod.player.joins", "hellomod.command.executions");
            System.out.println("PASS: OTLP logs, traces and metrics exported; command result and errors preserved.");
        } finally {
            server.stop(0);
        }
        // A closed dashboard must not break or block the command path.
        var exporterLogger = java.util.logging.Logger.getLogger("io.opentelemetry");
        var previousLevel = exporterLogger.getLevel();
        exporterLogger.setLevel(java.util.logging.Level.OFF);
        try (ModTelemetry offline = new ModTelemetry("http://127.0.0.1:" + server.getAddress().getPort())) {
            long start = System.nanoTime();
            if (offline.command(() -> 7) != 7) throw new AssertionError("Offline command failed");
            if (System.nanoTime() - start > 1_000_000_000L) throw new AssertionError("Command blocked on export");
            System.out.println("PASS: command works when dashboard is offline.");
        } finally {
            exporterLogger.setLevel(previousLevel);
        }
    }

    private static MovementTracker.Movement demoMovement() {
        return new MovementTracker.Movement(
            new MovementTracker.Position(0, 64, 0, "minecraft:overworld"),
            new MovementTracker.Position(3, 64, 4, "minecraft:overworld"), false, 5);
    }

    private static void testMovementTracker() {
        var tracker = new MovementTracker();
        var player = java.util.UUID.randomUUID();
        var start = new MovementTracker.Position(0, 64, 0, "minecraft:overworld");
        if (tracker.sample(player, start) != null) throw new AssertionError("First sample emitted movement");
        if (tracker.sample(player, start) != null) throw new AssertionError("Stationary player emitted movement");
        if (tracker.sample(player, new MovementTracker.Position(0.6, 64, 0, start.dimension())) != null)
            throw new AssertionError("Sub-block movement emitted a trace");
        var move = tracker.sample(player, new MovementTracker.Position(1, 64, 0, start.dimension()));
        if (move == null || move.distance() != 1 || !move.from().equals(start))
            throw new AssertionError("Slow cumulative movement or one-block threshold failed");
        move = tracker.sample(player, new MovementTracker.Position(1, 64, 0, "minecraft:the_nether"));
        if (move == null || !move.dimensionChanged()) throw new AssertionError("Dimension transition lost");
        if (tracker.sample(java.util.UUID.randomUUID(), start) != null) throw new AssertionError("Player baselines mixed");
        tracker.remove(player);
        if (tracker.sample(player, start) != null) throw new AssertionError("Disconnect baseline retained");
        var respawn = new MovementTracker.Position(100, 70, 100, start.dimension());
        tracker.reset(player, respawn);
        if (tracker.sample(player, respawn) != null) throw new AssertionError("Respawn baseline retained");
        tracker.clear();
        if (tracker.sample(player, start) != null) throw new AssertionError("World baseline retained");
        System.out.println("PASS: movement threshold, stationary suppression, dimension changes and lifecycle resets.");
    }

    private static void require(Map<String, String> received, String path, String... markers) {
        String body = received.getOrDefault(path, "");
        if (!body.contains("minecraft-hello-mod")) throw new AssertionError("Missing service at " + path);
        for (String marker : markers) {
            if (!body.contains(marker)) throw new AssertionError("Missing " + marker + " at " + path);
        }
    }
}
