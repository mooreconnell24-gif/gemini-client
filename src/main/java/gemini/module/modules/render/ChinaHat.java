package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class ChinaHat extends Module {
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Hat accent hue", 205));
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Hat brim radius", 0.65, 0.3, 1.2, 0.05));

   public ChinaHat() {
      super("ChinaHat", "Render a 3D neon cone hat above your head.", Category.RENDER);
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && !client.options.getCameraType().isFirstPerson()) {
         Vec3 base = player.getPosition(partialTick).add(0.0, player.getBbHeight() + 0.12, 0.0);
         Vec3 tip = base.add(0.0, 0.32, 0.0);
         int argb = this.color.argb();
         double r = this.radius.get();
         int segments = 16;

         for (int i = 0; i < segments; i++) {
            double a1 = i * Math.PI * 2.0 / segments;
            double a2 = (i + 1) * Math.PI * 2.0 / segments;
            Vec3 p1 = base.add(Math.cos(a1) * r, 0.0, Math.sin(a1) * r);
            Vec3 p2 = base.add(Math.cos(a2) * r, 0.0, Math.sin(a2) * r);
            Render3D.line(p1, p2, argb, 1.8F, false);
            Render3D.line(tip, p1, Render3D.alpha(argb, 150), 1.2F, false);
         }
      }
   }
}
