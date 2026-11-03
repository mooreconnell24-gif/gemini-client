package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Radar extends HudWidget {
   private final NumberSetting size = this.addSetting(new NumberSetting("Size", "Radar box size in pixels", 76.0, 50.0, 130.0, 2.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks shown from center", 48.0, 16.0, 128.0, 4.0));

   public Radar() {
      super("Radar", "2D rotating entity radar showing nearby players and mobs.", "Top Left", 6, 40);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.size.intValue();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return this.size.intValue();
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      LocalPlayer self = hud.client.player;
      int cx = w / 2;
      int cy = h / 2;
      hud.graphics.fill(cx, 2, cx + 1, h - 2, 872415231);
      hud.graphics.fill(2, cy, w - 2, cy + 1, 872415231);
      if (self != null && hud.client.level != null) {
         double maxR = this.range.get();
         double yawRad = Math.toRadians(self.getYRot() + 180.0F);
         double cos = Math.cos(yawRad);
         double sin = Math.sin(yawRad);

         for (Entity entity : hud.client.level.entitiesForRendering()) {
            if (entity != self && entity instanceof LivingEntity) {
               double dx = entity.getX() - self.getX();
               double dz = entity.getZ() - self.getZ();
               double rx = dx * cos - dz * sin;
               double rz = dx * sin + dz * cos;
               int px = cx + (int)Math.round(rx / maxR * (cx - 4));
               int py = cy + (int)Math.round(rz / maxR * (cy - 4));
               if (px >= 3 && px < w - 3 && py >= 3 && py < h - 3) {
                  int dotColor = entity instanceof Player ? -16722689 : -1548963;
                  hud.graphics.fill(px - 1, py - 1, px + 2, py + 2, dotColor);
               }
            }
         }
      }

      hud.graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, -1);
   }
}
