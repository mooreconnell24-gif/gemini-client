package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.render.Render3D;
import gemini.ui.HudContext;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class HitEffect extends Module {
   private final ModeSetting style = this.addSetting(
      new ModeSetting("Style", "Energy Burst: a growing box; Shockwave: an expanding ring; Lightning: a bolt striking the target", "Energy Burst", "Energy Burst", "Shockwave", "Lightning")
   );
   private final BooleanSetting hitmarker = this.addSetting(new BooleanSetting("Crosshair Hitmarker", "Flash a crisp X hitmarker on your crosshair", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Hit effect hue", 195));
   private final ArrayDeque<HitEffect.HitBurst> bursts = new ArrayDeque<>();
   private long lastHitMs;

   public HitEffect() {
      super("HitEffect", "Show 3D impact bursts and a crosshair hitmarker.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.bursts.clear();
   }

   @Override
   public void onAttack(Entity target) {
      if (target instanceof LivingEntity living) {
         this.lastHitMs = System.currentTimeMillis();
         this.bursts.addLast(new HitEffect.HitBurst(living.position().add(0.0, living.getBbHeight() * 0.55, 0.0), this.lastHitMs));

         while (this.bursts.size() > 10) {
            this.bursts.removeFirst();
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      long now = System.currentTimeMillis();
      int baseColor = this.color.argb();
      String mode = this.style.get();
      Iterator<HitEffect.HitBurst> it = this.bursts.iterator();

      while (it.hasNext()) {
         HitEffect.HitBurst burst = it.next();
         float t = (float)(now - burst.timestamp) / 550.0F;
         if (t >= 1.0F) {
            it.remove();
         } else {
            int alpha = Math.round(235.0F * (1.0F - t));
            int c = Render3D.alpha(baseColor, alpha);
            if (mode.equals("Lightning")) {
               double x = burst.center.x;
               double y = burst.center.y;
               double z = burst.center.z;
               double topY = y + 3.8 * (1.0F - t * 0.3F);
               Render3D.lineArgb(x, topY, z, x + 0.28, y + 2.4, z - 0.22, c, 2.4F, true);
               Render3D.lineArgb(x + 0.28, y + 2.4, z - 0.22, x - 0.24, y + 1.2, z + 0.25, c, 2.4F, true);
               Render3D.lineArgb(x - 0.24, y + 1.2, z + 0.25, x, y, z, c, 2.4F, true);
            } else if (mode.equals("Shockwave")) {
               double r = 0.25 + t * 1.45;
               Render3D.ring(burst.center.x, burst.center.y, burst.center.z, r, 18, c, 2.0F, true);
            } else {
               double s = 0.2 + t * 0.75;
               // The outline fades with the burst (it used to stay fully opaque until it vanished).
               Render3D.boxArgb(
                  burst.center.x - s, burst.center.y - s, burst.center.z - s, burst.center.x + s, burst.center.y + s, burst.center.z + s, c, 0, 1.8F, true
               );
            }
         }
      }
   }

   @Override
   public void onRenderHud(HudContext hud) {
      if (this.hitmarker.get()) {
         long age = System.currentTimeMillis() - this.lastHitMs;
         if (age >= 0L && age < 220L) {
            int cx = hud.width / 2;
            int cy = hud.height / 2;

            for (int d = 3; d <= 7; d++) {
               hud.graphics.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, hud.accent2);
               hud.graphics.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, hud.accent2);
               hud.graphics.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, hud.accent2);
               hud.graphics.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, hud.accent2);
            }
         }
      }
   }

   private record HitBurst(Vec3 center, long timestamp) {
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.bursts.clear();
   }
}
