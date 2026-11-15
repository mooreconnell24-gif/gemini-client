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
         double x = Render3D.entityX(player, partialTick);
         double y = Render3D.entityY(player, partialTick) + player.getBbHeight() + 0.12;
         double z = Render3D.entityZ(player, partialTick);
         int argb = this.color.argb();
         int spoke = Render3D.alpha(argb, 150);
         double r = this.radius.get();
         int segments = 16;
         Render3D.ring(x, y, z, r, segments, Render3D.alpha(argb, 255), 1.8F, false);

         for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2.0 / segments;
            Render3D.lineArgb(x, y + 0.32, z, x + Math.cos(angle) * r, y, z + Math.sin(angle) * r, spoke, 1.2F, false);
         }
      }
   }
}
