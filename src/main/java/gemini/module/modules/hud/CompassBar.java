package gemini.module.modules.hud;

import gemini.module.HudWidget;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * A compass strip: thin ticks every 5 degrees (taller every 15 and at the named directions), the direction names
 * under them, and your heading in degrees above a small caret at the centre. The strip's backdrop and everything on
 * it fade out toward both ends, so it has no hard edges. The heading text is rebuilt only when the whole degree
 * changes.
 */
public final class CompassBar extends HudWidget {
   private static final String[] NAMES = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
   /** Heading texts for 0..359, built once, so a frame never formats a number. */
   private static final String[] DEGREES = new String[360];
   private static final int HEIGHT = 30;
   private static final int TICKS_Y = 12;
   private static final int LABEL_Y = 20;
   private final NumberSetting width = this.addSetting(new NumberSetting("Width", "Width of the strip, in pixels", 200.0, 100.0, 400.0, 10.0));
   private final NumberSetting span = this.addSetting(new NumberSetting("Span", "Degrees shown across the strip", 140.0, 60.0, 270.0, 10.0));

   static {
      for (int i = 0; i < 360; i++) {
         DEGREES[i] = Integer.toString(i);
      }
   }

   public CompassBar() {
      super("Compass", "Shows a compass strip with the direction you are facing.", "Top Center", 0, 6);
   }

   @Override
   protected boolean hasBackground() {
      // The strip draws its own backdrop, faded at the ends.
      return false;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      return this.width.intValue() & ~1;
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      return HEIGHT;
   }

   /** The compass heading (0 = north, 90 = east) for a Minecraft yaw (0 = south, 90 = west). */
   static int heading(float yaw) {
      int degrees = Math.round(Mth.wrapDegrees(yaw + 180.0F));
      return Math.floorMod(degrees, 360);
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      Font font = hud.font;
      int backdrop = this.backdrop();
      float fade = w * 0.22F;
      if (backdrop != HudStyle.NONE) {
         int alpha = HudStyle.cardAlpha(backdrop, 1.0F);
         HudStyle.fadedStrip(g, 0, 0, w, h, Math.round(fade), Glass.withAlpha(hud.surface, alpha));
      }

      // Yaw 0 looks south; the strip is laid out in Minecraft yaw so south sits at 0.
      float yaw = Mth.wrapDegrees(hud.client.player.getYRot());
      float pxPerDegree = w / this.span.floatValue();
      float center = w / 2.0F;
      int tickColor = HudStyle.label(backdrop);
      for (int degree = 0; degree < 360; degree += 5) {
         float delta = Mth.wrapDegrees(degree - yaw);
         float fx = center + delta * pxPerDegree;
         float visible = HudStyle.edgeFade(fx, w, fade);
         if (visible <= 0.02F) {
            continue;
         }

         int x = Math.round(fx);
         boolean named = degree % 45 == 0;
         boolean major = degree % 15 == 0;
         int tall = named ? 5 : (major ? 3 : 2);
         int color = named ? Glass.TEXT : tickColor;
         g.fill(x, TICKS_Y + 5 - tall, x + 1, TICKS_Y + 5, Glass.fade(color, visible * (named ? 1.0F : (major ? 0.8F : 0.45F))));
         if (named && visible > 0.06F) {
            String label = NAMES[degree / 45];
            boolean cardinal = degree % 90 == 0;
            int labelColor = cardinal ? Glass.TEXT : tickColor;
            g.text(font, label, x - (font.width(label) - 1) / 2, LABEL_Y, Glass.fade(labelColor, visible), hud.shadow && visible > 0.6F);
         }
      }

      // The heading, centred above a small caret that marks where you look.
      String heading = DEGREES[heading(yaw)];
      int cx = Math.round(center);
      g.text(font, heading, cx - (font.width(heading) - 1) / 2, 0, Glass.TEXT, hud.shadow);
      g.fill(cx - 2, TICKS_Y - 3, cx + 3, TICKS_Y - 2, hud.accent2);
      g.fill(cx - 1, TICKS_Y - 2, cx + 2, TICKS_Y - 1, hud.accent2);
      g.fill(cx, TICKS_Y - 1, cx + 1, TICKS_Y + 5, hud.accent2);
   }
}
