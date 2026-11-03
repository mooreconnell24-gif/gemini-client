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
      new ModeSetting("Style", "3D hit burst visual", "Energy Burst", "Energy Burst", "Shockwave", "Lightning")
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
               Vec3 top = burst.center.add(0.0, 3.8 * (1.0F - t * 0.3F), 0.0);
               Vec3 mid1 = burst.center.add(0.28, 2.4, -0.22);
               Vec3 mid2 = burst.center.add(-0.24, 1.2, 0.25);
               Render3D.lineArgb(top, mid1, c, 2.4F, true);
               Render3D.lineArgb(mid1, mid2, c, 2.4F, true);
               Render3D.lineArgb(mid2, burst.center, c, 2.4F, true);
            } else if (mode.equals("Shockwave")) {
               double r = 0.25 + t * 1.45;
               int segs = 18;

               for (int i = 0; i < segs; i++) {
                  double a1 = i * Math.PI * 2.0 / segs;
                  double a2 = (i + 1) * Math.PI * 2.0 / segs;
                  Render3D.lineArgb(
                     new Vec3(burst.center.x + Math.cos(a1) * r, burst.center.y, burst.center.z + Math.sin(a1) * r),
                     new Vec3(burst.center.x + Math.cos(a2) * r, burst.center.y, burst.center.z + Math.sin(a2) * r),
                     c,
                     2.0F,
                     true
                  );
               }
            } else {
               double s = 0.2 + t * 0.75;
               AABB box = new AABB(burst.center.x - s, burst.center.y - s, burst.center.z - s, burst.center.x + s, burst.center.y + s, burst.center.z + s);
               Render3D.box(box, baseColor, true, false, 1.8F, alpha / 3, true);
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
}
