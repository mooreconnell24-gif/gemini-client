package gemini.ui;

import gemini.Gemini;
import gemini.module.HudFlow;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.render.CoordinatesModule;
import gemini.module.modules.render.EspModule;
import gemini.module.modules.render.HudModule;
import gemini.module.modules.render.StorageEspModule;
import gemini.module.modules.world.WaypointsModule;
import gemini.util.BaritoneBridge;
import gemini.util.SingleplayerGate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

/**
 * Gemini's in-game overlay: the watermark, the small built-in text panels (waypoints, coordinates, ESP tags,
 * storage), the module HUD widgets and the toast notifications.
 *
 * <p>Everything that only changes per tick (the watermark's stats, the panel texts) is rebuilt once a tick into
 * reused lists; a frame only draws. The {@link HudContext} handed to the widgets is one instance refilled per frame.
 * The module list is the {@code ArrayList} HUD widget.
 */
public final class HudOverlay {
   private static final int LINE = 11;
   private static final int TOAST_H = 26;
   private static final int TOAST_GAP = 5;
   private static final long TOAST_IN_MS = 240L;
   private static final long TOAST_OUT_MS = 220L;
   private static final int MAX_TOASTS = 5;
   /** Widest a toast grows before its text is ellipsized. */
   private static final int TOAST_MAX_W = 280;
   /** Left edge of a toast's text, past the status bar. */
   private static final int TOAST_TEXT_X = 10;
   private static final ArrayDeque<HudOverlay.Toast> TOASTS = new ArrayDeque<>();
   private final ModuleManager modules;
   private long lastFrameMs = Util.getMillis();
   private int surface = -15987178;
   /** One context, refilled every frame instead of allocated. */
   private HudContext context;
   /** The watermark's texts only change once a tick, so they are rebuilt per tick instead of per frame. */
   private String metaLineCache;
   private int metaLineWidth;
   private int metaLineTick = -1;
   private String metaLineStyle;
   private final List<String> metaParts = new ArrayList<>();
   /** Per-tick caches of the text panels so a frame only draws. */
   private int panelTick = Integer.MIN_VALUE;
   private final List<String> waypointLines = new ArrayList<>();
   private final List<String> coordLines = new ArrayList<>();
   private final IntList coordColors = new IntList();
   private boolean coordsRight;
   private boolean coordsBottom;
   private final List<Entity> espShown = new ArrayList<>();
   private final List<String> espLines = new ArrayList<>();
   private final IntList espColors = new IntList();
   private final List<String> storageLines = new ArrayList<>();
   /** Automatic layout of the widgets the user has not moved; refilled every frame. */
   private final HudFlow flow = new HudFlow();
   private HudWidget[] widgets;
   /** Sidebar scoreboard size in GUI pixels, worked out once a tick (0 when none is shown). */
   private int sidebarW;
   private int sidebarRows;

   public HudOverlay(ModuleManager modules) {
      this.modules = modules;
   }

   /**
    * What a toast is about; picks the colour of the small bar on its left edge. {@code badge} and {@code glyph}
    * are a short name and a one-character symbol for the kind, for callers that describe a toast in text.
    */
   public enum Severity {
      INFO("INFO", Glass.INFO, "i"),
      SUCCESS("OK", Glass.OK, "✔"),
      WARNING("WARN", Glass.WARN, "!"),
      ERROR("ALERT", Glass.ERROR, "✖"),
      ENABLED("ON", Glass.OK, "+"),
      DISABLED("OFF", Glass.TEXT_FAINT, "-");

      public final String badge;
      public final int color;
      public final String glyph;

      Severity(String badge, int color, String glyph) {
         this.badge = badge;
         this.color = color;
         this.glyph = glyph;
      }
   }

   /** Module on/off toast. */
   public static void pushNotification(String title, String body, boolean enabled) {
      toast(title, body, enabled ? Severity.ENABLED : Severity.DISABLED, 2100L);
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
            if (hud == null || hud.watermark()) {
               float size = hud == null ? 1.0F : hud.watermarkSize();
               graphics.pose().pushMatrix();
               graphics.pose().scale(size, size);
               topLeftY = Math.round(this.drawWatermark(graphics, client, hud, accent, accent2, shadow) * size);
               graphics.pose().popMatrix();
            }

            int watermarkRight = 0;
            if (hud == null || hud.watermark()) {
               // Generous estimate of the watermark card: logo, the name and the stats line plus padding.
               float size = hud == null ? 1.0F : hud.watermarkSize();
               watermarkRight = Math.round((44 + client.font.width("Gemini") + this.metaLineWidth) * size);
            }

            this.refreshPanels(client);
            this.drawWaypoints(graphics, client, accent, topLeftY, shadow);
            this.drawCoordinates(graphics, client, width, height, shadow);
            this.drawEspTags(graphics, client, width, shadow);
            this.drawStorage(graphics, client, accent, width, height, shadow);
            float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            HudContext ctx = this.context;
            if (ctx == null) {
               ctx = new HudContext(graphics, client, width, height, accent, accent2, this.surface, shadow, partial);
               this.context = ctx;
            }

            ctx.update(graphics, client, width, height, accent, accent2, this.surface, shadow, partial, delta);
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
      // Slots are laid out from the anchored edge, newest nearest it. Each card glides toward its slot, so the stack
      // closes up smoothly when a card above or below leaves.
      float slot = bottom ? height - 6 - TOAST_H : 6.0F;
      synchronized (TOASTS) {
         Iterator<HudOverlay.Toast> it = TOASTS.descendingIterator();
         while (it.hasNext()) {
            HudOverlay.Toast toast = it.next();
            long age = now - toast.createdAt;
            if (age >= toast.durationMs) {
               it.remove();
               continue;
            }

            if (toast.cardW == 0) {
               int natural = Math.max(font.width(toast.title), font.width(toast.body)) + TOAST_TEXT_X + 8;
               // Never wider than the screen (minus a margin) or a comfortable reading width; longer text is cut with "..".
               toast.cardW = Math.max(Math.min(140, width - 12), Math.min(natural, Math.min(TOAST_MAX_W, width - 12)));
               toast.shownTitle = Theme.fit(toast.title, toast.cardW - TOAST_TEXT_X - 8, font::width);
               toast.shownBody = Theme.fit(toast.body, toast.cardW - TOAST_TEXT_X - 8, font::width);
            }

            float slide = toastSlide(age, toast.durationMs);
            float visible = Mth.clamp(slide, 0.0F, 1.0F);
            toast.stackY = Float.isNaN(toast.stackY) ? slot : Glass.approach(toast.stackY, slot, 14.0F, delta);
            int cardW = toast.cardW;
            int travel = cardW + 6;
            int x = right ? width - Math.round(travel * slide) : Math.round(travel * slide) - cardW;
            this.drawToast(graphics, font, toast, x, Math.round(toast.stackY), cardW, visible);
            // A leaving card hands its slot over as it slides out, so the next one starts moving at once.
            float share = toast.durationMs - age < TOAST_OUT_MS ? visible : 1.0F;
            slot += (bottom ? -1.0F : 1.0F) * (TOAST_H + TOAST_GAP) * share;
         }
      }
   }

   /**
    * One toast: a flat card with a thin bar in the severity colour on its left edge, the title and the body. No
    * badge, icon, countdown or pulsing; the colour bar is the only thing that tells the kinds apart.
    */
   private void drawToast(GuiGraphicsExtractor g, Font font, HudOverlay.Toast toast, int x, int y, int cardW, float visible) {
      int status = toast.severity.color;
      Glass.roundRect(g, x, y, x + cardW, y + TOAST_H, Glass.RADIUS, Glass.withAlpha(this.surface, Math.round(225.0F * visible)));
      Glass.roundOutline(g, x, y, x + cardW, y + TOAST_H, Glass.RADIUS, Glass.withAlpha(0xFFFFFF, Math.round(20.0F * visible)));
      g.fill(x + 3, y + 4, x + 5, y + TOAST_H - 4, Glass.withAlpha(status, Math.round(220.0F * visible)));
      int textAlpha = Math.round(255.0F * visible);
      if (textAlpha >= 10) {
         // The font treats a near-zero alpha as opaque, so text is skipped while the card is all but invisible.
         g.text(font, toast.shownTitle, x + TOAST_TEXT_X, y + 5, Glass.withAlpha(Glass.TEXT, textAlpha), false);
         g.text(font, toast.shownBody, x + TOAST_TEXT_X, y + 15, Glass.withAlpha(Glass.TEXT_DIM, textAlpha), false);
      }
   }

   /**
    * The watermark in the top-left corner: "Gemini" and a few stats on one line. Returns the y just below it, for
    * the text panels that stack under it. Styles: Card (default, optional logo), Compact (slim card, no logo) and
    * Text (no card).
    */
   private int drawWatermark(GuiGraphicsExtractor graphics, Minecraft client, HudModule hud, int accent, int accent2, boolean shadow) {
      Font font = client.font;
      String wmStyle = hud == null ? "Card" : hud.watermarkStyle();
      LocalPlayer player = client.player;
      int tick = player == null ? -1 : player.tickCount;
      if (this.metaLineCache == null || tick != this.metaLineTick || !wmStyle.equals(this.metaLineStyle)) {
         List<String> meta = this.metaParts;
         meta.clear();
         meta.add(Gemini.VERSION);
         if (hud == null || hud.fps()) {
            meta.add(client.getFps() + " FPS");
         }

         // Ping only means something on a real connection; in single player it is always zero.
         if ((hud == null || hud.ping()) && !client.isLocalServer() && client.getConnection() != null && player != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
               meta.add(info.getLatency() + " ms");
            }
         }

         if (hud != null && hud.speed() && player != null) {
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            meta.add(String.format(Locale.ROOT, "%.1f b/s", Math.hypot(dx, dz) * 20.0));
         }

         String botTask = BaritoneBridge.taskLabel();
         if (botTask != null) {
            meta.add("Baritone: " + botTask);
         }

         this.metaLineCache = String.join(" \u00b7 ", meta);
         this.metaLineWidth = font.width(this.metaLineCache);
         this.metaLineTick = tick;
         this.metaLineStyle = wmStyle;
      }

      String name = "Gemini";
      int nameWidth = font.width(name);
      if ("Text".equals(wmStyle)) {
         graphics.text(font, name, 4, 4, accent, shadow);
         graphics.text(font, this.metaLineCache, 4 + nameWidth + 5, 4, Glass.TEXT_DIM, shadow);
         return 16;
      }

      boolean compact = "Compact".equals(wmStyle);
      boolean hasLogo = !compact && (hud == null || hud.logo());
      int boxH = compact ? 12 : 14;
      int textX = 4 + (hasLogo ? 16 : 5);
      int boxW = textX - 4 + nameWidth + 5 + this.metaLineWidth + 5;
      Glass.hudCard(graphics, 4, 4, 4 + boxW, 4 + boxH, this.surface, accent, accent2, 1.0F);
      if (hasLogo) {
         GeminiMark.draw(graphics, 7, 6, 10, accent, 1.0F);
      }

      int textY = 4 + (boxH - 8) / 2 + 1;
      graphics.text(font, name, textX, textY, compact ? accent : Glass.TEXT, shadow);
      graphics.text(font, this.metaLineCache, textX + nameWidth + 5, textY, Glass.TEXT_DIM, false);
      return 4 + boxH + 6;
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
      HudWidget.arrange(ctx, list, flow);
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

   /** Gemini's own text panels (waypoints, coordinates, ESP list, storage list), measured where they are drawn. */
   private void reservePanels(HudFlow flow, Font font, int width, int height, int topLeftY) {
      if (!this.waypointLines.isEmpty()) {
         flow.reserve(0, topLeftY, maxWidth(font, this.waypointLines) + 6, 2 + this.waypointLines.size() * LINE);
      }

      if (!this.coordLines.isEmpty()) {
         int w = maxWidth(font, this.coordLines) + 6;
         int h = this.coordLines.size() * LINE;
         int y = this.coordsBottom ? height - 6 - h - 10 : 38;
         flow.reserve(this.coordsRight ? width - w : 0, y, w, h);
      }

      if (!this.espLines.isEmpty()) {
         flow.reserve(width / 2 + 88, 22, Math.max(font.width("ESP"), maxWidth(font, this.espLines)) + 4, 12 + this.espLines.size() * LINE);
      }

      if (!this.storageLines.isEmpty()) {
         int y = height / 2 - 40;
         int rows = Math.min(this.storageLines.size() + 1, Math.max(1, (height - 40 - y) / LINE + 1));
         int w = Math.max(font.width("Storage"), maxWidth(font, this.storageLines)) + 8;
         flow.reserve(width - w, y, w, rows * LINE);
      }
   }

   private static int maxWidth(Font font, List<String> lines) {
      int widest = 0;
      for (int i = 0; i < lines.size(); i++) {
         widest = Math.max(widest, font.width(lines.get(i)));
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
      int widest = font.width(objective.getDisplayName());
      int rows = 0;
      for (net.minecraft.world.scores.PlayerScoreEntry entry : client.level.getScoreboard().listPlayerScores(objective)) {
         if (entry.isHidden()) {
            continue;
         }

         widest = Math.max(widest, font.width(entry.ownerName()) + 12 + font.width(Integer.toString(entry.value())));
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
      this.coordLines.clear();
      this.coordColors.clear();
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

      CoordinatesModule coords = this.modules.byClass(CoordinatesModule.class).orElse(null);
      if (coords != null && coords.isEnabled()) {
         this.buildCoordinates(client, player, coords);
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

   private void buildCoordinates(Minecraft client, LocalPlayer player, CoordinatesModule coords) {
      BlockPos pos = player.blockPosition();
      this.coordLines.add(pos.getX() + "  " + pos.getY() + "  " + pos.getZ());
      this.coordColors.add(-1511689);
      if (coords.showNether() && client.level != null) {
         boolean nether = client.level.dimension() == Level.NETHER;
         boolean overworld = client.level.dimension() == Level.OVERWORLD;
         if (nether || overworld) {
            double factor = nether ? 8.0 : 0.125;
            this.coordLines.add((nether ? "Overworld " : "Nether ") + Math.round(player.getX() * factor) + "  " + Math.round(player.getZ() * factor));
            this.coordColors.add(-6641995);
         }
      }

      if (coords.showDirection()) {
         String biome = "";
         if (coords.showBiome() && client.level != null) {
            biome = "  ·  " + biomeName(client, pos);
         }

         this.coordLines.add(facing(player.getYRot()) + biome);
         this.coordColors.add(0);
      } else if (coords.showBiome() && client.level != null) {
         this.coordLines.add(biomeName(client, pos));
         this.coordColors.add(0);
      }

      String position = coords.position();
      this.coordsRight = position.endsWith("Right");
      this.coordsBottom = position.startsWith("Bottom");
   }

   private static String biomeName(Minecraft client, BlockPos pos) {
      return client.level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath().replace('_', ' ')).orElse("");
   }

   private void drawWaypoints(GuiGraphicsExtractor graphics, Minecraft client, int accent, int startY, boolean shadow) {
      int y = startY + 2;
      for (int i = 0; i < this.waypointLines.size(); i++) {
         graphics.text(client.font, this.waypointLines.get(i), 4, y, accent, shadow);
         y += LINE;
      }
   }

   private void drawCoordinates(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean shadow) {
      List<String> lines = this.coordLines;
      if (lines.isEmpty()) {
         return;
      }

      Font font = client.font;
      int accent = Glass.accent();
      int y = this.coordsBottom ? height - 6 - lines.size() * LINE - 10 : 38;
      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);
         int x = this.coordsRight ? width - 4 - font.width(line) : 4;
         int color = this.coordColors.get(i);
         graphics.text(font, line, x, y + i * LINE, color == 0 ? accent : color, shadow);
      }
   }

   private void drawEspTags(GuiGraphicsExtractor graphics, Minecraft client, int width, boolean shadow) {
      if (this.espLines.isEmpty()) {
         return;
      }

      int x = width / 2 + 90;
      graphics.text(client.font, "ESP", x, 24, -6641995, false);
      int y = 34;
      for (int i = 0; i < this.espLines.size(); i++) {
         graphics.text(client.font, this.espLines.get(i), x, y, this.espColors.get(i), shadow);
         y += LINE;
      }
   }

   private void drawStorage(GuiGraphicsExtractor graphics, Minecraft client, int accent, int width, int height, boolean shadow) {
      if (this.storageLines.isEmpty()) {
         return;
      }

      int y = height / 2 - 40;
      String title = "Storage";
      graphics.text(client.font, title, width - 6 - client.font.width(title), y, accent, shadow);
      y += LINE;
      for (int i = 0; i < this.storageLines.size(); i++) {
         String label = this.storageLines.get(i);
         graphics.text(client.font, label, width - 6 - client.font.width(label), y, -2761496, shadow);
         y += LINE;
         if (y > height - 40) {
            break;
         }
      }
   }

   private static String facing(float yaw) {
      float wrapped = Mth.wrapDegrees(yaw);
      if (wrapped >= -45.0F && wrapped < 45.0F) {
         return "South";
      } else if (wrapped >= 45.0F && wrapped < 135.0F) {
         return "West";
      } else {
         return wrapped >= -135.0F && wrapped < -45.0F ? "East" : "North";
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

      Toast(String title, String body, Severity severity, long createdAt, long durationMs) {
         this.title = title;
         this.body = body;
         this.severity = severity;
         this.createdAt = createdAt;
         this.durationMs = durationMs;
      }
   }
}
