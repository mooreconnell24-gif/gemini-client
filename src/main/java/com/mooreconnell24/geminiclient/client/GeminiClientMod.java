package com.mooreconnell24.geminiclient.client;

import com.mooreconnell24.geminiclient.GeminiClient;
import com.mooreconnell24.geminiclient.GeminiConfig;
import com.mooreconnell24.geminiclient.GeminiNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GeminiClientMod implements ClientModInitializer {
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.geminiclient.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.geminiclient.main"
        ));

        GeminiNetwork.registerClient();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                boolean newState = !GeminiConfig.isEnabled();
                GeminiConfig.setEnabled(newState);
                client.player.sendMessage(Text.literal("Gemini Client toggled: " + newState), false);
                GeminiNetwork.sendSyncToServer(newState);
            }
        });
    }
}
