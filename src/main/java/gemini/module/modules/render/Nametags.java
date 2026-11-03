package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class Nametags extends Module {
   public static Nametags instance;
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Label other players", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Label hostile and passive mobs", false));
   private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Label dropped items with their stack size", false));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Yourself", "Label yourself in third person and Freecam", false));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Longest label distance", 64.0, 8.0, 128.0, 4.0));
   private final BooleanSetting health = this.addSetting(new BooleanSetting("Health", "Show hit points", true));
   private final BooleanSetting ping = this.addSetting(new BooleanSetting("Ping", "Show latency from the tab list", true));
   private final BooleanSetting gamemode = this.addSetting(new BooleanSetting("Gamemode", "Show the game mode letter", false));
   private final BooleanSetting distance = this.addSetting(new BooleanSetting("Distance", "Show distance in blocks", false));
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Label size", 1.25, 0.4, 3.0, 0.05));
   private final BooleanSetting constantSize = this.addSetting(new BooleanSetting("Constant Size", "Grow labels with distance so they stay readable", true));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Keep labels visible behind blocks", true));
   private final BooleanSetting hideVanilla = this.addSetting(new BooleanSetting("Hide Vanilla", "Hide the built-in name tags of labelled players", true));

   public Nametags() {
      super("Nametags", "Readable labels with health and ping above players.", Category.RENDER);
      instance = this;
   }

   public boolean replacesVanilla(Entity entity) {
      return this.isEnabled() && this.hideVanilla.get() && entity instanceof Player && this.players.get() && entity != Minecraft.getInstance().player;
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer me = client.player;
      ClientLevel level = client.level;
      if (me != null && level != null) {
         double limit = this.range.get() * this.range.get();
         boolean showSelf = this.self.get() && client.gameRenderer.mainCamera().isDetached();

         for (Entity entity : level.entitiesForRendering()) {
            if (entity != me || showSelf) {
               double distSq = entity.distanceToSqr(me);
               if (!(distSq > limit)) {
                  String label = this.label(client, entity, Math.sqrt(distSq));
                  if (label != null) {
                     Vec3 at = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() + 0.5, 0.0);
                     float size = 0.32F * this.scale.floatValue();
                     if (this.constantSize.get()) {
                        size *= (float)Math.max(1.0, Math.sqrt(distSq) / 5.0);
                     }

                     int color = entity instanceof LivingEntity living && this.health.get() && !(entity instanceof Player)
                        ? ColorSetting.hueColor(Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F) * 0.33F, 0.55F, 1.0F)
                        : RenderHelpers.typeColor(entity);
                     Render3D.text(label, at, color, size, this.throughWalls.get());
                  }
               }
            }
         }
      }
   }

   private String label(Minecraft client, Entity entity, double blocks) {
      StringBuilder text = new StringBuilder();
      if (entity instanceof Player player) {
         if (!this.players.get() || player.isSpectator() && player != client.player) {
            return null;
         }

         text.append(player.getName().getString());
         PlayerInfo info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player.getUUID());
         if (this.ping.get() && info != null && info.getLatency() > 0) {
            text.append(' ').append(info.getLatency()).append("ms");
         }

         if (this.gamemode.get() && info != null) {
            text.append(" [").append(modeLetter(info.getGameMode())).append(']');
         }
      } else {
         if (entity instanceof ItemEntity item) {
            if (!this.items.get()) {
               return null;
            }

            ItemStack stack = item.getItem();
            text.append(stack.getHoverName().getString());
            if (stack.getCount() > 1) {
               text.append(" x").append(stack.getCount());
            }

            return text.toString();
         }

         if (!(entity instanceof LivingEntity)) {
            return null;
         }

         if (!this.mobs.get()) {
            return null;
         }

         text.append(entity.getName().getString());
      }

      if (this.health.get() && entity instanceof LivingEntity living) {
         float hp = living.getHealth() + living.getAbsorptionAmount();
         text.append(' ').append(String.format(Locale.ROOT, "%.1f", hp));
      }

      if (this.distance.get()) {
         text.append(' ').append((int)blocks).append('m');
      }

      return text.toString();
   }

   private static char modeLetter(GameType type) {
      return switch (type) {
         case SURVIVAL -> 'S';
         case CREATIVE -> 'C';
         case ADVENTURE -> 'A';
         case SPECTATOR -> 'V';
         default -> throw new MatchException(null, null);
      };
   }
}
