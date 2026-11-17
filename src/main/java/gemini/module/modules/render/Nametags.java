package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.TotemPops;
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

public final class Nametags extends Module {
   public static Nametags instance;
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Label other players", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Label hostile and passive mobs", false));
   private final BooleanSetting items = this.addSetting(new BooleanSetting("Items", "Label dropped items with their stack size", false));
   private final BooleanSetting self = this.addSetting(new BooleanSetting("Yourself", "Label yourself in third person and Freecam", false));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest label, in blocks", 64.0, 8.0, 128.0, 4.0));
   private final BooleanSetting health = this.addSetting(new BooleanSetting("Health", "Show health", true));
   private final BooleanSetting ping = this.addSetting(new BooleanSetting("Ping", "Show ping from the player list", true));
   private final BooleanSetting gamemode = this.addSetting(new BooleanSetting("Gamemode", "Show the game mode letter", false));
   private final BooleanSetting pops = this.addSetting(new BooleanSetting("Totem Pops", "Add \"-N\" after the name of anything that has popped totems", true));
   private final BooleanSetting distance = this.addSetting(new BooleanSetting("Distance", "Show distance in blocks", false));
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Label size", 1.25, 0.4, 3.0, 0.05));
   private final BooleanSetting constantSize = this.addSetting(new BooleanSetting("Constant Size", "Grow labels with distance so they stay readable", true));
   private final BooleanSetting throughWalls = this.addSetting(new BooleanSetting("Through Walls", "Keep labels visible behind blocks", true));
   private final BooleanSetting hideVanilla = this.addSetting(new BooleanSetting("Hide Vanilla", "Hide the built-in name tags of labelled players", true));

   private final LabelCache labels = new LabelCache();

   public Nametags() {
      super("Nametags", "Shows labels with health and other details above players and other entities.", Category.RENDER);
      instance = this;
   }

   public boolean replacesVanilla(Entity entity) {
      return this.isEnabled() && this.hideVanilla.get() && entity instanceof Player && this.players.get() && entity != Minecraft.getInstance().player;
   }

   @Override
   public void onDisable() {
      this.labels.clear();
   }

   @Override
   public void onWorldChange() {
      this.labels.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer me = client.player;
      ClientLevel level = client.level;
      if (me != null && level != null) {
         this.labels.beginFrame();
         double limit = this.range.get() * this.range.get();
         boolean showSelf = this.self.get() && client.gameRenderer.mainCamera().isDetached();

         for (Entity entity : level.entitiesForRendering()) {
            if (entity != me || showSelf) {
               double distSq = entity.distanceToSqr(me);
               if (!(distSq > limit)) {
                  double x = Render3D.entityX(entity, partialTick);
                  double y = Render3D.entityY(entity, partialTick) + entity.getBbHeight() + 0.5;
                  double z = Render3D.entityZ(entity, partialTick);
                  if (Render3D.behindCamera(x, y, z, 1.0)) {
                     continue;
                  }

                  String label = this.label(client, entity, Math.sqrt(distSq));
                  if (label != null) {
                     float size = 0.32F * this.scale.floatValue();
                     if (this.constantSize.get()) {
                        size *= (float)Math.max(1.0, Math.sqrt(distSq) / 5.0);
                     }

                     int color = entity instanceof LivingEntity living && this.health.get() && !(entity instanceof Player)
                        ? ColorSetting.hueColor(Mth.clamp(living.getHealth() / Math.max(1.0F, living.getMaxHealth()), 0.0F, 1.0F) * 0.33F, 0.55F, 1.0F)
                        : RenderHelpers.typeColor(entity);
                     Render3D.text(label, x, y, z, color, size, this.throughWalls.get());
                  }
               }
            }
         }
      }
   }

   /**
    * The label text, or null when the entity gets none. Everything a label shows is packed into a signature first;
    * the text is only built again when that changes, not every frame.
    */
   private String label(Minecraft client, Entity entity, double blocks) {
      if (entity instanceof ItemEntity item) {
         if (!this.items.get()) {
            return null;
         }

         ItemStack stack = item.getItem();
         String cached = this.labels.get(entity.getId(), stack, stack.getCount());
         if (cached != null) {
            return cached;
         }

         String name = stack.getHoverName().getString();
         return this.labels.put(entity.getId(), stack, stack.getCount(), stack.getCount() > 1 ? name + " x" + stack.getCount() : name);
      }

      PlayerInfo info = null;
      if (entity instanceof Player player) {
         if (!this.players.get() || player.isSpectator() && player != client.player) {
            return null;
         }

         info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(player.getUUID());
      } else if (!(entity instanceof LivingEntity) || !this.mobs.get()) {
         return null;
      }

      int latency = this.ping.get() && info != null && info.getLatency() > 0 ? Math.min(info.getLatency(), 4095) : 0;
      GameType mode = this.gamemode.get() && info != null ? info.getGameMode() : null;
      int tenths = 0;
      if (this.health.get() && entity instanceof LivingEntity living) {
         // +1 so that showing health at 0 hp differs from not showing it.
         tenths = 1 + Mth.clamp(Math.round((living.getHealth() + living.getAbsorptionAmount()) * 10.0F), 0, (1 << 20) - 2);
      }

      int popped = this.pops.get() ? Math.min(TotemPops.count(entity), 255) : 0;
      int meters = this.distance.get() ? 1 + Math.min((int)blocks, 1022) : 0;
      long signature = (long)tenths
         | (long)latency << 20
         | (long)popped << 32
         | (long)meters << 40
         | (long)(mode == null ? 0 : mode.ordinal() + 1) << 50;
      // A renamed mob gets a new name component, so the name is part of what is compared.
      Object source = entity.getCustomName() != null ? entity.getCustomName() : entity;
      String cached = this.labels.get(entity.getId(), source, signature);
      if (cached != null) {
         return cached;
      }

      StringBuilder text = new StringBuilder(entity.getName().getString());
      if (latency > 0) {
         text.append(' ').append(latency).append("ms");
      }

      if (mode != null) {
         text.append(" [").append(modeLetter(mode)).append(']');
      }

      if (tenths > 0) {
         text.append(' ').append((tenths - 1) / 10.0F);
      }

      if (popped > 0) {
         text.append(" -").append(popped);
      }

      if (meters > 0) {
         text.append(' ').append(meters - 1).append('m');
      }

      return this.labels.put(entity.getId(), source, signature, text.toString());
   }

   private static char modeLetter(GameType type) {
      return switch (type) {
         case SURVIVAL -> 'S';
         case CREATIVE -> 'C';
         case ADVENTURE -> 'A';
         case SPECTATOR -> 'V';
      };
   }
}
