package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class PlayerTrails extends Module {
   private final NumberSetting length = this.addSetting(new NumberSetting("Trail Length", "Number of trail segments", 35.0, 10.0, 100.0, 5.0));
   private final BooleanSetting rainbow = this.addSetting(new BooleanSetting("Rainbow", "Cycle neon rainbow colors along the ribbon", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Ribbon color", 195).showIf(() -> !this.rainbow.get()));
   private final ArrayDeque<Vec3> samples = new ArrayDeque<>();

   public PlayerTrails() {
      super("PlayerTrails", "Render a smooth dual-ribbon neon trail behind you.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.samples.clear();
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.samples.addLast(player.position());

         while (this.samples.size() > this.length.intValue()) {
            this.samples.removeFirst();
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.samples.size() >= 2) {
         Vec3 prev = null;
         int idx = 0;
         int total = this.samples.size();
         int base = this.color.argb();

         for (Vec3 pt : this.samples) {
            if (prev != null) {
               float t = (float)idx / total;
               int c = this.rainbow.get()
                  ? ColorSetting.hueColor((float)((System.currentTimeMillis() / 18L + idx * 10L) % 360L) / 360.0F, 0.78F, 1.0F)
                  : base;
               int faded = Render3D.alpha(c, Math.round(210.0F * t));
               Render3D.lineArgb(prev.add(0.0, 0.12, 0.0), pt.add(0.0, 0.12, 0.0), faded, 2.2F, false);
               Render3D.lineArgb(prev.add(0.0, 1.05, 0.0), pt.add(0.0, 1.05, 0.0), Render3D.alpha(c, Math.round(135.0F * t)), 1.5F, false);
            }

            prev = pt;
            idx++;
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.samples.clear();
   }
}
