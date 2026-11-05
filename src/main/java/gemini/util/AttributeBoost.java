package gemini.util;

import gemini.module.modules.misc.BypassModule;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

/**
 * Applies and removes the transient attribute modifiers behind Step, High Jump, Reach and Fast Break.
 *
 * <p>A client-side anti-cheat running in the same game can read these modifiers and strip them. Two things make
 * that hard, both driven by {@link BypassModule}: with Hide Boosts on, the modifiers get a random namespace this
 * launch, so the anti-cheat has no id to look for; and {@link #reapplyActive()}, called at the very end of each
 * tick after other mods run, puts back anything that was stripped. Neither depends on which anti-cheat it is.
 */
public final class AttributeBoost {
   /** A random namespace for this launch, so hidden modifier ids can't be predicted. */
   private static final String HIDDEN_NAMESPACE = randomNamespace();
   /** The boosts currently applied, by logical id, so they can be re-applied after another mod strips them. */
   private static final Map<String, AttributeBoost.Boost> ACTIVE = new ConcurrentHashMap<>();

   private AttributeBoost() {
   }

   private static String randomNamespace() {
      String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
      java.util.Random random = new java.util.Random();
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < 10; i++) {
         builder.append(chars.charAt(random.nextInt(chars.length())));
      }

      return builder.toString();
   }

   private static Identifier id(String id) {
      return id(id, BypassModule.hideBoosts());
   }

   /**
    * The modifier id for a boost. Hidden ids use a random namespace generated this launch instead of
    * {@code gemini:<name>}, so an anti-cheat can't strip them by a known id (see {@link BypassModule}).
    */
   private static Identifier id(String id, boolean hidden) {
      return Identifier.fromNamespaceAndPath(hidden ? HIDDEN_NAMESPACE : "gemini", id);
   }

   public static void apply(Holder<Attribute> attribute, String id, double amount, Operation operation) {
      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player != null) {
         ACTIVE.put(id, new AttributeBoost.Boost(attribute, amount, operation));
         Identifier identifier = id(id);
         AttributeInstance own = player.getAttribute(attribute);
         boolean upToDate = own != null
            && own.getModifier(identifier) != null
            && Math.abs(own.getModifier(identifier).amount() - amount) < 1.0E-6
            && own.getModifier(identifier).operation() == operation;
         if (!upToDate) {
            // Drop the boost under its other id, left over from before Hide Boosts was switched on or off.
            Identifier stale = id(id, !BypassModule.hideBoosts());
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
      ACTIVE.remove(id);
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
    * Re-applies every boost that is currently meant to be on, on the client and the integrated server. Gemini
    * calls this at the end of each tick, after other mods (so an anti-cheat that strips a boost on its own tick
    * hook has it back before the next tick). It only touches boosts a module applied and has not cleared, so a
    * boost you turned off stays off.
    */
   public static void reapplyActive() {
      if (ACTIVE.isEmpty()) {
         return;
      }

      Minecraft client = Minecraft.getInstance();
      Player player = client.player;
      if (player == null) {
         return;
      }

      for (Map.Entry<String, AttributeBoost.Boost> entry : ACTIVE.entrySet()) {
         AttributeBoost.Boost boost = entry.getValue();
         Identifier identifier = id(entry.getKey());
         AttributeModifier modifier = new AttributeModifier(identifier, boost.amount(), boost.operation());
         AttributeInstance own = player.getAttribute(boost.attribute());
         if (own != null && !sameModifier(own.getModifier(identifier), modifier)) {
            own.addOrUpdateTransientModifier(modifier);
         }

         onServer(serverPlayer -> {
            AttributeInstance instance = serverPlayer.getAttribute(boost.attribute());
            if (instance != null && !sameModifier(instance.getModifier(identifier), modifier)) {
               instance.addOrUpdateTransientModifier(modifier);
            }
         });
      }
   }

   private static boolean sameModifier(AttributeModifier current, AttributeModifier wanted) {
      return current != null && Math.abs(current.amount() - wanted.amount()) < 1.0E-6 && current.operation() == wanted.operation();
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

   private record Boost(Holder<Attribute> attribute, double amount, Operation operation) {
   }
}
