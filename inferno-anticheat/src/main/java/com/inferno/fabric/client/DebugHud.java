package com.inferno.fabric.client;

import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.checks.PlayerState;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class DebugHud {
   private DebugHud() {
   }

   public static void register() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("inferno", "debug_hud"), DebugHud::extractRenderState);
   }

   private static void extractRenderState(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
      if (InfernoConfig.get().debugHud) {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && !client.gui.hud.isHidden()) {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("§6§lInferno debug");
            IntegratedServer server = client.getSingleplayerServer();
            ServerPlayer self = server == null ? null : server.getPlayerList().getPlayer(UUID.fromString(client.player.getStringUUID()));
            if (self == null) {
               lines.add("§7Only in your own world");
            } else {
               PlayerState s = InfernoMod.ENGINE.state(self);
               lines.add("§7speed §f" + fmt(s.lastHorizontal) + " §7b/t   dy §f" + fmt(s.lastDy));
               lines.add(s.lastGround ? "§aon the ground" : "§cin the air §7" + s.airTicks + " ticks");
               lines.add("§7combat suspicion §f" + InfernoMod.ENGINE.combatSuspicion(self.getStringUUID()) + "%");
               String paused = InfernoMod.ENGINE.notCheckedReason(self);
               lines.add(paused == null ? "§amovement checks active" : "§emovement checks paused: §f" + paused);
               long l = System.currentTimeMillis() - s.lastFlagTime;
               if (s.lastFlagTime > 0L && l < 30000L) {
                  String flag = s.lastFlag.length() > 48 ? s.lastFlag.substring(0, 47) + "…" : s.lastFlag;
                  lines.add("§c" + flag + " §8" + l / 1000L + "s ago");
               } else {
                  lines.add("§8no flags in the last 30 s");
               }
            }

            int width = 0;

            for (String string : lines) {
               width = Math.max(width, client.font.width(string));
            }

            ctx.fill(2, 2, 10 + width, 6 + lines.size() * 10, -1879048192);

            for (int i = 0; i < lines.size(); i++) {
               ctx.text(client.font, lines.get(i), 6, 5 + i * 10, -1, true);
            }
         }
      }
   }

   private static String fmt(double v) {
      return String.format(Locale.ROOT, "%.3f", v);
   }
}
