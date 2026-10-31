package com.mooreconnell24.geminiclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GeminiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("gemini_client.json");

    private boolean enabled = true;
    private int hudScale = 100;

    public static boolean isEnabled() {
        return load().enabled;
    }

    public static void setEnabled(boolean enabled) {
        GeminiConfig config = load();
        config.enabled = enabled;
        save(config);
    }

    public static int getHudScale() {
        return load().hudScale;
    }

    public static void setHudScale(int hudScale) {
        GeminiConfig config = load();
        config.hudScale = hudScale;
        save(config);
    }

    public static GeminiConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            GeminiConfig config = new GeminiConfig();
            save(config);
            return config;
        }

        try {
            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            return GSON.fromJson(json, GeminiConfig.class);
        } catch (IOException e) {
            return new GeminiConfig();
        }
    }

    private static void save(GeminiConfig config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
