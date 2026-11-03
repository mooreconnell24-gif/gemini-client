package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class Breadcrumbs extends Module {
   private final NumberSetting maxPoints = this.addSetting(new NumberSetting("Max Points", "How long the trail can get", 400.0, 20.0, 2000.0, 20.0));
   private final NumberSetting spacing = this.addSetting(new NumberSetting("Spacing", "Minimum distance between trail points", 0.6, 0.2, 4.0, 0.1));
   private final NumberSetting width = this.addSetting(new NumberSetting("Line Width", "Thickness of the trail", 2.0, 0.5, 5.0, 0.5));
   private final BooleanSetting fade = this.addSetting(new BooleanSetting("Fade", "Older parts of the trail fade out", true));
   private final BooleanSetting rainbow = this.addSetting(new BooleanSetting("Rainbow", "Color the trail by age instead of one color", false));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Trail color", 190).showIf(() -> !this.rainbow.get()));
   private final ArrayDeque<Vec3> points = new ArrayDeque<>();
   private ClientLevel lastLevel;

   public Breadcrumbs() {
      super("Breadcrumbs", "Draw the path you have walked.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.points.clear();
      this.lastLevel = null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         if (client.level != this.lastLevel) {
            this.points.clear();
            this.lastLevel = client.level;
         }

         Vec3 here = client.player.position().add(0.0, 0.1, 0.0);
         Vec3 last = this.points.peekLast();
         double gap = this.spacing.get();
         if (last == null || last.distanceToSqr(here) >= gap * gap) {
            this.points.addLast(here);

            while (this.points.size() > this.maxPoints.intValue()) {
               this.points.removeFirst();
            }
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      int size = this.points.size();
      if (size >= 2) {
         Vec3 previous = null;
         int index = 0;
         int base = this.color.argb();

         for (Vec3 point : this.points) {
            if (previous != null) {
               float age = (float)index / size;
               int argb = this.rainbow.get() ? ColorSetting.hueColor(age, 0.8F, 1.0F) : base;
               if (this.fade.get()) {
                  argb = Render3D.alpha(argb, 40 + (int)(215.0F * age));
               }

               Render3D.lineArgb(previous, point, argb, this.width.floatValue(), false);
            }

            previous = point;
            index++;
         }
      }
   }
}
