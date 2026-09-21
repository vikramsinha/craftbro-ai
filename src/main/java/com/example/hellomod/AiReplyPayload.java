package com.example.hellomod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client reply, also used on the integrated single-player server. */
public record AiReplyPayload(String question, String answer) implements CustomPacketPayload {
    public static final Type<AiReplyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("hellomod", "ai_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AiReplyPayload> CODEC = StreamCodec.of(
        (buffer, reply) -> { buffer.writeUtf(reply.question(), 500); buffer.writeUtf(reply.answer(), 2000); },
        buffer -> new AiReplyPayload(buffer.readUtf(500), buffer.readUtf(2000)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
