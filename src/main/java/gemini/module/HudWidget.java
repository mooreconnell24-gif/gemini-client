package gemini.module;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
import gemini.ui.HudOverlay;
import java.util.List;
import net.minecraft.util.Mth;

public abstract class HudWidget extends Module {
   private final ModeSetting corner;
   private final NumberSetting offsetX;
   private final NumberSetting offsetY;
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Size of this widget", 1.0, 0.5, 2.5, 0.05));
   private final BooleanSetting background = this.addSetting(new BooleanSetting("Background", "Dark glass panel behind the widget", true));
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH;
   private static final float MAGNET = 6.0F;
   private static final float GAP = 4.0F;

   /** The guide lines a drag snapped to, in HUD coordinates (NaN when not snapped on that axis). */
   public static final class Guides {
      public float lineX = Float.NaN;
      public float lineY = Float.NaN;

      public void clear() {
         this.lineX = Float.NaN;
         this.lineY = Float.NaN;
      }
   }

   protected HudWidget(String name, String description, String defaultCorner, int defaultX, int defaultY) {
      super(name, description, Category.HUD);
      this.corner = this.addSetting(
         new ModeSetting(
            "Corner",
            "Screen edge the widget is anchored to",
            defaultCorner,
            "Top Left",
            "Top Right",
            "Bottom Left",
            "Bottom Right",
            "Top Center",
            "Bottom Center",
            "Left Center",
            "Right Center"
         )
      );
      this.offsetX = this.addSetting(new NumberSetting("Offset X", "Distance from the anchor, pushing inward", defaultX, -600.0, 600.0, 1.0));
      this.offsetY = this.addSetting(new NumberSetting("Offset Y", "Distance from the anchor, pushing inward", defaultY, -400.0, 400.0, 1.0));
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   protected abstract int widgetWidth(HudContext var1);

   protected abstract int widgetHeight(HudContext var1);

   protected abstract void paint(HudContext var1, int var2, int var3);

   protected boolean hasBackground() {
      return this.background.get();
   }

   protected boolean shouldRenderContent(HudContext hud) {
      return true;
   }

   public float lastScreenX() {
      return this.lastX;
   }

   public float lastScreenY() {
      return this.lastY;
   }

   public float lastScreenW() {
      return this.lastW;
   }

   public float lastScreenH() {
      return this.lastH;
   }

   public boolean containsScreenPoint(double mx, double my) {
      return this.isEnabled()
         && this.lastW > 0.0F
         && this.lastH > 0.0F
         && mx >= this.lastX
         && mx <= this.lastX + this.lastW
         && my >= this.lastY
         && my <= this.lastY + this.lastH;
   }

   public void applyScreenPosition(int screenWidth, int screenHeight, float newX, float newY, boolean snapGrid) {
      this.applyScreenPosition(screenWidth, screenHeight, newX, newY, snapGrid, List.of(), null);
   }

   /**
    * Moves the widget while dragging. Edges pull toward the screen edges, the screen centre and the edges of the
    * other widgets (aligned, or butted up against them with a small gap); the grid only applies on an axis where
    * nothing magnetic is close. {@code guides}, when given, receives the lines the widget snapped to so the editor
    * can draw them.
    */
   public void applyScreenPosition(int screenWidth, int screenHeight, float newX, float newY, boolean snapGrid, List<HudWidget> others, HudWidget.Guides guides) {
      float w = Math.max(8.0F, this.lastW);
      float h = Math.max(8.0F, this.lastH);
      float clampedX = Mth.clamp(newX, 2.0F, Math.max(2.0F, screenWidth - w - 2.0F));
      float clampedY = Mth.clamp(newY, 2.0F, Math.max(2.0F, screenHeight - h - 2.0F));
      float magnetX = Float.NaN;
      float magnetY = Float.NaN;
      float lineX = Float.NaN;
      float lineY = Float.NaN;
      float bestX = MAGNET;
      float bestY = MAGNET;
      float[] xTargets = {2.0F, screenWidth - 2.0F - w, (screenWidth - w) / 2.0F};
      float[] xLines = {2.0F, screenWidth - 2.0F, screenWidth / 2.0F};
      for (int i = 0; i < xTargets.length; i++) {
         float d = Math.abs(clampedX - xTargets[i]);
         if (d < bestX) {
            bestX = d;
            magnetX = xTargets[i];
            lineX = xLines[i];
         }
      }

      float[] yTargets = {2.0F, screenHeight - 2.0F - h, (screenHeight - h) / 2.0F};
      float[] yLines = {2.0F, screenHeight - 2.0F, screenHeight / 2.0F};
      for (int i = 0; i < yTargets.length; i++) {
         float d = Math.abs(clampedY - yTargets[i]);
         if (d < bestY) {
            bestY = d;
            magnetY = yTargets[i];
            lineY = yLines[i];
         }
      }

      for (HudWidget other : others) {
         if (other == this || !other.isEnabled() || other.lastW <= 0.0F) {
            continue;
         }

         float ol = other.lastX;
         float or = other.lastX + other.lastW;
         float ot = other.lastY;
         float ob = other.lastY + other.lastH;
         float[][] xs = {{ol, ol}, {or - w, or}, {or + GAP, or + GAP}, {ol - GAP - w, ol - GAP}};
         for (float[] candidate : xs) {
            float d = Math.abs(clampedX - candidate[0]);
            if (d < bestX) {
               bestX = d;
               magnetX = candidate[0];
               lineX = candidate[1];
            }
         }

         float[][] ys = {{ot, ot}, {ob - h, ob}, {ob + GAP, ob + GAP}, {ot - GAP - h, ot - GAP}};
         for (float[] candidate : ys) {
            float d = Math.abs(clampedY - candidate[0]);
            if (d < bestY) {
               bestY = d;
               magnetY = candidate[0];
               lineY = candidate[1];
            }
         }
      }

      boolean snappedX = !Float.isNaN(magnetX);
      boolean snappedY = !Float.isNaN(magnetY);
      if (snappedX) {
         clampedX = magnetX;
      } else if (snapGrid) {
         clampedX = Math.round(clampedX / 4.0F) * 4.0F;
      }

      if (snappedY) {
         clampedY = magnetY;
      } else if (snapGrid) {
         clampedY = Math.round(clampedY / 4.0F) * 4.0F;
      }

      if (guides != null) {
         guides.lineX = lineX;
         guides.lineY = lineY;
      }

      float cx = clampedX + w / 2.0F;
      float cy = clampedY + h / 2.0F;
      int horiz = Math.abs(cx - screenWidth / 2.0F) < screenWidth * 0.16F ? 1 : (cx > screenWidth / 2.0F ? 2 : 0);
      int vert = Math.abs(cy - screenHeight / 2.0F) < screenHeight * 0.16F ? 1 : (cy > screenHeight / 2.0F ? 2 : 0);
      String chosenCorner;
      if (horiz == 1 && vert == 0) {
         chosenCorner = "Top Center";
      } else if (horiz == 1 && vert == 2) {
         chosenCorner = "Bottom Center";
      } else if (horiz == 0 && vert == 1) {
         chosenCorner = "Left Center";
      } else if (horiz == 2 && vert == 1) {
         chosenCorner = "Right Center";
      } else if (horiz == 2 && vert == 0) {
         chosenCorner = "Top Right";
      } else if (horiz == 0 && vert == 2) {
         chosenCorner = "Bottom Left";
      } else if (horiz == 2 && vert == 2) {
         chosenCorner = "Bottom Right";
      } else {
         chosenCorner = "Top Left";
      }

      this.corner.set(chosenCorner);

      double ox = switch (horiz) {
         case 1 -> clampedX - (screenWidth - w) / 2.0F;
         case 2 -> screenWidth - w - clampedX;
         default -> clampedX;
      };

      double oy = switch (vert) {
         case 1 -> clampedY - (screenHeight - h) / 2.0F;
         case 2 -> screenHeight - h - clampedY;
         default -> clampedY;
      };
      this.offsetX.set((double)Math.round(ox));
      this.offsetY.set((double)Math.round(oy));
      this.lastX = clampedX;
      this.lastY = clampedY;
      this.save();
   }

   @Override
   public void onRenderHud(HudContext hud) {
      int w = this.widgetWidth(hud);
      int h = this.widgetHeight(hud);
      if (w > 0 && h > 0) {
         float s = this.scale.floatValue();
         float scaledW = w * s;
         float scaledH = h * s;
         String anchor = this.corner.get();

         int horizontal = switch (anchor) {
            case "Top Right", "Bottom Right", "Right Center" -> 2;
            case "Top Center", "Bottom Center" -> 1;
            default -> 0;
         };

         int vertical = switch (anchor) {
            case "Bottom Left", "Bottom Right", "Bottom Center" -> 2;
            case "Left Center", "Right Center" -> 1;
            default -> 0;
         };
         float x = horizontal == 2
            ? hud.width - scaledW - this.offsetX.floatValue()
            : (horizontal == 1 ? (hud.width - scaledW) / 2.0F + this.offsetX.floatValue() : this.offsetX.floatValue());
         float y = vertical == 2
            ? hud.height - scaledH - this.offsetY.floatValue()
            : (vertical == 1 ? (hud.height - scaledH) / 2.0F + this.offsetY.floatValue() : this.offsetY.floatValue());
         this.lastX = x;
         this.lastY = y;
         this.lastW = scaledW;
         this.lastH = scaledH;
         if (this.shouldRenderContent(hud)) {
            hud.graphics.pose().pushMatrix();
            hud.graphics.pose().translate(x, y);
            hud.graphics.pose().scale(s, s);
            if (this.hasBackground()) {
               hud.graphics.fill(-1, -1, w + 1, h + 1, GeminiMark.withAlpha(hud.accent, 42));
               hud.graphics.fill(0, 0, w, h, GeminiMark.withAlpha(-15987178, 198));
               hud.graphics.fill(0, 0, w, 4, GeminiMark.withAlpha(hud.accent, 14));
               int steps = Math.min(18, Math.max(1, w));
               for (int i = 0; i < steps; i++) {
                  int sx = w * i / steps;
                  int ex = w * (i + 1) / steps;
                  float t = steps <= 1 ? 0.0F : (float)i / (steps - 1);
                  hud.graphics.fill(sx, 0, ex, 1, GeminiMark.withAlpha(HudOverlay.lerp(hud.accent, hud.accent2, t), 160));
               }
               hud.graphics.fill(0, 0, 2, h, hud.accent);
            }

            this.paint(hud, w, h);
            hud.graphics.pose().popMatrix();
         }
      }
   }
}
