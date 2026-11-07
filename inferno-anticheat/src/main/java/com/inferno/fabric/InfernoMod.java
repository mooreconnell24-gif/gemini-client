package com.inferno.fabric;

import com.inferno.fabric.admin.AdminCommands;
import com.inferno.fabric.admin.AdminService;
import com.inferno.fabric.admin.BanList;
import com.inferno.fabric.admin.ExemptStore;
import com.inferno.fabric.checks.CheckEngine;
import com.inferno.fabric.checks.ClientBrands;
import com.inferno.fabric.checks.PacketCounter;
import java.util.Date;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InfernoMod implements ModInitializer {
   public static final String ID = "inferno";
   public static final Logger LOGGER = LoggerFactory.getLogger("Inferno");
   public static final CheckEngine ENGINE = new CheckEngine();
   public static volatile MinecraftServer server;

   public void onInitialize() {
      if (Boolean.getBoolean("inferno.mixinSelfTest")) {
         selfTest();
      }

      InfernoConfig.get();
      BanList.load();
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> AdminCommands.register(dispatcher));
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)s -> {
         server = s;
         ENGINE.reset();
         Alerts.resetFlagCounts();
         BanList.load();
      });
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)s -> {
         server = null;
         ENGINE.reset();
      });
      ServerTickEvents.START_SERVER_TICK.register(ENGINE::startTick);
      ServerTickEvents.END_SERVER_TICK.register((EndTick)s -> {
         ENGINE.tick(s);
         AdminService.tickFrozen(s);
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, s) -> {
         ServerPlayer player = handler.player;
         BanList.find(player.getStringUUID()).ifPresent(ban -> {
            String until = ban.permanent() ? "permanently" : "until " + new Date(ban.expiresAt);
            player.connection.disconnect(Component.literal("§c§lInferno Ban\n\n§7" + ban.reason + "\n§8Banned " + until));
         });
         ENGINE.join(player);
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, s) -> {
         ServerPlayer player = handler.player;
         String uuid = player.getStringUUID();
         ENGINE.leave(player);
         Alerts.forget(uuid);
         AdminService.leaveCleanup(uuid);
         PacketCounter.forget(player.getUUID());
         ClientBrands.forget(player.getUUID());
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
         if (entity instanceof ServerPlayer sp) {
            ENGINE.death(sp);
         }
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ENGINE.respawned(newPlayer));
      ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
         try {
            ENGINE.chat(sender, message.signedContent());
         } catch (RuntimeException e) {
            LOGGER.error("Inferno check error (please report this)", e);
         }

         return true;
      });
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp) {
            if (!ExemptStore.isExempt(sp.getStringUUID()) && !AdminService.isFrozen(sp.getStringUUID())) {
               return (InteractionResult)(ENGINE.attack(sp, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
            } else {
               return (InteractionResult)(AdminService.isFrozen(sp.getStringUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
            }
         } else {
            return InteractionResult.PASS;
         }
      });
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp) {
            if (ExemptStore.isExempt(sp.getStringUUID()) || AdminService.isFrozen(sp.getStringUUID())) {
               return (InteractionResult)(AdminService.isFrozen(sp.getStringUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
            } else if (ENGINE.useBlock(sp, hit)) {
               return InteractionResult.FAIL;
            } else if (!(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
               return InteractionResult.PASS;
            } else {
               return (InteractionResult)(ENGINE.place(sp, hit) ? InteractionResult.FAIL : InteractionResult.PASS);
            }
         } else {
            return InteractionResult.PASS;
         }
      });
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp && !ExemptStore.isExempt(sp.getStringUUID())) {
            ENGINE.useItem(sp, hand);
         }

         return InteractionResult.PASS;
      });
      AttackBlockCallback.EVENT.register((AttackBlockCallback)(player, world, hand, pos, direction) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp && !ExemptStore.isExempt(sp.getStringUUID())) {
            ENGINE.digStart(sp, pos);
         }

         return InteractionResult.PASS;
      });
      PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> {
         if (!world.isClientSide() && player instanceof ServerPlayer sp) {
            return ExemptStore.isExempt(sp.getStringUUID()) ? true : !ENGINE.breakBlock(sp, pos);
         } else {
            return true;
         }
      });
      LOGGER.info("Inferno Anti Cheat+ loaded with {} checks. Press I for panel, /infernoadmin for ops.", ENGINE.all().size());
   }

   private static void selfTest() {
      String[] targets = new String[]{
         "net.minecraft.server.network.ServerGamePacketListenerImpl",
         "net.minecraft.server.network.ServerCommonPacketListenerImpl",
         "net.minecraft.world.entity.LivingEntity",
         "net.minecraft.world.entity.projectile.FishingHook",
         "net.minecraft.commands.Commands",
         "net.minecraft.network.Connection"
      };

      for (String target : targets) {
         try {
            Class.forName(target, false, InfernoMod.class.getClassLoader());
            LOGGER.info("[mixin self-test] {} OK", target);
         } catch (Throwable t) {
            LOGGER.error("[mixin self-test] {} FAILED", target, t);
         }
      }
   }
}
