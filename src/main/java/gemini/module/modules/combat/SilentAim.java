package gemini.module.modules.combat;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Sends rotation packets that point at a chosen target while your camera stays where you put it. Other clients and
 * the server see you aiming at the target; your own view, and the crosshair, do not move. Single-player only: the
 * client refuses server connections, so this only affects mobs in your own world. The Aim module draws a marker
 * where the silent rotation points, so you can check it.
 */
public final class SilentAim extends Module {
   private final ModeSetting target = this.addSetting(
      new ModeSetting("Target", "What the server sees you aiming at", "Nearest Mob", "Nearest Mob", "Fixed Angle")
   );
   private final NumberSetting range = this.addSetting(
      new NumberSetting("Range", "How far to look for the nearest mob", 4.5, 1.0, 16.0, 0.5).showIf(() -> "Nearest Mob".equals(this.target.get()))
   );
   private final NumberSetting yaw = this.addSetting(
      new NumberSetting("Yaw", "Yaw the server sees when Target is Fixed Angle", 0.0, -180.0, 180.0, 1.0).showIf(() -> "Fixed Angle".equals(this.target.get()))
   );
   private final NumberSetting pitch = this.addSetting(
      new NumberSetting("Pitch", "Pitch the server sees when Target is Fixed Angle", 0.0, -90.0, 90.0, 1.0).showIf(() -> "Fixed Angle".equals(this.target.get()))
   );
   private final NumberSetting speed = this.addSetting(
      new NumberSetting("Speed", "How quickly the server-side aim turns, in degrees per tick", 60.0, 5.0, 180.0, 5.0)
   );

   public SilentAim() {
      super("Silent Aim", "Make the server see you aiming at a target while your camera stays put.", Category.COMBAT);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.COMBAT;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null || client.gui.screen() != null) {
         return;
      }

      float[] rotation;
      if ("Fixed Angle".equals(this.target.get())) {
         rotation = new float[]{this.yaw.floatValue(), this.pitch.floatValue()};
      } else {
         Entity mob = this.nearestMob(client, player);
         if (mob == null) {
            return;
         }

         rotation = RotationManager.toward(player, mob);
      }

      RotationManager.request(player, rotation, this.speed.floatValue(), true, ActionPriority.COMBAT);
   }

   private Entity nearestMob(Minecraft client, LocalPlayer player) {
      double maxSq = this.range.get() * this.range.get();
      Entity best = null;
      double bestSq = maxSq;
      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity != player && entity instanceof LivingEntity && !(entity instanceof Player) && entity.isAlive()) {
            double d = player.distanceToSqr(entity);
            if (d <= bestSq) {
               bestSq = d;
               best = entity;
            }
         }
      }

      return best;
   }

   @Override
   public String info() {
      return this.target.get();
   }
}
