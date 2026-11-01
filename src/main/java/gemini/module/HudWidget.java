package gemini.module;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudContext;
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
      float w = Math.max(8.0F, this.lastW);
      float h = Math.max(8.0F, this.lastH);
      float clampedX = Mth.clamp(newX, 2.0F, Math.max(2.0F, screenWidth - w - 2.0F));
      float clampedY = Mth.clamp(newY, 2.0F, Math.max(2.0F, screenHeight - h - 2.0F));
      if (snapGrid) {
         float centerX = (screenWidth - w) / 2.0F;
         float centerY = (screenHeight - h) / 2.0F;
         if (Math.abs(clampedX - centerX) <= 6.0F) {
            clampedX = centerX;
         } else {
            clampedX = Math.round(clampedX / 4.0F) * 4.0F;
         }

         if (Math.abs(clampedY - centerY) <= 6.0F) {
            clampedY = centerY;
         } else {
            clampedY = Math.round(clampedY / 4.0F) * 4.0F;
         }
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
         hud.graphics.pose().pushMatrix();
         hud.graphics.pose().translate(x, y);
         hud.graphics.pose().scale(s, s);
         if (this.hasBackground()) {
            hud.graphics.fill(-1, -1, w + 1, h + 1, GeminiMark.withAlpha(hud.accent, 38));
            hud.graphics.fill(0, 0, w, h, GeminiMark.withAlpha(-15987178, 185));
            hud.graphics.fill(0, 0, w, 1, GeminiMark.withAlpha(hud.accent2, 95));
            hud.graphics.fill(0, 0, 2, h, hud.accent);
         }

         this.paint(hud, w, h);
         hud.graphics.pose().popMatrix();
      }
   }
}
