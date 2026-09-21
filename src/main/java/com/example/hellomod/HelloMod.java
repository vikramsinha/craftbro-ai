package com.example.hellomod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("hellomod");
    private ModTelemetry telemetry;
    private final MovementTracker movementTracker = new MovementTracker();
    private int movementTicks;

    @Override
    public void onInitialize() {
        LOGGER.info("CraftBro AI loaded successfully!");
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(AiReplyPayload.TYPE, AiReplyPayload.CODEC);
        new AiCommands(() -> telemetry).register();

        // Leave ordinary launcher installs quiet unless telemetry is explicitly enabled.
        if ("true".equalsIgnoreCase(System.getenv("HELLOMOD_TELEMETRY_ENABLED"))) {
            try {
                String endpoint = System.getenv().getOrDefault(
                    "OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:4318");
                telemetry = new ModTelemetry(endpoint);
                telemetry.log("CraftBro AI loaded successfully!");
                Runtime.getRuntime().addShutdownHook(new Thread(telemetry::close, "hellomod-telemetry-shutdown"));
            } catch (RuntimeException error) {
                LOGGER.warn("Telemetry could not start; the mod will continue without it.", error);
            }
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            handler.player.sendSystemMessage(Component.literal(
                "CraftBro AI is ready! Type /askmod for advice."));
            if (telemetry != null) {
                telemetry.playerJoined();
                movementTracker.reset(handler.player.getUUID(), position(handler.player));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (telemetry == null || ++movementTicks < 20) return;
            movementTicks = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive()) continue;
                var movement = movementTracker.sample(player.getUUID(), position(player));
                if (movement != null) telemetry.playerMoved(player.getId(), movement);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            movementTracker.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (telemetry != null) movementTracker.reset(newPlayer.getUUID(), position(newPlayer));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            movementTracker.clear();
            movementTicks = 0;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hellomod")
                .executes(context -> {
                    java.util.function.IntSupplier action = () -> {
                        context.getSource().sendSuccess(
                            () -> Component.literal("CraftBro AI is working! Try /askmod or /aitip."), false);
                        return 1;
                    };
                    return telemetry == null ? action.getAsInt() : telemetry.command(action);
                }))
        );
    }

    private static MovementTracker.Position position(ServerPlayer player) {
        return new MovementTracker.Position(player.getX(), player.getY(), player.getZ(),
            player.level().dimension().location().toString());
    }
}
