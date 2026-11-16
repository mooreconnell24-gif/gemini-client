package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Nearby living entities on a small map that turns with you: straight ahead is up. Everything is drawn inside the
 * card (clipped to it), players in the accent colour, monsters red and other mobs grey.
 */
public final class Radar extends HudWidget {
   private static final int EDGE = 3;
   private final NumberSetting size = this.addSetting(new NumberSetting("Size", "Size of the radar, in pixels", 76.0, 50.0, 130.0, 2.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks shown from the centre", 48.0, 16.0, 128.0, 4.0));

   public Radar() {
      super("Radar", "Shows nearby players and mobs on a small map that turns with you.", "Top Left", 6, 40);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.size.intValue();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.size.intValue();
   }

   /** Sideways offset of a point from the player as seen on the radar: positive is to the player's right. */
   static double right(double dx, double dz, float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      // Minecraft looks along (-sin yaw, cos yaw); the player's right is (-cos yaw, -sin yaw).
      return -dx * Math.cos(yaw) - dz * Math.sin(yaw);
   }

   /** Forward offset of a point from the player: positive is ahead (drawn upward). */
   static double forward(double dx, double dz, float yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      return -dx * Math.sin(yaw) + dz * Math.cos(yaw);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer self = hud.client.player;
      int cx = w / 2;
      int cy = h / 2;
      int faint = Glass.withAlpha(Glass.TEXT_FAINT, 90);
      hud.graphics.fill(cx, EDGE, cx + 1, h - EDGE, faint);
      hud.graphics.fill(EDGE, cy, w - EDGE, cy + 1, faint);
      if (self != null && hud.client.level != null) {
         double maxR = this.range.get();
         float yaw = self.getYRot();
         double sin = Math.sin(Math.toRadians(yaw));
         double cos = Math.cos(Math.toRadians(yaw));
         double half = Math.min(cx, cy) - EDGE - 1;
         hud.graphics.enableScissor(1, 1, w - 1, h - 1);
         for (Entity entity : hud.client.level.entitiesForRendering()) {
            if (entity != self && entity instanceof LivingEntity && entity.isAlive()) {
               double dx = entity.getX() - self.getX();
               double dz = entity.getZ() - self.getZ();
               if (Math.abs(dx) > maxR || Math.abs(dz) > maxR) {
                  continue;
               }

               // same maths as right()/forward(), with the trigonometry done once per frame
               double rx = -dx * cos - dz * sin;
               double fw = -dx * sin + dz * cos;
               int px = cx + (int)Math.round(rx / maxR * half);
               int py = cy - (int)Math.round(fw / maxR * half);
               if (px >= EDGE && px < w - EDGE && py >= EDGE && py < h - EDGE) {
                  int color = entity instanceof Player ? hud.accent : (entity instanceof Enemy ? Glass.ERROR : Glass.TEXT_DIM);
                  hud.graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
               }
            }
         }

         hud.graphics.disableScissor();
      }

      // you, at the centre, with a tick pointing ahead
      hud.graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, Glass.TEXT);
      hud.graphics.fill(cx, cy - 4, cx + 1, cy - 1, Glass.TEXT);
   }
}
