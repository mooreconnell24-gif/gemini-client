package com.mooreconnell24.geminiclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class GeminiNetwork {
    public static final Identifier SYNC_PACKET = new Identifier(GeminiClient.MOD_ID, "sync");

    private GeminiNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SYNC_PACKET, (server, player, handler, buf, responseSender) -> {
            boolean enabled = buf.readBoolean();
            server.execute(() -> {
                player.sendMessage(net.minecraft.text.Text.literal("Gemini Client synced: " + enabled), false);
            });
        });
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_PACKET, (client, handler, buf, responseSender) -> {
            boolean enabled = buf.readBoolean();
            client.execute(() -> {
                GeminiConfig.setEnabled(enabled);
            });
        });
    }

    public static void sendSyncToServer(boolean enabled) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeBoolean(enabled);
        ClientPlayNetworking.send(SYNC_PACKET, buffer);
    }
}
