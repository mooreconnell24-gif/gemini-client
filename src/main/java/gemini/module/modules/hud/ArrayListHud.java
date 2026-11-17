package gemini.module.modules.hud;

import gemini.Gemini;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.Glass;
import gemini.ui.HudContext;
import gemini.ui.HudStyle;
import gemini.ui.clickgui.Menus;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The list of running modules: one tight strip per module against the screen edge, widest first, with a thin
 * coloured bar on the edge side and the theme gradient running down the list.
 *
 * <p>Rows slide in from (and back out to) the screen edge the widget is anchored to while their height opens and
 * collapses, so the rows below glide instead of jumping. All of that state lives in an {@link ArrayListLayout},
 * which is polled 20 times a second and only re-measures and re-sorts when the set of running modules or one of
 * their {@code info()} texts actually changed; drawing a frame allocates nothing.
 *
 * <p><b>Cost.</b> The list never grows past the screen: only as many rows as fit are laid out and drawn (the
 * narrowest are the ones left off), and rows outside the screen are skipped. Without that limit a long list (every
 * module on, as the self-test does) was two hundred text rows a frame, most of them below the screen, and because it
 * did not fit the automatic layout shrank it, which sent every one of those rows down the font's slower
 * scaled-drawing path: about 100 ms of CPU per second, where a normal list costs well under one.
 */
public final class ArrayListHud extends HudWidget {
   private static final int PAD = 4;
   private static final int BAR = 2;
   private static final int ROW = 11;
   private static final long POLL_MS = 50L;
   private static final String PLACEHOLDER = "ArrayList";
   private static final FontWeight NAME = FontWeight.MEDIUM;
   private static final int SOFT_ALPHA = 150;
   private static final int SOLID_ALPHA = 224;
   private final ModeSetting sort = this.addSetting(
      new ModeSetting("Sort", "Order of the list: widest first, A to Z, or grouped by category", "Width", "Width", "Alphabetical", "Category")
   );
   private final ModeSetting colorMode = this.addSetting(
      new ModeSetting(
         "Color Mode",
         "How the rows are coloured: the theme's two colours blended down the list, the theme accent alone, each module's category colour, or a rainbow",
         "Theme Gradient",
         "Theme Gradient",
         "Static Accent",
         "Category",
         "Rainbow"
      )
   );
   private final BooleanSetting wave = this.addSetting(
      new BooleanSetting("Wave", "Slowly move the gradient along the list", false).showIf(() -> this.colorMode.is("Theme Gradient"))
   );
   private final ModeSetting rowBackdrop = this.addSetting(
      new ModeSetting("Backdrop", "Strip behind each row: None shows only the text with a shadow, Soft is a light tint, Solid a dark strip", "Soft", "None", "Soft", "Solid")
   );
   private final ModeSetting bar = this.addSetting(
      new ModeSetting("Bar", "Thin coloured bar beside the rows; Screen Edge puts it on the side the list is anchored to", "Screen Edge", "Screen Edge", "Left", "Right", "None")
   );
   private final BooleanSetting suffix = this.addSetting(new BooleanSetting("Suffix", "Show a module's mode or status after its name, in dim text", true));
   private final BooleanSetting lowercase = this.addSetting(new BooleanSetting("Lowercase", "Write names and suffixes in lower case", false));
   private final NumberSetting animationSpeed = this.addSetting(
      new NumberSetting("Animation Speed", "How quickly rows slide in and out", 14.0, 4.0, 30.0, 1.0)
   );
   private final ArrayListLayout<Module> layout = new ArrayListLayout<>(
      text -> UiFont.width(text, FontSize.BODY, NAME),
      text -> UiFont.width(text),
      new ArrayListLayout.Decorator<>() {
         @Override
         public String label(Module module, String raw) {
            return ArrayListHud.this.lowercase.get() ? raw.toLowerCase(Locale.ROOT) : raw;
         }

         @Override
         public String info(Module module, String raw) {
            return ArrayListHud.this.lowercase.get() ? raw.toLowerCase(Locale.ROOT) : raw;
         }
      }
   );
   private final ArrayListLayout.Glide glide = (current, target, delta) -> Glass.approach(current, target, this.animationSpeed.floatValue(), delta);
   private long lastPollMs;
   private long lastFrameMs;
   private int decorationKey = -1;
   /** Rows that fit on the screen this frame; the rest of the list is not drawn. */
   private int shownRows;

   public ArrayListHud() {
      super("ArrayList", "Shows a list of the modules that are turned on.", "Top Right", 0, 3);
      // On by default, like the built-in list it replaced.
      this.loadState(true, this.getKeybind());
   }

   /** The list sits flush with the screen edge so its bar reads as part of the edge. */
   @Override
   protected int flowInset() {
      return 0;
   }

   @Override
   protected boolean hasBackground() {
      // The rows draw their own strips; the widget card would box the whole list in.
      return false;
   }

   @Override
   protected boolean hasBackgroundSwitch() {
      return false;
   }

   @Override
   public void onDisable() {
      this.layout.clear();
   }

   /** Refreshes the layout: polls the modules at most 20 times a second and advances the animations every frame. */
   private void update(HudContext hud) {
      long now = Util.getMillis();
      // a font or GUI scale change makes the measured row widths stale
      int key = (this.lowercase.get() ? 1 : 0) | (this.suffix.get() ? 2 : 0) | UiFont.generation() << 2;
      if (key != this.decorationKey) {
         this.decorationKey = key;
         this.layout.invalidate();
         this.lastPollMs = 0L;
      }

      this.layout.setSortMode(this.sort.is("Alphabetical") ? ArrayListLayout.SORT_ALPHABETICAL : (this.sort.is("Category") ? ArrayListLayout.SORT_GROUP : ArrayListLayout.SORT_WIDTH));
      if (now - this.lastPollMs >= POLL_MS || now < this.lastPollMs) {
         this.lastPollMs = now;
         boolean info = this.suffix.get();
         List<Module> modules = Gemini.getInstance().modules().getModules();
         this.layout.beginPoll();
         for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            if (module.isEnabled() && module.isListed()) {
               this.layout.offer(module, module.getName(), 0, info ? module.info() : null, module.getCategory().ordinal());
            }
         }

         this.layout.endPoll();
      }

      float delta = this.lastFrameMs == 0L ? 0.0F : Mth.clamp((now - this.lastFrameMs) / 1000.0F, 0.0F, 0.1F);
      this.lastFrameMs = now;
      this.layout.advance(delta, this.glide);
      this.shownRows = this.layout.fitCount(ROW, Math.max(ROW, hud.height - 2 * ROW));
   }

   private int barWidth() {
      return this.bar.is("None") ? 0 : BAR;
   }

   @Override
   protected int widgetWidth(HudContext hud) {
      this.update(hud);
      if (this.layout.size() == 0) {
         return Menus.isMenu(hud.client.gui.screen()) ? UiFont.width(PLACEHOLDER) + 2 * PAD : 0;
      }

      return this.layout.maxWidth() + 2 * PAD + this.barWidth();
   }

   @Override
   protected int widgetHeight(HudContext hud) {
      if (this.layout.size() == 0) {
         return Menus.isMenu(hud.client.gui.screen()) ? ROW : 0;
      }

      return Math.max(1, Mth.ceil(this.layout.height(ROW, this.shownRows)));
   }

   @Override
   protected void paint(HudContext hud, int w, int h) {
      GuiGraphicsExtractor g = hud.graphics;
      List<ArrayListLayout.Row<Module>> rows = this.layout.rows();
      int total = Math.min(this.shownRows, rows.size());
      int backdrop = HudStyle.backdrop(this.rowBackdrop.get());
      boolean shadow = backdrop == HudStyle.NONE || hud.shadow;
      if (total == 0) {
         UiFont.draw(g, PLACEHOLDER, PAD, UiFont.centerY(0, h), hud.textDim, shadow);
         return;
      }

      String anchor = this.anchor();
      int horizontal = anchor.endsWith("Right") || anchor.startsWith("Right") ? 2 : (anchor.endsWith("Center") && !anchor.startsWith("Left") ? 1 : 0);
      boolean bottom = anchor.startsWith("Bottom");
      boolean barRight = this.bar.is("Right") || this.bar.is("Screen Edge") && horizontal == 2;
      int barW = horizontal == 1 && this.bar.is("Screen Edge") ? 0 : this.barWidth();
      int stripAlpha = backdrop == HudStyle.SOLID ? SOLID_ALPHA : (backdrop == HudStyle.SOFT ? SOFT_ALPHA : 0);
      int mode = this.colorMode.modes().indexOf(this.colorMode.get());
      float wavePhase = mode == 0 && this.wave.get() ? (Util.getMillis() % 6000L) / 6000.0F + 0.0001F : 0.0F;
      // Rows above or below the screen are not drawn. The pose scale is the drawn height over the laid-out height.
      float scale = h > 0 && this.lastScreenH() > 0.0F ? this.lastScreenH() / h : 1.0F;
      float visibleTop = -this.lastScreenY() / scale;
      float visibleBottom = (hud.height - this.lastScreenY()) / scale;
      int capOffset = (ROW - UiFont.capHeight(FontSize.BODY)) / 2;
      int suffixColor = HudStyle.label(backdrop);
      float cursor = 0.0F;
      int color = this.rowColor(mode, rows.get(bottom ? total - 1 : 0), 0, total, hud, wavePhase);
      // A bottom-anchored list is drawn upside down so the widest row still sits against the screen edge.
      for (int i = 0; i < total; i++) {
         ArrayListLayout.Row<Module> row = rows.get(bottom ? total - 1 - i : i);
         int nextColor = i + 1 < total ? this.rowColor(mode, rows.get(bottom ? total - 2 - i : i + 1), i + 1, total, hud, wavePhase) : color;
         float anim = row.anim;
         int top = Math.round(cursor);
         cursor += ROW * anim;
         int bottomY = Math.round(cursor);
         if (top > visibleBottom) {
            break;
         }

         if (bottomY > top && bottomY >= visibleTop) {
            int rowW = row.width + 2 * PAD + barW;
            int slide = Math.round((1.0F - anim) * (rowW + 2));
            int left = horizontal == 2 ? w - rowW + slide : (horizontal == 1 ? (w - rowW) / 2 : -slide);
            int right = left + rowW;
            if (stripAlpha > 0) {
               g.fill(barRight || barW == 0 ? left : left + barW, top, barRight && barW > 0 ? right - barW : right, bottomY, Glass.withAlpha(hud.surface, Math.round(stripAlpha * anim)));
            }

            if (barW > 0) {
               int barLeft = barRight ? right - barW : left;
               int alpha = Math.round(255.0F * anim);
               Glass.gradientV(g, barLeft, top, barLeft + barW, bottomY, Glass.withAlpha(color, alpha), Glass.withAlpha(nextColor, alpha));
            }

            if (anim > 0.05F) {
               int textX = left + PAD + (barRight ? 0 : barW);
               int textY = top + Math.round(capOffset * anim);
               int textAlpha = Math.max(8, Math.round(255.0F * anim));
               UiFont.draw(g, row.label, textX, textY, Glass.withAlpha(color, textAlpha), FontSize.BODY, NAME, shadow);
               if (row.info != null) {
                  UiFont.draw(g, row.info, textX + row.labelWidth + ArrayListLayout.INFO_GAP, textY, Glass.withAlpha(suffixColor, textAlpha), shadow);
               }
            }
         }

         color = nextColor;
      }
   }

   /** Colour of the row drawn at {@code index} (0 = top) out of {@code total}. */
   private int rowColor(int mode, ArrayListLayout.Row<Module> row, int index, int total, HudContext hud, float wavePhase) {
      return switch (mode) {
         case 0 -> HudStyle.gradientAt(hud.accent, hud.accent2, index, total, wavePhase);
         case 2 -> row.key.getCategory().accent() | 0xFF000000;
         case 3 -> Glass.rainbow(5000L, index * 0.035F, 0.55F, 1.0F);
         default -> hud.accent | 0xFF000000;
      };
   }
}
