package gemini.module;

import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.UiMath;
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
   private static final float GRID = 4.0F;

   /**
    * The guide lines a drag snapped to, in HUD coordinates (NaN when not snapped on that axis), and what kind of
    * target each one is ({@code UiMath.SNAP_*}: edge, centre, alignment with another widget, gap, equal spacing).
    */
   public static final class Guides {
      public float lineX = Float.NaN;
      public float lineY = Float.NaN;
      public int kindX = UiMath.SNAP_NONE;
      public int kindY = UiMath.SNAP_NONE;

      public void clear() {
         this.lineX = Float.NaN;
         this.lineY = Float.NaN;
         this.kindX = UiMath.SNAP_NONE;
         this.kindY = UiMath.SNAP_NONE;
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

   /** The "Corner" setting: which screen edge or corner the widget is anchored to. */
   protected final String anchor() {
      return this.corner.get();
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
      this.applyScreenPosition(screenWidth, screenHeight, newX, newY, snapGrid, true, List.of(), null);
   }

   public void applyScreenPosition(int screenWidth, int screenHeight, float newX, float newY, boolean snapGrid, List<HudWidget> others, HudWidget.Guides guides) {
      this.applyScreenPosition(screenWidth, screenHeight, newX, newY, snapGrid, true, others, guides);
   }

   /**
    * Moves the widget while dragging. With {@code magnetic}, its edges and centre pull toward the screen edges
    * and centre, toward the edges and centres of the other widgets (aligned, or butted up against them with a
    * small gap), and toward positions that repeat the spacing between two other widgets; the grid only applies
    * on an axis where nothing magnetic is close. {@code guides}, when given, receives the lines the widget
    * snapped to so the editor can draw them.
    */
   public void applyScreenPosition(
      int screenWidth, int screenHeight, float newX, float newY, boolean snapGrid, boolean magnetic, List<HudWidget> others, HudWidget.Guides guides
   ) {
      float w = Math.max(8.0F, this.lastW);
      float h = Math.max(8.0F, this.lastH);
      float clampedX = Mth.clamp(newX, 2.0F, Math.max(2.0F, screenWidth - w - 2.0F));
      float clampedY = Mth.clamp(newY, 2.0F, Math.max(2.0F, screenHeight - h - 2.0F));
      int n = 0;
      float[] xs = new float[others.size()];
      float[] ws = new float[others.size()];
      float[] ys = new float[others.size()];
      float[] hs = new float[others.size()];
      for (HudWidget other : others) {
         if (other != this && other.isEnabled() && other.lastW > 0.0F) {
            xs[n] = other.lastX;
            ws[n] = other.lastW;
            ys[n] = other.lastY;
            hs[n] = other.lastH;
            n++;
         }
      }

      float grid = snapGrid ? GRID : 0.0F;
      UiMath.AxisSnap snapX = magnetic
         ? UiMath.snapAxis(clampedX, w, screenWidth, 2.0F, true, xs, ws, n, MAGNET, GAP, grid, true)
         : UiMath.snapAxis(clampedX, w, screenWidth, 2.0F, false, xs, ws, 0, 0.0F, GAP, grid, false);
      UiMath.AxisSnap snapY = magnetic
         ? UiMath.snapAxis(clampedY, h, screenHeight, 2.0F, true, ys, hs, n, MAGNET, GAP, grid, true)
         : UiMath.snapAxis(clampedY, h, screenHeight, 2.0F, false, ys, hs, 0, 0.0F, GAP, grid, false);
      if (guides != null) {
         guides.lineX = snapX.magnetic() ? snapX.line : Float.NaN;
         guides.lineY = snapY.magnetic() ? snapY.line : Float.NaN;
         guides.kindX = snapX.magnetic() ? snapX.kind : UiMath.SNAP_NONE;
         guides.kindY = snapY.magnetic() ? snapY.kind : UiMath.SNAP_NONE;
      }

      this.placeAt(screenWidth, screenHeight, Mth.clamp(snapX.pos, 0.0F, Math.max(0.0F, screenWidth - w)), Mth.clamp(snapY.pos, 0.0F, Math.max(0.0F, screenHeight - h)), w, h);
   }

   /** Moves the widget by whole pixels from where it was last drawn (arrow-key nudging in the HUD editor). */
   public void nudge(int screenWidth, int screenHeight, int dx, int dy) {
      float w = Math.max(8.0F, this.lastW);
      float h = Math.max(8.0F, this.lastH);
      float x = Mth.clamp(Math.round(this.lastX) + dx, 0.0F, Math.max(0.0F, screenWidth - w));
      float y = Mth.clamp(Math.round(this.lastY) + dy, 0.0F, Math.max(0.0F, screenHeight - h));
      this.placeAt(screenWidth, screenHeight, x, y, w, h);
   }

   /** Stores a screen position as the nearest anchor plus offsets (the saved Corner / Offset X / Offset Y). */
   private void placeAt(int screenWidth, int screenHeight, float clampedX, float clampedY, float w, float h) {
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
               // The shared card: dark glass, rounded corners, a thin accent border and an accent-to-secondary top line.
               Glass.panel(hud.graphics, 0, 0, w, h, Glass.RADIUS, hud.surface, hud.accent, 0.94F, 0.0F);
               Glass.roundOutline(hud.graphics, 0, 0, w, h, Glass.RADIUS, Glass.withAlpha(hud.accent, 96));
               if (w > 2 * Glass.RADIUS + 2) {
                  Glass.gradientBar(hud.graphics, Glass.RADIUS, 0, w - Glass.RADIUS, 1, Glass.withAlpha(hud.accent, 200), Glass.withAlpha(hud.accent2, 200));
               }
            }

            this.paint(hud, w, h);
            hud.graphics.pose().popMatrix();
         }
      }
   }
}
