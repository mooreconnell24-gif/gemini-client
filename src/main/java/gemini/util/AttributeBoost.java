package gemini.util;

import gemini.module.modules.misc.InfernoBypass;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
      return id(id, InfernoBypass.hideBoosts());
   }

   /**
    * The modifier id for a boost. Hidden ids get a prefix, because the Inferno anti-cheat strips modifiers by their
    * exact {@code gemini:<name>} id every tick (see {@link InfernoBypass}).
    */
   private static Identifier id(String id, boolean hidden) {
      return Identifier.fromNamespaceAndPath("gemini", hidden ? "boost_" + id : id);
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
            // Drop the boost under its other id, left over from before Inferno Bypass was switched on or off.
            Identifier stale = id(id, !InfernoBypass.hideBoosts());
            if (own != null) {
               own.removeModifier(stale);
               own.addOrUpdateTransientModifier(new AttributeModifier(identifier, amount, operation));
            }

            onServer(serverPlayer -> {
               AttributeInstance instance = serverPlayer.getAttribute(attribute);
               if (instance != null) {
                  instance.removeModifier(stale);
                  instance.addOrUpdateTransientModifier(new AttributeModifier(identifier, amount, operation));
               }
            });
         }
      }
   }

   public static void clear(Holder<Attribute> attribute, String id) {
      Minecraft client = Minecraft.getInstance();
      Identifier plain = id(id, false);
      Identifier hidden = id(id, true);
      if (client.player != null) {
         AttributeInstance own = client.player.getAttribute(attribute);
         if (own != null) {
            own.removeModifier(plain);
            own.removeModifier(hidden);
         }
      }

      onServer(serverPlayer -> {
         AttributeInstance instance = serverPlayer.getAttribute(attribute);
         if (instance != null) {
            instance.removeModifier(plain);
            instance.removeModifier(hidden);
         }
      });
   }

   /**
    * Clears fall distance on the client and on the integrated server. The server keeps its own count from the
    * movement packets, so resetting only the client copy still lets a long glide or flight hurt on landing.
    */
   public static void resetFall(LocalPlayer player) {
      if (player != null) {
         player.resetFallDistance();
         onServer(serverPlayer -> serverPlayer.resetFallDistance());
      }
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
