package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class JumpEffect extends Module {
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Ring accent hue", 198));
   private final ArrayDeque<JumpEffect.Ring> rings = new ArrayDeque<>();
   private boolean wasOnGround = true;

   public JumpEffect() {
      super("JumpEffect", "Render expanding neon ground rings when jumping.", Category.RENDER);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         if (this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.1) {
            this.rings.addLast(new JumpEffect.Ring(player.position(), System.currentTimeMillis()));
            if (this.rings.size() > 8) {
               this.rings.removeFirst();
            }
         }

         this.wasOnGround = player.onGround();
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      long now = System.currentTimeMillis();
      int baseColor = this.color.argb();
      Iterator<JumpEffect.Ring> it = this.rings.iterator();

      while (it.hasNext()) {
         JumpEffect.Ring ring = it.next();
         float t = (float)(now - ring.createdAt) / 900.0F;
         if (t >= 1.0F) {
            it.remove();
         } else {
            double r = 0.2 + t * 1.4;
            int alpha = Math.round(220.0F * (1.0F - t));
            int segments = 20;

            for (int i = 0; i < segments; i++) {
               double a1 = i * Math.PI * 2.0 / segments;
               double a2 = (i + 1) * Math.PI * 2.0 / segments;
               Vec3 p1 = new Vec3(ring.center.x + Math.cos(a1) * r, ring.center.y + 0.04, ring.center.z + Math.sin(a1) * r);
               Vec3 p2 = new Vec3(ring.center.x + Math.cos(a2) * r, ring.center.y + 0.04, ring.center.z + Math.sin(a2) * r);
               Render3D.lineArgb(p1, p2, Render3D.alpha(baseColor, alpha), 2.0F, false);
            }
         }
      }
   }

   private record Ring(Vec3 center, long createdAt) {
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.rings.clear();
   }
}
