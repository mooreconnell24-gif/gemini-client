package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudContext;
import net.minecraft.util.Mth;

public final class CompassBar extends HudWidget {
   private static final String[] NAMES = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
   private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Pixel width of the strip", 200.0, 100.0, 400.0, 10.0));
   private final NumberSetting span = this.addSetting(new NumberSetting("Span", "Degrees visible at once", 140.0, 60.0, 270.0, 10.0));

   public CompassBar() {
      super("Compass", "A heading strip with cardinal directions.", "Top Center", 0, 6);
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.width.intValue();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return 22;
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      float yaw = Mth.wrapDegrees(hud.client.player.getYRot() + 180.0F);
      float pxPerDegree = w / this.span.floatValue();
      int center = w / 2;
      hud.graphics.enableScissor(0, 0, w, h);

      for (int degree = 0; degree < 360; degree += 15) {
         float delta = Mth.wrapDegrees(degree - yaw);
         int x = center + Math.round(delta * pxPerDegree);
         if (x >= 0 && x <= w) {
            boolean cardinal = degree % 45 == 0;
            hud.graphics.fill(x, h - (cardinal ? 8 : 4) - 2, x + 1, h - 2, cardinal ? -1511689 : -6641995);
            if (cardinal) {
               String label = NAMES[degree / 45];
               boolean main = degree % 90 == 0;
               hud.graphics.text(hud.font, label, x - hud.font.width(label) / 2, 3, main ? hud.accent : -6641995, hud.shadow);
            }
         }
      }

      hud.graphics.disableScissor();
      hud.graphics.fill(center, 0, center + 1, 4, hud.accent2);
   }
}
