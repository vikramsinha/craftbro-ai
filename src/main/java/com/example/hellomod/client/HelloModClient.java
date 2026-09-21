package com.example.hellomod.client;

import com.example.hellomod.AiReplyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

public final class HelloModClient implements ClientModInitializer {
    private AiReplyPayload lastReply;
    private boolean pending;
    private int relaunchCheckTicks;

    @Override public void onInitializeClient() {
        // Ignore stale requests from a launcher that previously stopped unexpectedly.
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("hellomod-relaunch.request")); }
        catch (java.io.IOException error) { com.example.hellomod.HelloMod.LOGGER.warn("Could not clear relaunch request", error); }
        ClientPlayNetworking.registerGlobalReceiver(AiReplyPayload.TYPE, (reply, context) -> {
            lastReply = reply;
            pending = true;
        });
        // Wait until other menus close; never replace an inventory/death/pause screen.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++relaunchCheckTicks >= 20) {
                relaunchCheckTicks = 0;
                if (java.nio.file.Files.exists(java.nio.file.Path.of("hellomod-relaunch.request"))) {
                    client.stop();
                    return;
                }
            }
            if (pending && client.player != null && client.screen == null) {
                pending = false;
                client.setScreen(new AiReplyScreen(lastReply));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastReply = null;
            pending = false;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("aipanel").executes(context -> {
                if (lastReply == null) context.getSource().sendFeedback(Component.literal("Ask a question with /askmod first."));
                else pending = true;
                return 1;
            })));
    }
}
