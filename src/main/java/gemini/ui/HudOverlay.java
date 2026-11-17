package gemini.ui;

import gemini.Gemini;
import gemini.module.HudFlow;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.render.EspModule;
import gemini.module.modules.render.HudModule;
import gemini.module.modules.render.StorageEspModule;
import gemini.module.modules.world.WaypointsModule;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.util.BaritoneBridge;
import gemini.util.SingleplayerGate;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

/**
 * Gemini's in-game overlay: the watermark, the small built-in text panels (waypoints, ESP tags, storage), the
 * module HUD widgets and the notifications. Every colour comes from the active {@link Theme}.
 *
 * <p>Everything that only changes per tick (the watermark's stats, the panel texts) is rebuilt once a tick into
 * reused lists; a frame only draws. The {@link HudContext} handed to the widgets is one instance refilled per frame.
 * The module list is the {@code ArrayList} HUD widget.
 */
public final class HudOverlay {
   private static final int LINE = 11;
   /** Height of a two-line notification (title and body) and of a one-line one (a module switched on or off). */
   private static final int TOAST_H = 32;
   private static final int TOAST_H_LINE = 22;
   private static final int TOAST_GAP = 4;
   private static final long TOAST_IN_MS = 260L;
   private static final long TOAST_OUT_MS = 220L;
   private static final int MAX_TOASTS = 5;
   /** Narrowest and widest a notification gets; longer text is ellipsized. */
   private static final int TOAST_MIN_W = 96;
   private static final int TOAST_MAX_W = 220;
   /** Diameter of the icon's round badge, and the left edge of the text past it. */
   private static final int TOAST_ICON = 14;
   private static final int TOAST_TEXT_X = 8 + TOAST_ICON + 7;
   private static final int TOAST_PAD_RIGHT = 10;
   /** Distance from the screen edge. */
   private static final int TOAST_MARGIN = 6;
   /** Alpha of a notification's card: they appear over anything, so they are always a solid card. */
   private static final int TOAST_ALPHA = 236;
   private static final String NAME = "Gemini";
   private static final String VERSION = "dev".equals(Gemini.VERSION) ? "dev" : "v" + Gemini.VERSION;
   private static final ArrayDeque<HudOverlay.Toast> TOASTS = new ArrayDeque<>();
   private final ModuleManager modules;
   private long lastFrameMs = Util.getMillis();
   private int surface = -15987178;
   /** The HUD-wide backdrop look ({@link HudStyle}) for this frame. */
   private int backdrop = HudStyle.SOFT;
   /** One context, refilled every frame instead of allocated. */
   private HudContext context;
   /** The watermark's texts only change once a tick, so they are rebuilt per tick instead of per frame. */
   private String metaLineCache;
   private int metaLineWidth;
   private int metaLineTick = -1;
   private String metaLineStyle;
   private int metaGeneration = -1;
   /** Right edge of the watermark as last drawn (before the Watermark Size scale), for the automatic layout. */
   private int watermarkRight;
   private final List<String> metaParts = new ArrayList<>();
   /** Per-tick caches of the text panels so a frame only draws. */
   private int panelTick = Integer.MIN_VALUE;
   private final List<String> waypointLines = new ArrayList<>();
   private final List<Entity> espShown = new ArrayList<>();
   private final List<String> espLines = new ArrayList<>();
   private final IntList espColors = new IntList();
   private final List<String> storageLines = new ArrayList<>();
   /** Automatic layout of the widgets the user has not moved; refilled every frame. */
   private final HudFlow flow = new HudFlow();
   private HudWidget[] widgets;
   /** Scratch for {@link #toastStart}: the widgets in the notification corner, as distances from the screen edge. */
   private float[] toastRects = new float[16];
   /** Sidebar scoreboard size in GUI pixels, worked out once a tick (0 when none is shown). */
   private int sidebarW;
   private int sidebarRows;

   public HudOverlay(ModuleManager modules) {
      this.modules = modules;
   }

   /**
    * What a notification is about; picks its icon and the colour of the icon and the timer bar. {@code badge} and
    * {@code glyph} are a short name and a one-character symbol for the kind, for callers that describe one in text.
    */
   public enum Severity {
      INFO("INFO", Glass.INFO, "i", HudShapes.INFO),
      SUCCESS("OK", Glass.OK, "✔", HudShapes.CHECK),
      WARNING("WARN", Glass.WARN, "!", HudShapes.WARNING),
      ERROR("ALERT", Glass.ERROR, "✖", HudShapes.CROSS),
      ENABLED("ON", Glass.OK, "+", HudShapes.CHECK),
      DISABLED("OFF", Glass.TEXT_FAINT, "-", HudShapes.CROSS);

      public final String badge;
      /** The colour outside any theme; drawing uses {@link #color(Theme.Palette)}. */
      public final int color;
      public final String glyph;
      /** The {@link HudShapes} icon. */
      public final int icon;

      Severity(String badge, int color, String glyph, int icon) {
         this.badge = badge;
         this.color = color;
         this.glyph = glyph;
         this.icon = icon;
      }

      /**
       * The colour in a theme: the accent for plain information and a module switched on, the theme's status
       * colours for success, warnings and errors, and faint text for a module switched off.
       */
      public int color(Theme.Palette theme) {
         return switch (this) {
            case INFO, ENABLED -> theme.accent;
            case SUCCESS -> theme.ok;
            case WARNING -> theme.warn;
            case ERROR -> theme.error;
            case DISABLED -> theme.textDim;
         } | 0xFF000000;
      }
   }

   /**
    * Module on/off notification: one short line, the name and "on" / "off" ({@code body} is not shown). Returns
    * whether notifications are showing, so the caller can fall back to a chat line when they are switched off
    * instead of saying the same thing twice. With notifications on but the HUD module's Module Toggles switch off,
    * nothing is shown and nothing is written to chat either: the user asked for toggles to be quiet.
    */
   public static boolean pushNotification(String title, String body, boolean enabled) {
      if (!toastsShown()) {
         return false;
      }

      Gemini gemini = Gemini.getInstance();
      HudModule hud = gemini == null || gemini.modules() == null ? null : gemini.modules().byClass(HudModule.class).orElse(null);
      if (hud == null || hud.toggleToasts()) {
         toast(title, "", enabled ? Severity.ENABLED : Severity.DISABLED, 1500L);
      }

      return true;
   }

   /** Whether toasts are drawn right now: the HUD module and its Toasts switch are on and Gemini is active. */
   public static boolean toastsShown() {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || gemini.modules() == null || !SingleplayerGate.sandboxMode()) {
         return false;
      }

      HudModule hud = gemini.modules().byClass(HudModule.class).orElse(null);
      return hud == null || hud.isEnabled() && hud.toasts();
   }

   /** Shows a toast for the default time; a toast with the same title replaces the one already showing. */
   public static void toast(String title, String body, Severity severity) {
      toast(title, body, severity, severity == Severity.WARNING || severity == Severity.ERROR ? 4200L : 2800L);
   }

   public static void toast(String title, String body, Severity severity, long durationMs) {
      if (title == null || severity == null) {
         return;
      }

      synchronized (TOASTS) {
         HudOverlay.Toast replaced = null;
         Iterator<HudOverlay.Toast> it = TOASTS.iterator();
         while (it.hasNext()) {
            HudOverlay.Toast old = it.next();
            if (old.title.equals(title)) {
               replaced = old;
               it.remove();
               break;
            }
         }

         if (TOASTS.size() >= MAX_TOASTS) {
            TOASTS.removeFirst();
         }

         HudOverlay.Toast toast = new HudOverlay.Toast(title, body == null ? "" : body, severity, Util.getMillis(), Math.max(600L, durationMs));
         if (replaced != null && Util.getMillis() - replaced.createdAt < replaced.durationMs - TOAST_OUT_MS) {
            // The replacement takes the old card's place instead of sliding in from the edge again.
            toast.stackY = replaced.stackY;
            toast.createdAt -= TOAST_IN_MS;
            toast.durationMs += TOAST_IN_MS;
         }

         TOASTS.addLast(toast);
      }
   }

   /** How far a toast is slid onto the screen (0..1): ease-out on the way in, ease-in on the way out. */
   static float toastSlide(long age, long duration) {
      if (age < 0L) {
         return 0.0F;
      }

      if (age < TOAST_IN_MS) {
         return Glass.easeOutCubic((float)age / TOAST_IN_MS);
      }

      long left = duration - age;
      if (left < TOAST_OUT_MS) {
         float t = 1.0F - Math.max(0.0F, (float)left / TOAST_OUT_MS);
         return 1.0F - t * t * t;
      }

      return 1.0F;
   }

   public void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      Minecraft client = Minecraft.getInstance();
      long now = Util.getMillis();
      float delta = Mth.clamp((now - this.lastFrameMs) / 1000.0F, 0.0F, 0.1F);
      this.lastFrameMs = now;
      if (client.player != null && !client.gui.hud.isHidden() && SingleplayerGate.sandboxMode()) {
         HudModule hud = this.modules.byClass(HudModule.class).orElse(null);
         if (hud == null || hud.isEnabled()) {
            ClickGuiModule style = this.modules.byClass(ClickGuiModule.class).orElse(null);
            int accent = style == null ? -16748289 : style.accentColor();
            int accent2 = style == null ? -16722689 : style.secondaryColor();
            this.surface = style == null ? -15987178 : style.surface(0);
            float scale = hud == null ? 1.0F : hud.scale();
            boolean shadow = hud == null || hud.shadow();
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            int width = Math.round(graphics.guiWidth() / scale);
            int height = Math.round(graphics.guiHeight() / scale);
            int topLeftY = 4;
            this.backdrop = hud == null ? HudStyle.SOFT : hud.backdrop();
            if (hud == null || hud.watermark()) {
               float size = hud == null ? 1.0F : hud.watermarkSize();
               graphics.pose().pushMatrix();
               graphics.pose().scale(size, size);
               topLeftY = Math.round(this.drawWatermark(graphics, client, hud, accent, accent2, shadow) * size);
               graphics.pose().popMatrix();
            }

            int watermarkRight = 0;
            if (hud == null || hud.watermark()) {
               float size = hud == null ? 1.0F : hud.watermarkSize();
               watermarkRight = Mth.ceil(this.watermarkRight * size);
            }

            this.refreshPanels(client);
            this.drawWaypoints(graphics, client, accent, topLeftY, shadow || this.backdrop == HudStyle.NONE);
            this.drawEspTags(graphics, client, width, shadow);
            this.drawStorage(graphics, client, accent, width, height, shadow);
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            HudContext ctx = this.context;
            if (ctx == null) {
               ctx = new HudContext(graphics, client, width, height, accent, accent2, this.surface, shadow, partial);
               this.context = ctx;
            }

            ctx.update(graphics, client, width, height, accent, accent2, this.surface, shadow, partial, delta);
            ctx.backdrop = this.backdrop;
            this.layoutWidgets(ctx, client, scale, watermarkRight, topLeftY);
            this.modules.renderHud(ctx);
            if (hud == null || hud.toasts()) {
               this.drawToasts(graphics, client.font, width, height, delta, hud == null || hud.toastsRight(), hud == null || hud.toastsBottom());
            }

            graphics.pose().popMatrix();
         }
      }
   }

   private void drawToasts(GuiGraphicsExtractor graphics, Font font, int width, int height, float delta, boolean right, boolean bottom) {
      long now = Util.getMillis();
      Theme.Palette theme = Theme.current();
      // Slots are laid out from the anchored edge, newest nearest it. Each card glides toward its slot, so the stack
      // closes up smoothly when a card above or below leaves.
      float slot = TOASTS.isEmpty() ? TOAST_MARGIN : this.toastStart(width, height, right, bottom);
      boolean first = true;
      synchronized (TOASTS) {
         Iterator<HudOverlay.Toast> it = TOASTS.descendingIterator();
         while (it.hasNext()) {
            HudOverlay.Toast toast = it.next();
            long age = now - toast.createdAt;
            if (age >= toast.durationMs) {
               it.remove();
               continue;
            }

            int generation = UiFont.generation();
            if (toast.cardW == 0 || toast.generation != generation) {
               toast.generation = generation;
               measureToast(toast, width);
            }

            float slide = toastSlide(age, toast.durationMs);
            float visible = Mth.clamp(slide, 0.0F, 1.0F);
            if (!first) {
               slot += TOAST_GAP;
            }

            first = false;
            // Slots count from the anchored edge; a bottom stack is mirrored so the newest card sits lowest.
            float y = bottom ? height - slot - toast.height : slot;
            toast.stackY = Float.isNaN(toast.stackY) ? y : Glass.approach(toast.stackY, y, 14.0F, delta);
            int cardW = toast.cardW;
            int travel = cardW + TOAST_MARGIN;
            int x = right ? width - Math.round(travel * slide) : Math.round(travel * slide) - cardW;
            this.drawToast(graphics, theme, toast, x, Math.round(toast.stackY), cardW, visible, timeLeft(age, toast.durationMs));
            // A leaving card hands its slot over as it slides out, so the next one starts moving at once.
            float share = toast.durationMs - age < TOAST_OUT_MS ? visible : 1.0F;
            slot += toast.height * share;
         }
      }
   }

   /**
    * How far from the anchored screen edge the notification stack starts: past any HUD widget sitting in that
    * corner, so a notification never covers one. See {@link #clearOf}.
    */
   private float toastStart(int width, int height, boolean right, boolean bottom) {
      HudWidget[] list = this.widgets;
      if (list == null) {
         return TOAST_MARGIN;
      }

      int n = 0;
      if (this.toastRects.length < list.length * 4) {
         this.toastRects = new float[list.length * 4];
      }

      float columnLeft = right ? width - TOAST_MAX_W - TOAST_MARGIN : 0.0F;
      float columnRight = right ? width : TOAST_MAX_W + TOAST_MARGIN;
      for (HudWidget widget : list) {
         if (widget.isEnabled() && widget.lastScreenW() > 0.0F && widget.lastScreenH() > 0.0F
            && widget.lastScreenX() < columnRight && widget.lastScreenX() + widget.lastScreenW() > columnLeft) {
            // as distances from the anchored edge: near side, far side
            float top = widget.lastScreenY();
            float low = top + widget.lastScreenH();
            this.toastRects[n++] = bottom ? height - low : top;
            this.toastRects[n++] = bottom ? height - top : low;
         }
      }

      return clearOf(this.toastRects, n / 2, TOAST_MARGIN, TOAST_H, TOAST_GAP, height * 0.45F);
   }

   /**
    * The first distance from a screen edge, at or after {@code start}, where a band {@code need} tall is free of the
    * {@code count} spans in {@code spans} (pairs of near and far distance from that edge), keeping {@code gap}
    * after a span it has to pass. If that would be farther than {@code limit} (the corner is full, say with a long
    * module list), the answer is {@code start}: better over a widget for a moment than in the middle of the screen.
    */
   static float clearOf(float[] spans, int count, float start, float need, float gap, float limit) {
      float edge = start;
      boolean moved = true;
      for (int pass = 0; moved && pass <= count; pass++) {
         moved = false;
         for (int i = 0; i < count; i++) {
            float near = spans[i * 2];
            float far = spans[i * 2 + 1];
            if (near < edge + need && far > edge - gap) {
               edge = far + gap;
               moved = true;
            }
         }
      }

      return edge > limit ? start : edge;
   }

   /**
    * How much of a notification's time is left (1 when it appears, 0 when it starts to leave): what its timer bar
    * shows. The bar is empty just as the card begins to slide out, not when it is already gone.
    */
   static float timeLeft(long age, long duration) {
      long shown = duration - TOAST_OUT_MS;
      if (shown <= 0L || age >= shown) {
         return 0.0F;
      }

      return age <= 0L ? 1.0F : 1.0F - (float)age / shown;
   }

   /**
    * Works out a notification's size once: a module toggle is one line (the name and a dim "on" / "off"), anything
    * else is the title and, if there is one, the body under it. Never wider than a comfortable reading width or the
    * screen; longer text is cut with an ellipsis.
    */
   private static void measureToast(HudOverlay.Toast toast, int width) {
      boolean toggle = toast.severity == Severity.ENABLED || toast.severity == Severity.DISABLED;
      int max = Math.min(TOAST_MAX_W, width - 2 * TOAST_MARGIN);
      if (toggle) {
         toast.shownBody = toast.severity == Severity.ENABLED ? "on" : "off";
         int stateW = UiFont.width(toast.shownBody);
         toast.shownTitle = UiFont.ellipsize(toast.title, max - TOAST_TEXT_X - TOAST_PAD_RIGHT - stateW - 8, FontSize.BODY, FontWeight.MEDIUM);
         toast.titleW = UiFont.width(toast.shownTitle, FontSize.BODY, FontWeight.MEDIUM);
         toast.cardW = Math.max(Math.min(TOAST_MIN_W, max), TOAST_TEXT_X + toast.titleW + 8 + stateW + TOAST_PAD_RIGHT);
         toast.height = TOAST_H_LINE;
         toast.oneLine = true;
         return;
      }

      boolean hasBody = !toast.body.isEmpty();
      int natural = Math.max(UiFont.width(toast.title, FontSize.BODY, FontWeight.MEDIUM), hasBody ? UiFont.width(toast.body) : 0) + TOAST_TEXT_X + TOAST_PAD_RIGHT;
      toast.cardW = Math.max(Math.min(TOAST_MIN_W, max), Math.min(natural, max));
      toast.shownTitle = UiFont.ellipsize(toast.title, toast.cardW - TOAST_TEXT_X - TOAST_PAD_RIGHT, FontSize.BODY, FontWeight.MEDIUM);
      toast.titleW = UiFont.width(toast.shownTitle, FontSize.BODY, FontWeight.MEDIUM);
      toast.shownBody = hasBody ? UiFont.ellipsize(toast.body, toast.cardW - TOAST_TEXT_X - TOAST_PAD_RIGHT) : "";
      toast.height = hasBody ? TOAST_H : TOAST_H_LINE;
      toast.oneLine = !hasBody;
   }

   /**
    * One notification: a solid card with a soft shadow, a small drawn icon in a round badge of the severity colour,
    * the title (medium weight) with a dim one-line body under it, and a thin bar along the bottom that empties as
    * its time runs out.
    */
   private void drawToast(GuiGraphicsExtractor g, Theme.Palette theme, HudOverlay.Toast toast, int x, int y, int cardW, float visible, float timeLeft) {
      int status = toast.severity.color(theme);
      int h = toast.height;
      Glass.shadow(g, x, y, x + cardW, y + h, HudStyle.RADIUS, 8, 2, 0.32F * visible);
      Glass.roundRect(g, x, y, x + cardW, y + h, HudStyle.RADIUS, Glass.withAlpha(theme.surface(1), Math.round(TOAST_ALPHA * visible)));
      int iconX = x + 8;
      int iconY = y + (h - TOAST_ICON) / 2;
      Glass.roundRect(g, iconX, iconY, iconX + TOAST_ICON, iconY + TOAST_ICON, TOAST_ICON / 2, Glass.withAlpha(status, Math.round(44.0F * visible)));
      HudShapes.icon(g, toast.severity.icon, iconX + 2, iconY + 2, TOAST_ICON - 4, Glass.withAlpha(status, Math.round(255.0F * visible)));
      int barW = HudStyle.fillWidth(cardW - 2 * HudStyle.RADIUS, timeLeft);
      if (barW > 0) {
         g.fill(x + HudStyle.RADIUS, y + h - 2, x + HudStyle.RADIUS + barW, y + h, Glass.withAlpha(status, Math.round(220.0F * visible)));
      }

      int textAlpha = Math.round(255.0F * visible);
      if (textAlpha >= 10) {
         // The font treats a near-zero alpha as opaque, so text is skipped while the card is all but invisible.
         int textX = x + TOAST_TEXT_X;
         int cap = UiFont.capHeight(FontSize.BODY);
         if (toast.oneLine) {
            int ty = UiFont.centerY(y, h - 1);
            UiFont.draw(g, toast.shownTitle, textX, ty, Glass.withAlpha(theme.text, textAlpha), FontSize.BODY, FontWeight.MEDIUM, false);
            if (!toast.shownBody.isEmpty()) {
               UiFont.draw(g, toast.shownBody, textX + toast.titleW + 5, ty, Glass.withAlpha(theme.textDim, textAlpha), false);
            }
         } else {
            int ty = y + (h - 1 - (cap + 5 + cap)) / 2;
            UiFont.draw(g, toast.shownTitle, textX, ty, Glass.withAlpha(theme.text, textAlpha), FontSize.BODY, FontWeight.MEDIUM, false);
            UiFont.draw(g, toast.shownBody, textX, ty + cap + 5, Glass.withAlpha(theme.textDim, textAlpha), false);
         }
      }
   }

   /**
    * The watermark in the top-left corner. Returns the y just below it, for the text panels that stack under it, and
    * leaves its width in {@link #watermarkRight}. Styles: Simple (the name with its first letter in the accent and
    * the version small beside it), Bar (a small card: name, then fps, time, world) and Logo (the logo image).
    */
   private int drawWatermark(GuiGraphicsExtractor graphics, Minecraft client, HudModule hud, int accent, int accent2, boolean shadow) {
      String wmStyle = hud == null ? "Simple" : hud.watermarkStyle();
      Theme.Palette theme = Theme.current();
      int x = Theme.EDGE;
      int y = Theme.EDGE;
      if ("Logo".equals(wmStyle)) {
         int size = 22;
         GeminiMark.draw(graphics, x, y, size, accent, 1.0F);
         this.watermarkRight = x + size + 2;
         return y + size + 4;
      }

      this.refreshMeta(client, hud, wmStyle);
      boolean hasMeta = !this.metaLineCache.isEmpty();
      if ("Bar".equals(wmStyle)) {
         // A small card with a hairline of the theme gradient along its top: name, then the stats, dim.
         int nameW = UiFont.width(NAME, FontSize.BODY, FontWeight.BOLD);
         int boxH = 17;
         int boxW = 2 * 7 + nameW + (hasMeta ? 12 + this.metaLineWidth : 0);
         int backdrop = this.backdrop;
         boolean textShadow = HudStyle.shadow(backdrop, shadow);
         HudStyle.card(graphics, x, y, x + boxW, y + boxH, backdrop, this.surface, 1.0F);
         if (backdrop != HudStyle.NONE) {
            Glass.gradientH(graphics, x + HudStyle.RADIUS, y, x + boxW - HudStyle.RADIUS, y + 1, accent | 0xFF000000, accent2 | 0xFF000000);
         }

         int textY = UiFont.centerY(y + 1, boxH - 1);
         int textX = x + 7;
         UiFont.draw(graphics, NAME, textX, textY, theme.text, FontSize.BODY, FontWeight.BOLD, textShadow);
         if (hasMeta) {
            int sepX = textX + nameW + 6;
            graphics.fill(sepX, y + 5, sepX + 1, y + boxH - 4, Glass.withAlpha(HudStyle.unit(backdrop), 150));
            UiFont.draw(graphics, this.metaLineCache, sepX + 6, textY, HudStyle.label(backdrop), textShadow);
         }

         this.watermarkRight = x + boxW + 2;
         return y + boxH + 4;
      }

      // Simple: no card. The name large and bold, its first letter in the accent, the version small beside it,
      // sitting on the same baseline.
      String first = NAME.substring(0, 1);
      String rest = NAME.substring(1);
      int firstW = UiFont.width(first, FontSize.LARGE, FontWeight.BOLD);
      int nameW = UiFont.width(NAME, FontSize.LARGE, FontWeight.BOLD);
      UiFont.draw(graphics, first, x, y, accent | 0xFF000000, FontSize.LARGE, FontWeight.BOLD, true);
      // drawn from the measured width of the whole word, so the pair keeps the font's own spacing
      UiFont.draw(graphics, rest, x + nameW - UiFont.width(rest, FontSize.LARGE, FontWeight.BOLD), y, theme.text, FontSize.LARGE, FontWeight.BOLD, true);
      int largeCap = UiFont.capHeight(FontSize.LARGE);
      int smallY = y + largeCap - UiFont.capHeight(FontSize.SMALL);
      int end = x + Math.max(nameW, firstW);
      UiFont.draw(graphics, VERSION, end + 4, smallY, HudStyle.label(HudStyle.NONE), FontSize.SMALL, FontWeight.REGULAR, true);
      end += 4 + UiFont.width(VERSION, FontSize.SMALL, FontWeight.REGULAR);
      if (hasMeta) {
         UiFont.draw(graphics, this.metaLineCache, end + 6, smallY, HudStyle.unit(HudStyle.NONE), FontSize.SMALL, FontWeight.REGULAR, true);
         end += 6 + UiFont.width(this.metaLineCache, FontSize.SMALL, FontWeight.REGULAR);
      }

      this.watermarkRight = end + 4;
      return y + largeCap + 6;
   }

   /**
    * The watermark's stats text, rebuilt at most every few ticks (a frame only draws it). The Bar style lists what
    * its switches ask for; Simple only ever shows a running Baritone task, because that is worth knowing anywhere.
    */
   private void refreshMeta(Minecraft client, HudModule hud, String wmStyle) {
      LocalPlayer player = client.player;
      int tick = player == null ? -1 : player.tickCount / 5;
      int generation = UiFont.generation();
      if (this.metaLineCache != null && tick == this.metaLineTick && wmStyle.equals(this.metaLineStyle) && generation == this.metaGeneration) {
         return;
      }

      boolean bar = "Bar".equals(wmStyle);
      List<String> meta = this.metaParts;
      meta.clear();
      if (bar) {
         if (hud == null || hud.fps()) {
            meta.add(client.getFps() + " fps");
         }

         if (hud == null || hud.time()) {
            LocalTime now = LocalTime.now();
            meta.add((now.getHour() < 10 ? "0" : "") + now.getHour() + (now.getMinute() < 10 ? ":0" : ":") + now.getMinute());
         }

         if (hud != null && hud.speed() && player != null) {
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            meta.add(gemini.util.InfoText.format(Math.hypot(dx, dz) * 20.0, 1) + " b/s");
         }

         if ((hud == null || hud.world()) && client.getSingleplayerServer() != null) {
            String world = client.getSingleplayerServer().getWorldData().getLevelName();
            if (world != null && !world.isEmpty()) {
               meta.add(world.length() > 24 ? world.substring(0, 23) + UiFont.ellipsis() : world);
            }
         }
      }

      String botTask = BaritoneBridge.taskLabel();
      if (botTask != null) {
         meta.add("Baritone: " + botTask);
      }

      this.metaLineCache = String.join(bar ? "   " : " · ", meta);
      this.metaLineWidth = bar ? UiFont.width(this.metaLineCache) : UiFont.width(this.metaLineCache, FontSize.SMALL, FontWeight.REGULAR);
      this.metaLineTick = tick;
      this.metaLineStyle = wmStyle;
      this.metaGeneration = generation;
   }

   /**
    * Fills the automatic layout with everything already on screen (the vanilla HUD, the watermark, Gemini's text
    * panels) and lets {@link HudWidget#arrange} place the widgets around it. Vanilla areas are measured in GUI pixels
    * and converted to the HUD's own scale.
    */
   private void layoutWidgets(HudContext ctx, Minecraft client, float scale, int watermarkRight, int topLeftY) {
      HudWidget[] list = this.widgets;
      if (list == null) {
         List<HudWidget> found = new ArrayList<>();
         for (Module module : this.modules.getModules()) {
            if (module instanceof HudWidget widget) {
               found.add(widget);
            }
         }

         list = found.toArray(new HudWidget[0]);
         this.widgets = list;
      }

      HudFlow flow = this.flow;
      flow.begin(ctx.width, ctx.height);
      if (watermarkRight > 0) {
         flow.reserve(0, 0, watermarkRight + 2, topLeftY);
      }

      this.reservePanels(flow, client.font, ctx.width, ctx.height, topLeftY);
      this.reserveVanilla(flow, client, scale, Math.round(ctx.width * scale), Math.round(ctx.height * scale));
      HudWidget.arrange(ctx, list, flow, client.getWindow().getGuiScale() * scale);
   }

   /** Reserves a rectangle given in GUI pixels, converted to HUD pixels (rounded outward). */
   private static void reserveGui(HudFlow flow, float scale, int x, int y, int w, int h) {
      int x0 = Mth.floor(x / scale);
      int y0 = Mth.floor(y / scale);
      flow.reserve(x0, y0, Mth.ceil((x + w) / scale) - x0, Mth.ceil((y + h) / scale) - y0);
   }

   /** The parts of the vanilla HUD a widget must not cover: hotbar and status bars, chat, boss bars, effect icons, scoreboard. */
   private void reserveVanilla(HudFlow flow, Minecraft client, float scale, int guiW, int guiH) {
      LocalPlayer player = client.player;
      int cx = guiW / 2;
      // Hotbar with the offhand slot and attack indicator either side, XP bar, hearts, armour, food, air, the
      // selected item's name (59 above the bottom) and the action bar line.
      int top = guiH - 70;
      if (player != null) {
         float maxHealth = Math.max(player.getMaxHealth(), player.getHealth());
         int hearts = Mth.ceil((maxHealth + player.getAbsorptionAmount()) / 2.0F);
         int rows = Math.max(1, Mth.ceil(hearts / 10.0F));
         int rowHeight = Math.max(10 - (rows - 2), 3);
         int armorTop = guiH - 39 - (rows - 1) * rowHeight - 10;
         top = Math.min(top, armorTop - 2);
      }

      reserveGui(flow, scale, cx - 122, top, 244, guiH - top);
      // Chat: drawn 40 above the bottom, as wide and tall as the chat options say, in the chat's own scale.
      double chatScale = client.options.chatScale().get();
      if (chatScale > 0.0) {
         int chatW = Mth.ceil(net.minecraft.client.gui.components.ChatComponent.getWidth(client.options.chatWidth().get()) * chatScale) + 12;
         int chatH = Mth.ceil(net.minecraft.client.gui.components.ChatComponent.getHeight(client.options.chatHeightUnfocused().get()) * chatScale);
         reserveGui(flow, scale, 0, guiH - 40 - chatH - 2, chatW, chatH + 40 + 2);
      }

      int bosses = BossBars.count(client);
      if (bosses > 0) {
         // 12 from the top, one bar every 19 pixels, never past a third of the screen.
         int bottom = Math.min(12 + 19 * (bosses - 1) + 5, guiH / 3) + 2;
         reserveGui(flow, scale, cx - 100, 0, 200, bottom);
      }

      if (player != null && !player.getActiveEffects().isEmpty()) {
         int good = 0;
         int bad = 0;
         for (net.minecraft.world.effect.MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
               if (effect.getEffect().value().isBeneficial()) {
                  good++;
               } else {
                  bad++;
               }
            }
         }

         int columns = Math.max(good, bad);
         if (columns > 0) {
            // Beneficial icons in a row at y 1, the others at y 27, 25 pixels apart from the right edge.
            reserveGui(flow, scale, guiW - columns * 25 - 1, 0, columns * 25 + 1, bad > 0 ? 52 : 26);
         }
      }

      if (this.sidebarRows > 0) {
         // Vanilla puts the sidebar's last row a third of its height below the middle, the title above the first.
         int listH = this.sidebarRows * 9;
         int bottom = guiH / 2 + listH / 3;
         reserveGui(flow, scale, guiW - this.sidebarW - 4, bottom - listH - 11, this.sidebarW + 4, listH + 13);
      }
   }

   /** Gemini's own text panels (waypoints, ESP list, storage list), measured where they are drawn. */
   private void reservePanels(HudFlow flow, Font font, int width, int height, int topLeftY) {
      if (!this.waypointLines.isEmpty()) {
         flow.reserve(0, topLeftY, maxWidth(font, this.waypointLines) + 6, 2 + this.waypointLines.size() * LINE);
      }

      if (!this.espLines.isEmpty()) {
         flow.reserve(width / 2 + 88, 22, Math.max(UiFont.width("ESP"), maxWidth(font, this.espLines)) + 4, 12 + this.espLines.size() * LINE);
      }

      if (!this.storageLines.isEmpty()) {
         int y = height / 2 - 40;
         int rows = Math.min(this.storageLines.size() + 1, Math.max(1, (height - 40 - y) / LINE + 1));
         int w = Math.max(UiFont.width("Storage"), maxWidth(font, this.storageLines)) + 8;
         flow.reserve(width - w, y, w, rows * LINE);
      }
   }

   private static int maxWidth(Font font, List<String> lines) {
      int widest = 0;
      for (int i = 0; i < lines.size(); i++) {
         widest = Math.max(widest, UiFont.width(lines.get(i)));
      }

      return widest;
   }

   /** Measures the sidebar scoreboard once a tick: rows shown (at most 15) and the widest line. */
   private void measureSidebar(Minecraft client) {
      this.sidebarRows = 0;
      this.sidebarW = 0;
      if (client.level == null) {
         return;
      }

      Objective objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
      if (objective == null) {
         return;
      }

      Font font = client.font;
      int widest = UiFont.width(objective.getDisplayName());
      int rows = 0;
      for (net.minecraft.world.scores.PlayerScoreEntry entry : client.level.getScoreboard().listPlayerScores(objective)) {
         if (entry.isHidden()) {
            continue;
         }

         widest = Math.max(widest, UiFont.width(entry.ownerName()) + 12 + UiFont.width(Integer.toString(entry.value())));
         if (++rows >= 15) {
            break;
         }
      }

      this.sidebarRows = rows;
      this.sidebarW = widest + 4;
   }

   /** Linear blend of two opaque colours (alpha forced to 255). Kept for the callers outside the HUD. */
   public static int lerp(int from, int to, float t) {
      int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** Rebuilds the waypoint, coordinate, ESP and storage texts once per tick into reused lists. */
   private void refreshPanels(Minecraft client) {
      LocalPlayer player = client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (tick == this.panelTick) {
         return;
      }

      this.panelTick = tick;
      this.measureSidebar(client);
      this.waypointLines.clear();
      this.espLines.clear();
      this.espColors.clear();
      this.storageLines.clear();
      if (player == null) {
         return;
      }

      WaypointsModule waypoints = this.modules.byClass(WaypointsModule.class).orElse(null);
      if (waypoints != null && waypoints.isEnabled() && client.level != null) {
         for (WaypointsModule.Waypoint waypoint : waypoints.waypoints()) {
            if (waypoints.visible(waypoint)) {
               double dx = waypoint.x() + 0.5 - player.getX();
               double dz = waypoint.z() + 0.5 - player.getZ();
               this.waypointLines.add("o " + waypoint.name() + "  " + (int)Math.sqrt(dx * dx + dz * dz) + "m");
            }
         }
      }

      EspModule esp = this.modules.byClass(EspModule.class).orElse(null);
      if (esp != null && esp.isEnabled() && esp.tags() && client.level != null) {
         List<Entity> shown = this.espShown;
         for (Entity entity : client.level.entitiesForRendering()) {
            if (esp.matchesTarget(entity) && !(entity instanceof ItemEntity)) {
               shown.add(entity);
            }
         }

         shown.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)));
         for (int i = 0; i < Math.min(shown.size(), esp.maxTags()); i++) {
            Entity entity = shown.get(i);
            this.espLines.add(entity.getName().getString() + "  " + (int)entity.distanceTo(player) + "m");
            this.espColors.add(0xFF000000 | esp.color(entity));
         }

         // Entities are not kept past the tick.
         shown.clear();
      }

      StorageEspModule storage = this.modules.byClass(StorageEspModule.class).orElse(null);
      if (storage != null && storage.isEnabled()) {
         for (StorageEspModule.Hit hit : storage.hits()) {
            double dx = hit.pos().getX() + 0.5 - player.getX();
            double dy = hit.pos().getY() + 0.5 - player.getY();
            double dz = hit.pos().getZ() + 0.5 - player.getZ();
            this.storageLines.add(hit.label() + "  " + (int)Math.sqrt(dx * dx + dy * dy + dz * dz) + "m");
            if (this.storageLines.size() >= 48) {
               break;
            }
         }
      }
   }

   private void drawWaypoints(GuiGraphicsExtractor graphics, Minecraft client, int accent, int startY, boolean shadow) {
      int y = startY + 2;
      for (int i = 0; i < this.waypointLines.size(); i++) {
         UiFont.draw(graphics, this.waypointLines.get(i), 4, y, accent, shadow);
         y += LINE;
      }
   }

   private void drawEspTags(GuiGraphicsExtractor graphics, Minecraft client, int width, boolean shadow) {
      if (this.espLines.isEmpty()) {
         return;
      }

      int x = width / 2 + 90;
      UiFont.draw(graphics, "ESP", x, 24, Theme.current().textDim, shadow);
      int y = 34;
      for (int i = 0; i < this.espLines.size(); i++) {
         UiFont.draw(graphics, this.espLines.get(i), x, y, this.espColors.get(i), shadow);
         y += LINE;
      }
   }

   private void drawStorage(GuiGraphicsExtractor graphics, Minecraft client, int accent, int width, int height, boolean shadow) {
      if (this.storageLines.isEmpty()) {
         return;
      }

      int y = height / 2 - 40;
      String title = "Storage";
      UiFont.draw(graphics, title, width - 6 - UiFont.width(title), y, accent, shadow);
      y += LINE;
      for (int i = 0; i < this.storageLines.size(); i++) {
         String label = this.storageLines.get(i);
         UiFont.draw(graphics, label, width - 6 - UiFont.width(label), y, Theme.current().text, shadow);
         y += LINE;
         if (y > height - 40) {
            break;
         }
      }
   }

   public static boolean isPlayer(Entity entity) {
      return entity instanceof Player;
   }

   /** A growable int list, so the per-tick colour caches do not box. */
   private static final class IntList {
      private int[] values = new int[8];
      private int size;

      void clear() {
         this.size = 0;
      }

      void add(int value) {
         if (this.size == this.values.length) {
            this.values = Arrays.copyOf(this.values, this.size * 2);
         }

         this.values[this.size++] = value;
      }

      int get(int index) {
         return this.values[index];
      }
   }

   /** One notification card. Mutable animation state is only touched under the toast lock. */
   private static final class Toast {
      final String title;
      final String body;
      final Severity severity;
      long createdAt;
      long durationMs;
      /** Animated vertical position; NaN until first drawn. */
      float stackY = Float.NaN;
      int cardW;
      /** Title and body ellipsized to the card, worked out once with the width. */
      String shownTitle;
      String shownBody;
      /** Width of {@link #shownTitle} as drawn, and the font generation it was measured in. */
      int titleW;
      int generation = -1;
      int height = TOAST_H;
      boolean oneLine;

      Toast(String title, String body, Severity severity, long createdAt, long durationMs) {
         this.title = title;
         this.body = body;
         this.severity = severity;
         this.createdAt = createdAt;
         this.durationMs = durationMs;
      }
   }
}
