package com.example.hellomod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.Locale;
import java.util.function.Supplier;

final class AiCommands {
    private OllamaAssistant assistant;
    private final Supplier<ModTelemetry> telemetry;

    AiCommands(Supplier<ModTelemetry> telemetry) { this.telemetry = telemetry; }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> assistant = new OllamaAssistant());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            OllamaAssistant previous = assistant;
            assistant = null;
            if (previous != null) previous.close();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("askmod")
                .executes(context -> ask(context.getSource(), "What should I do next to survive and make progress?"))
                .then(Commands.argument("question", StringArgumentType.greedyString())
                    .executes(context -> ask(context.getSource(), StringArgumentType.getString(context, "question")))));
            dispatcher.register(Commands.literal("aitip")
                .executes(context -> ask(context.getSource(), "Give me one useful tip for my current situation.")));
        });
    }

    private int ask(CommandSourceStack source, String question) {
        var player = source.getPlayer();
        if (player == null) { source.sendFailure(Component.literal("Use this command in-game as a player.")); return 0; }
        if (question.isBlank() || question.length() > 500) {
            source.sendFailure(Component.literal("Keep your question between 1 and 500 characters.")); return 0;
        }
        OllamaAssistant current = assistant;
        if (current == null) { source.sendFailure(Component.literal("AI is not ready. Try again after entering the world.")); return 0; }
        // Capture all Minecraft state on the server thread, before asynchronous I/O.
        StringBuilder inventory = new StringBuilder();
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty()) inventory.append(stack.getCount()).append("x ")
                .append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).append(", ");
        }
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
            .map(key -> key.location().toString()).orElse("unknown");
        String snapshot = String.format(Locale.ROOT,
            "Position: %.1f, %.1f, %.1f; dimension: %s; biome: %s; health: %.1f/%.1f; food: %d/20; daytime: %s; inventory: %s",
            player.getX(), player.getY(), player.getZ(), player.level().dimension().location(), biome,
            player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(), player.level().isDay(), inventory);
        source.sendSuccess(() -> Component.literal("Local AI is thinking..."), false);
        ModTelemetry metrics = telemetry.get();
        var span = metrics == null ? null : metrics.startAiRequest();
        var server = source.getServer();
        current.ask(question, snapshot, span == null ? io.opentelemetry.api.trace.Span.getInvalid() : span).whenComplete((answer, error) -> {
            if (span != null) {
                if (error != null) span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, OllamaAssistant.friendlyError(error));
                span.end();
            }
            server.execute(() -> {
                if (assistant != current || server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                String reply = error == null ? answer : OllamaAssistant.friendlyError(error);
                if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, AiReplyPayload.TYPE)) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new AiReplyPayload(question, reply));
                } else {
                    player.sendSystemMessage(Component.literal("AI: " + reply));
                }
            });
        });
        return 1;
    }
}
