package gemini.module.modules.hud;

import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.clickgui.Menus;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The animated list of running modules, as a movable HUD widget.
 *
 * <p>Rows slide in from (and back out to) the screen edge the widget is anchored to while their height opens and
 * collapses, so the rows below glide instead of jumping. All of that state lives in an {@link ArrayListLayout},
 * which is polled 20 times a second and only re-measures and re-sorts when the set of running modules or one of
 * their {@code info()} texts actually changed; drawing a frame allocates nothing.
 *
 * <p>"Blur" is a frosted tint, not a real blur of the world: Minecraft 26.3 allows one full-screen blur per frame
 * ({@code GuiGraphicsExtractor.blurBeforeThisStratum}) and every vanilla menu already uses it, so asking for it
 * from the HUD would crash the game the moment a menu opened.
 */
public final class ArrayListHud extends HudWidget {
   private static final int PAD = 4;
   private static final int BAR = 2;
   private static final long POLL_MS = 50L;
   private static final String PLACEHOLDER = "ArrayList";
   private final ModeSetting sort = this.addSetting(
      new ModeSetting("Sort", "Order of the list: widest first, A to Z, or grouped by category", "Width", "Width", "Alphabetical", "Category")
   );
   private final ModeSetting colors = this.addSetting(
      new ModeSetting(
         "Colors",
         "How the rows are coloured: the theme accent, a gradient between the two theme colours, per category, or a moving colour",
         "Accent",
         "Theme Gradient",
         "Accent",
         "Category",
         "Rainbow",
         "Pink-Blue Wave",
         "Wave",
         "Static Gradient"
      )
   );
   private final ColorSetting gradientStart = this.addSetting(
      new ColorSetting("Gradient Start", "Colour of the first row", 195).showIf(() -> this.colors.is("Static Gradient"))
   );
   private final ColorSetting gradientEnd = this.addSetting(
      new ColorSetting("Gradient End", "Colour of the last row", 300).showIf(() -> this.colors.is("Static Gradient"))
   );
   private final NumberSetting colorSpeed = this.addSetting(
      new NumberSetting("Color Speed", "How fast cycling colours change", 1.0, 0.1, 4.0, 0.1)
         .showIf(this::movingColors)
   );
   private final NumberSetting colorSpread = this.addSetting(
      new NumberSetting("Color Spread", "How far the colour shifts from one row to the next", 0.05, 0.0, 0.25, 0.01)
         .showIf(this::movingColors)
   );
   private final ModeSetting sideBar = this.addSetting(
      new ModeSetting("Side Bar", "Coloured bar beside the rows; Auto puts it on the screen edge the list is anchored to", "Auto", "Auto", "Left", "Right", "None")
   );
   private final NumberSetting opacity = this.addSetting(new NumberSetting("Opacity", "Opacity of the background behind the rows", 0.55, 0.05, 1.0, 0.05));
   private final BooleanSetting blur = this.addSetting(
      new BooleanSetting("Blur", "Use a denser background with a light sheen behind the rows (the world behind is not actually blurred)", false)
   );
   private final BooleanSetting outline = this.addSetting(new BooleanSetting("Outline", "Thin coloured line along the inner edge of the list", false));
   private final BooleanSetting showInfo = this.addSetting(new BooleanSetting("Show Info", "Show a module's mode or status after its name", true));
   private final ModeSetting tagStyle = this.addSetting(
      new ModeSetting("Tag Style", "How the info after the name is written", "Plain", "Plain", "Brackets [Info]", "Dot · Info", "Angle <Info>").showIf(this.showInfo::get)
   );
   private final BooleanSetting showKeys = this.addSetting(new BooleanSetting("Show Keys", "Show each module's key bind after its name", false));
   private final BooleanSetting lowercase = this.addSetting(new BooleanSetting("Lowercase", "Write names and info in lower case", false));
   private final NumberSetting lineSpacing = this.addSetting(new NumberSetting("Line Spacing", "Extra pixels between rows", 1.0, 0.0, 6.0, 1.0));
   private final BooleanSetting textShadow = this.addSetting(new BooleanSetting("Text Shadow", "Draw the text with a shadow", true));
   private final BooleanSetting animations = this.addSetting(new BooleanSetting("Animations", "Slide rows in and out when modules are turned on or off", true));
   private final NumberSetting animationSpeed = this.addSetting(
      new NumberSetting("Animation Speed", "How quickly rows slide", 14.0, 4.0, 30.0, 1.0).showIf(this.animations::get)
   );
   private final ArrayListLayout<Module> layout = new ArrayListLayout<>(text -> Minecraft.getInstance().font.width(text), new ArrayListLayout.Decorator<>() {
      @Override
      public String label(Module module, String raw) {
         String base = ArrayListHud.this.showKeys.get() && module.getKeybind() > 0 ? raw + " [" + GeminiKeys.displayName(module.getKeybind()) + "]" : raw;
         return ArrayListHud.this.lowercase.get() ? base.toLowerCase(Locale.ROOT) : base;
      }

      @Override
      public String info(Module module, String raw) {
         String text = ArrayListHud.this.lowercase.get() ? raw.toLowerCase(Locale.ROOT) : raw;
         return switch (ArrayListHud.this.tagStyle.get()) {
            case "Brackets [Info]" -> "[" + text + "]";
            case "Dot · Info" -> "· " + text;
            case "Angle <Info>" -> "<" + text + ">";
            default -> text;
         };
      }
   });
   private final ArrayListLayout.Glide glide = (current, target, delta) -> Glass.approach(current, target, this.animationSpeed.floatValue(), delta);
   private long lastPollMs;
   private long lastFrameMs;
   private int decorationKey = -1;

   public ArrayListHud() {
      super("ArrayList", "Shows a list of the modules that are turned on.", "Top Right", 0, 3);
      // On by default, like the built-in list it replaced.
      this.loadState(true, this.getKeybind());
   }

   /** The list sits nearly flush with the screen edge, as it always has. */
   @Override
   protected int flowInset() {
      return 2;
   }

   @Override
   protected boolean hasBackground() {
      // The rows draw their own tint; the widget card would box the whole list in.
      return false;
   }

   /** The colour modes that change over time, which the speed and spread settings apply to. */
   private boolean movingColors() {
      return this.colors.is("Rainbow") || this.colors.is("Pink-Blue Wave") || this.colors.is("Wave");
   }

   private boolean rowBackground() {
      return super.hasBackground();
   }

   @Override
   public void onDisable() {
      this.layout.clear();
   }

   /** Refreshes the layout: polls the modules at most 20 times a second and advances the animations every frame. */
   private void update() {
      long now = Util.getMillis();
      int key = (this.showKeys.get() ? 1 : 0) | (this.lowercase.get() ? 2 : 0) | (this.showInfo.get() ? 4 : 0) | this.tagStyle.modes().indexOf(this.tagStyle.get()) << 3;
      if (key != this.decorationKey) {
         this.decorationKey = key;
         this.layout.invalidate();
         this.lastPollMs = 0L;
      }

      this.layout.setSortMode(this.sort.is("Alphabetical") ? ArrayListLayout.SORT_ALPHABETICAL : (this.sort.is("Category") ? ArrayListLayout.SORT_GROUP : ArrayListLayout.SORT_WIDTH));
      if (now - this.lastPollMs >= POLL_MS || now < this.lastPollMs) {
         this.lastPollMs = now;
         boolean keys = this.showKeys.get();
         boolean info = this.showInfo.get();
         List<Module> modules = Gemini.getInstance().modules().getModules();
         this.layout.beginPoll();
         for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled() && module.isListed()) {
               this.layout.offer(module, module.getName(), keys ? module.getKeybind() : -1, info ? module.info() : null, module.getCategory().ordinal());
            }
         }

         this.layout.endPoll();
      }

      float delta = this.lastFrameMs == 0L ? 0.0F : Mth.clamp((now - this.lastFrameMs) / 1000.0F, 0.0F, 0.1F);
      this.lastFrameMs = now;
      this.layout.advance(delta, this.animations.get() ? this.glide : null);
   }

   private int rowHeight() {
      return 10 + this.lineSpacing.intValue();
   }

   private int barWidth() {
      return this.sideBar.is("None") ? 0 : BAR;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.update();
      if (this.layout.size() == 0) {
         return Menus.isMenu(hud.client.gui.screen()) ? hud.font.width(PLACEHOLDER) + 2 * PAD : 0;
      }

      return this.layout.maxWidth() + 2 * PAD + this.barWidth();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      if (this.layout.size() == 0) {
         return Menus.isMenu(hud.client.gui.screen()) ? this.rowHeight() : 0;
      }

      return Math.max(1, Mth.ceil(this.layout.height(this.rowHeight())));
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      List<ArrayListLayout.Row<Module>> rows = this.layout.rows();
      int total = rows.size();
      if (total == 0) {
         g.text(hud.font, PLACEHOLDER, PAD, 1, Glass.TEXT_DIM, this.textShadow.get());
         return;
      }

      String anchor = this.anchor();
      int horizontal = anchor.endsWith("Right") || anchor.startsWith("Right") ? 2 : (anchor.endsWith("Center") && !anchor.startsWith("Left") ? 1 : 0);
      boolean bottom = anchor.startsWith("Bottom");
      boolean barRight = this.sideBar.is("Right") || this.sideBar.is("Auto") && horizontal == 2;
      int barW = this.barWidth();
      int rowH = this.rowHeight();
      // The HUD-wide "None" backdrop also clears the row tint.
      boolean background = this.rowBackground() && this.backdrop() != gemini.ui.HudStyle.NONE;
      boolean frosted = this.blur.get();
      float tint = frosted ? Math.max(0.82F, this.opacity.floatValue()) : this.opacity.floatValue();
      boolean contour = this.outline.get();
      boolean shadow = this.textShadow.get();
      int mode = this.colors.modes().indexOf(this.colors.get());
      long now = Util.getMillis();
      float cursor = 0.0F;
      int previousEdge = Integer.MIN_VALUE;
      int color = this.rowColor(mode, rows.get(bottom ? total - 1 : 0), 0, total, hud, now);
      // A bottom-anchored list is drawn upside down so the widest row still sits against the screen edge.
      for (int i = 0; i < total; i++) {
         ArrayListLayout.Row<Module> row = rows.get(bottom ? total - 1 - i : i);
         int nextColor = i + 1 < total ? this.rowColor(mode, rows.get(bottom ? total - 2 - i : i + 1), i + 1, total, hud, now) : color;
         float anim = row.anim;
         int rowW = row.width + 2 * PAD + barW;
         int slide = Math.round((1.0F - anim) * (rowW + 2));
         int left = horizontal == 2 ? w - rowW + slide : (horizontal == 1 ? (w - rowW) / 2 : -slide);
         int right = left + rowW;
         int top = Math.round(cursor);
         cursor += rowH * anim;
         int bottomY = Math.round(cursor);
         if (bottomY > top) {
            if (background) {
               g.fill(left, top, right, bottomY, Glass.withAlpha(hud.surface, Math.round(255.0F * tint * anim)));
               if (frosted) {
                  g.fill(left, top, right, bottomY, Glass.withAlpha(0xFFFFFF, Math.round(16.0F * anim)));
                  g.fill(left, top, right, top + 1, Glass.withAlpha(0xFFFFFF, Math.round(22.0F * anim)));
               }
            }

            int lineAlpha = Math.round(210.0F * anim);
            if (contour) {
               int lineColor = Glass.withAlpha(color, lineAlpha);
               if (horizontal == 1) {
                  g.fill(left - 1, top, left, bottomY, lineColor);
                  g.fill(right, top, right + 1, bottomY, lineColor);
               } else {
                  int edge = horizontal == 2 ? left - 1 : right;
                  g.fill(edge, top, edge + 1, bottomY, lineColor);
                  if (previousEdge == Integer.MIN_VALUE) {
                     g.fill(Math.min(edge, left), top - 1, Math.max(edge + 1, right), top, lineColor);
                  } else if (previousEdge != edge) {
                     g.fill(Math.min(previousEdge, edge), top - 1, Math.max(previousEdge, edge) + 1, top, lineColor);
                  }

                  if (i == total - 1) {
                     g.fill(Math.min(edge, left), bottomY, Math.max(edge + 1, right), bottomY + 1, lineColor);
                  }

                  previousEdge = edge;
               }
            }

            if (barW > 0) {
               int barLeft = barRight ? right - barW : left;
               int middle = (top + bottomY) / 2;
               g.fill(barLeft, top, barLeft + barW, middle, Glass.withAlpha(color, Math.round(255.0F * anim)));
               g.fill(barLeft, middle, barLeft + barW, bottomY, Glass.withAlpha(Glass.lerp(color, nextColor, 0.5F), Math.round(255.0F * anim)));
            }

            if (anim > 0.05F) {
               int textX = left + PAD + (barRight ? 0 : barW);
               int textY = top + Math.round((rowH - 8) / 2.0F * anim);
               int textAlpha = Math.max(8, Math.round(255.0F * anim));
               // Theme and category colours are tuned for bars, not text: names are lifted toward white so they stay
               // readable over a dark scene, while the side bar keeps the full colour.
               int textColor = mode <= 2 ? Glass.lerp(color, 0xFFFFFFFF, 0.3F) : color;
               g.text(hud.font, row.label, textX, textY, Glass.withAlpha(textColor, textAlpha), shadow);
               if (row.info != null) {
                  g.text(hud.font, row.info, textX + row.labelWidth + ArrayListLayout.INFO_GAP, textY, Glass.withAlpha(Glass.TEXT_DIM, textAlpha), shadow);
               }
            }
         }

         color = nextColor;
      }
   }

   /** Colour of the row drawn at {@code index} (0 = top) out of {@code total}. */
   private int rowColor(int mode, ArrayListLayout.Row<Module> row, int index, int total, HudContext hud, long now) {
      float along = total <= 1 ? 0.0F : (float)index / (total - 1);
      long period = Math.max(250L, Math.round(4000.0 / this.colorSpeed.get()));
      float offset = index * this.colorSpread.floatValue();
      return switch (mode) {
         case 0 -> Glass.lerp(hud.accent, hud.accent2, along) | 0xFF000000;
         case 1 -> hud.accent | 0xFF000000;
         case 2 -> row.key.getCategory().accent() | 0xFF000000;
         case 4 -> Glass.astolfo(period, offset);
         case 5 -> Glass.lerp(hud.accent, hud.accent2, 0.5F + 0.5F * Mth.sin(((now % period) / (float)period - offset) * Mth.TWO_PI)) | 0xFF000000;
         case 6 -> Glass.lerp(this.gradientStart.argb(), this.gradientEnd.argb(), along) | 0xFF000000;
         default -> Glass.rainbow(period, offset, 0.65F, 1.0F);
      };
   }
}
