package gemini.util;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;

public final class AttributeBoost {
   private AttributeBoost() {
   }

   private static Identifier id(String id) {
      return Identifier.fromNamespaceAndPath("gemini", id);
   }

   public static void apply(Holder<Attribute> attribute, String id, double amount, Operation operation) {
      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player != null) {
         Identifier identifier = id(id);
         AttributeInstance own = player.getAttribute(attribute);
         boolean upToDate = own != null
            && own.getModifier(identifier) != null
            && Math.abs(own.getModifier(identifier).amount() - amount) < 1.0E-6
            && own.getModifier(identifier).operation() == operation;
         if (!upToDate) {
            if (own != null) {
               own.addOrUpdateTransientModifier(new AttributeModifier(identifier, amount, operation));
            }

            onServer(serverPlayer -> {
               AttributeInstance instance = serverPlayer.getAttribute(attribute);
               if (instance != null) {
                  instance.addOrUpdateTransientModifier(new AttributeModifier(identifier, amount, operation));
               }
            });
         }
      }
   }

   public static void clear(Holder<Attribute> attribute, String id) {
      Minecraft client = Minecraft.getInstance();
      Identifier identifier = id(id);
      if (client.player != null) {
         AttributeInstance own = client.player.getAttribute(attribute);
         if (own != null) {
            own.removeModifier(identifier);
         }
      }

      onServer(serverPlayer -> {
         AttributeInstance instance = serverPlayer.getAttribute(attribute);
         if (instance != null) {
            instance.removeModifier(identifier);
         }
      });
   }

   public static void onServer(Consumer<ServerPlayer> action) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (server != null && client.player != null) {
         UUID uuid = client.player.getUUID();
         server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
            if (serverPlayer != null) {
               action.accept(serverPlayer);
            }
         });
      }
   }
}
