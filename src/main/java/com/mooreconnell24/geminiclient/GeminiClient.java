package com.mooreconnell24.geminiclient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

public class GeminiClient implements ModInitializer {
    public static final String MOD_ID = "gemini_client";
    public static final String MOD_NAME = "Gemini Client";

    @Override
    public void onInitialize() {
        GeminiNetwork.registerServer();
        registerCommands();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = com.mojang.brigadier.builder.LiteralArgumentBuilder.literal("gemini");

            root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<ServerCommandSource>literal("status")
                .executes(context -> {
                    context.getSource().sendMessage(Text.literal("Gemini Client is online and multiplayer-safe."));
                    return Command.SINGLE_SUCCESS;
                }));

            root.then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<ServerCommandSource>literal("toggle")
                .executes(context -> {
                    boolean newState = !GeminiConfig.isEnabled();
                    GeminiConfig.setEnabled(newState);
                    context.getSource().sendMessage(Text.literal("Gemini Client toggled to: " + newState));
                    return Command.SINGLE_SUCCESS;
                }));

            dispatcher.register(root);
        });
    }
}
