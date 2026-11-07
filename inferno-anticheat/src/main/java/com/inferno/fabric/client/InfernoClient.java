package com.inferno.fabric.client;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.admin.AdminRank;
import com.inferno.fabric.admin.OpAuth;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class InfernoClient implements ClientModInitializer {
   private static KeyMapping openPanel;
   private static volatile boolean openNextTick;
   private static boolean greeted;

   public void onInitializeClient() {
      Category category = Category.register(Identifier.fromNamespaceAndPath("inferno", "inferno"));
      openPanel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.inferno.panel", Type.KEYBOARD, 12, category));
      DebugHud.register();
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, context) -> {
               LiteralArgumentBuilder<FabricClientCommandSource> root = (LiteralArgumentBuilder<FabricClientCommandSource>)ClientCommands.literal("inferno")
                  .executes(ctx -> {
                     openNextTick = true;
                     return 1;
                  });
               root.then(ClientCommands.literal("status").executes(ctx -> {
                  ((FabricClientCommandSource)ctx.getSource())
                     .sendFeedback(Component.literal(status(((FabricClientCommandSource)ctx.getSource()).getClient())));
                  return 1;
               }));
               root.then(ClientCommands.literal("login").then(ClientCommands.argument("password", StringArgumentType.greedyString()).executes(ctx -> {
                  Minecraft client = ((FabricClientCommandSource)ctx.getSource()).getClient();
                  if (client.player == null) {
                     return 0;
                  }

                  String pass = StringArgumentType.getString(ctx, "password");
                  AdminRank rank = OpAuth.tryLogin(client.player.getStringUUID(), pass);
                  if (rank.atLeast(AdminRank.HELPER)) {
                     ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§6§lInferno§8] §aLogged in as " + rank.colored()));
                     openNextTick = true;
                  } else {
                     ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§6§lInferno§8] §cWrong OP password"));
                  }

                  return 1;
               })));
               root.then(ClientCommands.literal("logout").executes(ctx -> {
                  Minecraft client = ((FabricClientCommandSource)ctx.getSource()).getClient();
                  if (client.player != null) {
                     OpAuth.logout(client.player.getStringUUID());
                  }

                  ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("§8[§6§lInferno§8] §7Logged out"));
                  return 1;
               }));
               dispatcher.register(root);
            }
         );
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               boolean open = openNextTick;
               openNextTick = false;

               while (openPanel.consumeClick()) {
                  open = true;
               }

               if (open && client.gui.screen() == null) {
                  client.gui.setScreen(new PanelScreen());
               }

               if (client.player == null) {
                  greeted = false;
                  Alerts.hostUuid = "";
                  OpAuth.clearAll();
               } else if (!greeted) {
                  greeted = true;
                  client.gui
                     .hud
                     .getChat()
                     .addClientSystemMessage(
                        Component.literal(
                           "§8[§6§lInferno§8] §7Anti-cheat+ ("
                              + InfernoMod.ENGINE.all().size()
                              + " checks). §fI§7 panel · §f/inferno login <pass>§7 (Owner: §f4566§7)"
                        )
                     );
               }

               if (client.player != null) {
                  Alerts.hostUuid = client.player.getStringUUID();

                  String line;
                  while ((line = Alerts.CHAT.poll()) != null) {
                     client.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
                  }
               }
            }
         );
   }

   static String status(Minecraft client) {
      IntegratedServer server = client.getSingleplayerServer();
      if (server != null && client.player != null) {
         ServerPlayer self = server.getPlayerList().getPlayer(client.player.getUUID());
         if (self == null) {
            return "§8[§6§lInferno§8] §7Still loading, try again in a moment.";
         }

         AdminRank rank = OpAuth.rankOf(self.getStringUUID());
         String rankLine = rank.atLeast(AdminRank.HELPER) ? " §8| rank " + rank.colored() : " §8| §7not logged in";
         String reason = InfernoMod.ENGINE.notCheckedReason(self);
         return reason == null ? "§8[§6§lInferno§8] §aYou are being checked." + rankLine : "§8[§6§lInferno§8] §ePaused: §f" + reason + rankLine;
      } else {
         return "§8[§6§lInferno§8] §7Inferno only runs in your own world (single-player or LAN host).";
      }
   }
}
