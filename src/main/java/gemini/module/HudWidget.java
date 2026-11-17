package gemini.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.UiMath;
import gemini.ui.clickgui.Menus;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * A movable HUD card.
 *
 * <p>Position: a widget is either <b>placed</b> or <b>automatic</b>. A placed widget sits exactly where the user put
 * it (its Corner plus Offset X / Offset Y). An automatic widget ignores the offsets and is stacked by
 * {@link HudFlow} into the stack its Corner names, next to the other automatic widgets and around everything
 * already on screen, so two of them never overlap. A widget becomes placed when it is dragged or nudged in the HUD
 * editor, or when its offsets are edited in the menu; "Reset Position" makes it automatic again. The state is the
 * hidden {@code Placed} setting, so it travels with configs and profiles; configs written before it existed are
 * read by {@link #readExtra}: a widget whose Corner and offsets still hold the old defaults was never moved and
 * becomes automatic, anything else stays where the user put it.
 */
public abstract class HudWidget extends Module {
   /**
    * Where each widget stacks by default and in what order, top of each list first. Widgets not listed keep the
    * corner they were created with and go after the listed ones.
    */
   private static final String[][] HOMES = {
      {"Top Left", "Position", "Facing", "Biome", "Chunk Info", "Light Level", "Day Info", "Weather Forecast", "Spawn Info", "Last Death", "Radar"},
      {"Top Right", "ArrayList", "Mob Caps", "Entity Counter", "AntiLag Status"},
      {"Right Center", "Potion Timers", "Free Slots", "Food Info", "Xp Info", "Item Counter"},
      {"Top Center", "Compass", "Looking At", "Lag Notifier", "Welcome", "Cooldowns"},
      {"Bottom Center", "TargetHUD", "Armor", "Held Item", "Totem Counter", "Combat Items"},
      {"Bottom Right", "FPS", "TPS", "Speedometer", "CPS", "Memory", "Clock", "Session Timer", "Session Stats", "FPS Graph"},
      {"Bottom Left", "Keystrokes", "Inventory Viewer", "Ender Chest Preview"},
      {"Left Center", "Keybinds", "Custom Text"}
   };
   private static int created;
   /** Counts layout passes; a widget measured in the current pass is drawn with that measurement. */
   private static int frame;
   private final String legacyCorner;
   private final int legacyX;
   private final int legacyY;
   private final int flowOrder;
   private final ModeSetting corner;
   private final NumberSetting offsetX;
   private final NumberSetting offsetY;
   private final BooleanSetting placed;
   private final NumberSetting scale = this.addSetting(new NumberSetting("Scale", "Size of this widget", 1.0, 0.5, 2.5, 0.05));
   private final BooleanSetting background = this.addSetting(
      new BooleanSetting("Background", "Draw the HUD backdrop behind this widget; its look (Soft, Solid or None) is the HUD module's Backdrop setting", true)
   );
   private float lastX;
   private float lastY;
   private float lastW;
   private float lastH;
   private int measuredFrame = -1;
   private int measuredW;
   private int measuredH;
   private boolean measuredVisible;
   private boolean measuredPreview;
   private int flowSlot = -1;
   /** The backdrop this frame's paint uses ({@link HudStyle}): the HUD-wide look, or none when this widget's Background is off. */
   private int backdrop = HudStyle.SOFT;
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

   /**
    * {@code defaultCorner}, {@code defaultX} and {@code defaultY} are the widget's original defaults; they stay the
    * offsets' defaults and are how an old config's untouched widget is recognised. The Corner's default is the
    * widget's home stack from {@link #HOMES}.
    */
   protected HudWidget(String name, String description, String defaultCorner, int defaultX, int defaultY) {
      super(name, description, Category.HUD);
      this.legacyCorner = defaultCorner;
      this.legacyX = defaultX;
      this.legacyY = defaultY;
      String home = defaultCorner;
      int order = 1000 + created++;
      for (String[] stack : HOMES) {
         for (int i = 1; i < stack.length; i++) {
            if (stack[i].equals(name)) {
               home = stack[0];
               order = i;
            }
         }
      }

      this.flowOrder = order;
      this.corner = this.addSetting(
         new ModeSetting(
            "Corner",
            "Screen edge the widget is anchored to. Until you move the widget, it stacks with the others at this edge",
            home,
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
      this.offsetX = this.addSetting(
         new NumberSetting("Offset X", "Distance from the anchor, pushing inward. Changing it pins the widget at that spot", defaultX, -600.0, 600.0, 1.0)
      );
      this.offsetY = this.addSetting(
         new NumberSetting("Offset Y", "Distance from the anchor, pushing inward. Changing it pins the widget at that spot", defaultY, -400.0, 400.0, 1.0)
      );
      this.placed = this.addSetting(
         new BooleanSetting("Placed", "Whether you moved this widget yourself; automatic widgets stack without overlapping", false).showIf(() -> false)
      );
      this.addSetting(
         new ActionSetting("Reset Position", "Put the widget back into the automatic stack at its corner", this::resetPosition).showIf(this::isPlaced)
      );
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   protected abstract int widgetWidth(HudContext var1);

   protected abstract int widgetHeight(HudContext var1);

   protected abstract void paint(HudContext var1, int var2, int var3);

   /**
    * Whether the shared card is drawn behind {@link #paint}. Widgets that draw their own shapes on the backdrop
    * (key caps, a round radar, a fading strip) return false and use {@link #backdrop()} themselves.
    */
   protected boolean hasBackground() {
      return this.background.get();
   }

   /**
    * The backdrop to paint with this frame, one of {@link HudStyle#SOFT}, {@link HudStyle#SOLID} or
    * {@link HudStyle#NONE}: the HUD-wide choice, or none when this widget's Background switch is off.
    */
   protected final int backdrop() {
      return this.backdrop;
   }

   /** The "Corner" setting: which screen edge or corner the widget is anchored to (or stacks at, while automatic). */
   protected final String anchor() {
      return this.corner.get();
   }

   /** Distance from the screen edge at the start of the widget's automatic stack. */
   protected int flowInset() {
      return 4;
   }

   protected boolean shouldRenderContent(HudContext hud) {
      return true;
   }

   /** True once the user moved the widget (drag, nudge, or an edited offset); false while it stacks automatically. */
   public boolean isPlaced() {
      return this.placed.get() || !this.offsetX.isDefault() || !this.offsetY.isDefault();
   }

   /** Back to the automatic stack at the widget's home corner. */
   public void resetPosition() {
      this.placed.set(false);
      this.offsetX.reset();
      this.offsetY.reset();
      this.corner.reset();
      this.save();
   }

   /**
    * Configs written before the Placed setting existed: a widget whose Corner and offsets are still the original
    * defaults was never moved, so it joins the automatic layout at its home corner; any other values were set by the
    * user and the widget stays placed there.
    */
   @Override
   public void readExtra(JsonObject object) {
      super.readExtra(object);
      JsonElement settings = object.get("settings");
      if (settings == null || !settings.isJsonObject() || settings.getAsJsonObject().has(this.placed.name())) {
         return;
      }

      boolean untouched = this.corner.get().equals(this.legacyCorner)
         && Math.round(this.offsetX.get()) == this.legacyX
         && Math.round(this.offsetY.get()) == this.legacyY;
      if (untouched) {
         this.placed.set(false);
         this.offsetX.reset();
         this.offsetY.reset();
         this.corner.reset();
      } else {
         this.placed.set(true);
      }
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

   /** Stores a screen position as the nearest anchor plus offsets (the saved Corner / Offset X / Offset Y) and pins the widget there. */
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
      this.placed.set(true);
      this.lastX = clampedX;
      this.lastY = clampedY;
      this.save();
   }

   /**
    * Sizes the widget for this frame: its own size, or a small name card while a Gemini menu is open and the widget
    * has nothing to show (so it can still be found and dragged), or nothing at all.
    */
   private void measure(HudContext hud) {
      this.measuredFrame = frame;
      int w = this.widgetWidth(hud);
      int h = this.widgetHeight(hud);
      this.measuredPreview = false;
      boolean visible = w > 0 && h > 0 && this.shouldRenderContent(hud);
      if (!visible && Menus.isMenu(hud.client.gui.screen())) {
         this.measuredPreview = true;
         w = hud.font.width(this.getName()) + 12;
         h = 16;
         visible = true;
      }

      this.measuredW = w;
      this.measuredH = h;
      this.measuredVisible = visible;
      this.flowSlot = -1;
   }

   /** Extra scale for widgets laid out automatically, below 1 only when they would not all fit at full size. */
   private static float fitScale = 1.0F;

   /** This widget's own Scale times the shared fit scale (which never applies to widgets the user placed). */
   private float effectiveScale() {
      return this.isPlaced() ? this.scale.floatValue() : this.scale.floatValue() * fitScale;
   }

   private int scaledW() {
      return Mth.ceil(this.measuredW * this.effectiveScale());
   }

   private int scaledH() {
      return Mth.ceil(this.measuredH * this.effectiveScale());
   }

   /** Where the saved Corner and offsets put a card of this size, kept on screen. */
   private void anchoredPosition(HudContext hud, float scaledW, float scaledH) {
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
      // A screen made smaller since the widget was placed must not push it out of sight.
      this.lastX = Mth.clamp(x, 0.0F, Math.max(0.0F, hud.width - scaledW));
      this.lastY = Mth.clamp(y, 0.0F, Math.max(0.0F, hud.height - scaledH));
   }

   /**
    * Lays out every enabled widget for this frame: placed widgets where the user put them, the others stacked by
    * {@code flow}, which the caller has begun for this screen size and filled with the vanilla HUD's areas.
    */
   public static void arrange(HudContext hud, HudWidget[] widgets, HudFlow flow) {
      arrange(hud, widgets, flow, 0.0F);
   }

   /**
    * As {@link #arrange(HudContext, HudWidget[], HudFlow)}. {@code pixelsPerUnit} is how many screen pixels one HUD
    * pixel covers (GUI scale times HUD scale); when the automatic widgets don't all fit, they are laid out again a
    * step smaller ({@link HudFlow#fitSteps}) instead of being drawn on top of each other.
    */
   public static void arrange(HudContext hud, HudWidget[] widgets, HudFlow flow, float pixelsPerUnit) {
      frame++;
      fitScale = 1.0F;
      for (HudWidget widget : widgets) {
         if (!widget.isEnabled()) {
            continue;
         }

         widget.measure(hud);
         if (widget.measuredVisible && widget.isPlaced()) {
            widget.anchoredPosition(hud, widget.scaledW(), widget.scaledH());
            flow.reserve(Math.round(widget.lastX), Math.round(widget.lastY), widget.scaledW(), widget.scaledH());
         }
      }

      int fixed = flow.reservedCount();
      float[] steps = pixelsPerUnit > 0.0F ? HudFlow.fitSteps(pixelsPerUnit) : FULL_SIZE_ONLY;
      for (int step = 0; step < steps.length; step++) {
         fitScale = steps[step];
         flow.rewind(fixed);
         for (HudWidget widget : widgets) {
            widget.flowSlot = -1;
            if (widget.isEnabled() && widget.measuredFrame == frame && widget.measuredVisible && !widget.isPlaced()) {
               widget.flowSlot = flow.add(HudFlow.anchor(widget.corner.get()), widget.flowOrder, widget.scaledW(), widget.scaledH(), widget.flowInset());
            }
         }

         flow.solve();
         boolean allFit = true;
         for (HudWidget widget : widgets) {
            if (widget.flowSlot >= 0 && !flow.fitted(widget.flowSlot)) {
               allFit = false;
               break;
            }
         }

         if (allFit) {
            break;
         }
      }

      for (HudWidget widget : widgets) {
         if (widget.flowSlot >= 0 && widget.measuredFrame == frame) {
            widget.lastX = flow.x(widget.flowSlot);
            widget.lastY = flow.y(widget.flowSlot);
         }
      }
   }

   private static final float[] FULL_SIZE_ONLY = {1.0F};

   @Override
   public void onRenderHud(HudContext hud) {
      if (this.measuredFrame != frame) {
         // Drawn without a layout pass: fall back to the saved anchor.
         this.measure(hud);
         if (this.measuredVisible) {
            this.anchoredPosition(hud, this.scaledW(), this.scaledH());
         }
      }

      // Measured once per frame; the next frame measures again.
      this.measuredFrame = -1;
      if (!this.measuredVisible) {
         this.lastW = 0.0F;
         this.lastH = 0.0F;
         return;
      }

      int w = this.measuredW;
      int h = this.measuredH;
      float s = this.effectiveScale();
      this.lastW = w * s;
      this.lastH = h * s;
      this.backdrop = this.background.get() ? hud.backdrop : HudStyle.NONE;
      boolean shadow = hud.shadow;
      hud.shadow = HudStyle.shadow(this.backdrop, shadow);
      hud.graphics.pose().pushMatrix();
      // Whole pixels only: a card at a fractional position blurs its text and edges.
      hud.graphics.pose().translate(Math.round(this.lastX), Math.round(this.lastY));
      hud.graphics.pose().scale(s, s);
      if (this.measuredPreview) {
         HudStyle.card(hud.graphics, 0, 0, w, h, HudStyle.SOFT, hud.surface, 0.8F);
         hud.graphics.text(hud.font, this.getName(), HudStyle.PAD_X, (h - 8) / 2, Glass.TEXT_DIM, false);
      } else {
         if (this.hasBackground()) {
            Glass.hudCard(hud.graphics, 0, 0, w, h, this.backdrop, hud.surface);
         }

         this.paint(hud, w, h);
      }

      hud.graphics.pose().popMatrix();
      hud.shadow = shadow;
   }
}
