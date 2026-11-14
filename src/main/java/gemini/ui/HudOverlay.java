package gemini.ui;

import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.render.CoordinatesModule;
import gemini.module.modules.render.EspModule;
import gemini.module.modules.render.HudModule;
import gemini.module.modules.render.StorageEspModule;
import gemini.module.modules.world.WaypointsModule;
import gemini.module.setting.ColorSetting;
import gemini.util.BaritoneBridge;
import gemini.util.SingleplayerGate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

public final class HudOverlay {
   private static final int LINE = 11;
   private static final ArrayDeque<HudOverlay.Toast> TOASTS = new ArrayDeque<>();
   private final ModuleManager modules;
   private final Map<Module, Float> arrayAnimations = new IdentityHashMap<>();
   private long lastFrameMs = Util.getMillis();
   private int surface = -15987178;
   /** The watermark's stats line only changes once a tick, so it is rebuilt per tick instead of per frame. */
   private String metaLineCache;
   private int metaLineTick = -1;
   private String metaLineStyle;

   public HudOverlay(ModuleManager modules) {
      this.modules = modules;
   }

   public static void pushNotification(String title, String body, boolean enabled) {
      synchronized (TOASTS) {
         Iterator<HudOverlay.Toast> it = TOASTS.iterator();
         while (it.hasNext()) {
            if (it.next().title.equals(title)) {
               it.remove();
               break;
            }
         }

         if (TOASTS.size() >= 5) {
            TOASTS.removeFirst();
         }

         TOASTS.addLast(new HudOverlay.Toast(title, body, enabled, Util.getMillis(), 2100L));
      }
   }

   private static float easeOutCubic(float t) {
      float clamped = Mth.clamp(t, 0.0F, 1.0F);
      float inv = 1.0F - clamped;
      return 1.0F - inv * inv * inv;
   }

   public void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      Minecraft client = Minecraft.getInstance();
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
               float size = hud == null ? 0.7F : hud.watermarkSize();
               graphics.pose().pushMatrix();
               graphics.pose().scale(size, size);
               topLeftY = Math.round(this.drawWatermark(graphics, client, hud, accent, accent2, shadow) * size);
               graphics.pose().popMatrix();
            }

            if (hud == null || hud.arraylist()) {
               this.drawArrayList(graphics, client, hud, style, accent, accent2, width, height, shadow);
            }

            topLeftY = this.drawWaypoints(graphics, client, accent, topLeftY, shadow);
            this.drawCoordinates(graphics, client, accent, width, height, shadow);
            this.drawEspTags(graphics, client, width, shadow);
            this.drawStorage(graphics, client, accent, width, height, shadow);
            this.modules.renderHud(new HudContext(graphics, client, width, height, accent, accent2, this.surface, shadow, deltaTracker.getGameTimeDeltaPartialTick(false)));
            this.drawToasts(graphics, client.font, width, height, accent, accent2);
            graphics.pose().popMatrix();
         }
      }
   }

   private void drawToasts(GuiGraphicsExtractor graphics, Font font, int width, int height, int accent, int accent2) {
      long now = Util.getMillis();
      float cursorY = height - 34.0F;
      synchronized (TOASTS) {
         Iterator<HudOverlay.Toast> it = TOASTS.iterator();

         while (it.hasNext()) {
            HudOverlay.Toast toast = it.next();
            long age = now - toast.createdAt;
            if (age >= toast.durationMs) {
               it.remove();
            } else {
               float in = easeOutCubic((float)age / 210.0F);
               float out = easeOutCubic((float)(toast.durationMs - age) / 240.0F);
               float slide = Math.min(in, out);
               String badge = toast.enabled ? "ON" : "OFF";
               int badgeW = font.width(badge) + 8;
               int cardW = Math.max(132, Math.max(font.width(toast.title) + badgeW + 14, font.width(toast.body) + 18));
               int cardH = 25;
               int x = width - 6 - Math.round((cardW + 4) * slide) + 4;
               int y = Math.round(cursorY);
               int statusColor = toast.enabled ? -11086174 : -1548963;
               int alphaBg = Math.round(228.0F * slide);
               int alphaText = Math.round(255.0F * slide);
               graphics.fill(x - 1, y - 1, x + cardW + 1, y + cardH + 1, GeminiMark.withAlpha(accent, Math.round(62.0F * slide)));
               graphics.fill(x, y, x + cardW, y + cardH, GeminiMark.withAlpha(this.surface, alphaBg));
               graphics.fill(x, y, x + 2, y + cardH, GeminiMark.withAlpha(statusColor, alphaText));
               int pillX = x + cardW - badgeW - 5;
               graphics.fill(pillX, y + 4, pillX + badgeW, y + 13, GeminiMark.withAlpha(statusColor, Math.round(45.0F * slide)));
               graphics.text(font, badge, pillX + 4, y + 5, GeminiMark.withAlpha(statusColor, alphaText), false);
               float progress = 1.0F - (float)age / (float)toast.durationMs;
               int barW = Math.round(cardW * progress);
               graphics.fill(x, y + cardH - 2, x + barW, y + cardH, GeminiMark.withAlpha(lerp(accent, accent2, 1.0F - progress), alphaText));
               graphics.text(font, toast.title, x + 7, y + 4, GeminiMark.withAlpha(-1, alphaText), false);
               graphics.text(font, toast.body, x + 7, y + 14, GeminiMark.withAlpha(-4733999, alphaText), false);
               cursorY -= (cardH + 5) * slide;
            }
         }
      }
   }

   private int drawWatermark(GuiGraphicsExtractor graphics, Minecraft client, HudModule hud, int accent, int accent2, boolean shadow) {
      Font font = client.font;
      String wmStyle = hud == null ? "Nextgen Card" : hud.watermarkStyle();
      LocalPlayer player = client.player;
      int tick = player == null ? -1 : player.tickCount;
      String metaLine = this.metaLineCache;
      if (metaLine == null || tick != this.metaLineTick || !wmStyle.equals(this.metaLineStyle)) {
         List<String> meta = new ArrayList<>();
         meta.add("v" + Gemini.VERSION);
         if (hud == null || hud.fps()) {
            meta.add(client.getFps() + " FPS");
         }

         if ((hud == null || hud.ping()) && client.getConnection() != null && player != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
               meta.add(info.getLatency() + "ms");
            }
         }

         if ((hud == null || hud.speed()) && player != null) {
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            meta.add(String.format(Locale.ROOT, "%.1f b/s", Math.hypot(dx, dz) * 20.0));
         }

         String botTask = BaritoneBridge.taskLabel();
         if (botTask != null) {
            meta.add("Baritone: " + botTask);
         }

         metaLine = String.join("  ·  ", meta);
         this.metaLineCache = metaLine;
         this.metaLineTick = tick;
         this.metaLineStyle = wmStyle;
      }

      if ("Minimal".equals(wmStyle)) {
         String line = "GEMINI NEXTGEN  ·  " + metaLine;
         graphics.text(font, line, 6, 6, accent, shadow);
         return 18;
      } else if ("LiquidBounce Pill".equals(wmStyle)) {
         String title = "GEMINI ";
         String sub = "NEXTGEN  |  " + metaLine;
         int pillW = 14 + font.width(title) + font.width(sub);
         int pillH = 17;
         graphics.fill(3, 3, 5 + pillW, 5 + pillH, GeminiMark.withAlpha(accent, 42));
         graphics.fill(4, 4, 4 + pillW, 4 + pillH, GeminiMark.withAlpha(this.surface, 225));
         graphics.fill(4, 4, 6, 4 + pillH, accent);
         drawHorizontalGradient(graphics, 4, 4, 4 + pillW, 5, accent, accent2);
         graphics.text(font, title, 10, 8, accent, shadow);
         graphics.text(font, sub, 10 + font.width(title), 8, -1511689, shadow);
         return 25;
      } else {
         boolean hasLogo = hud == null || hud.logo();
         int textX = hasLogo ? 28 : 8;
         int boxW = textX + Math.max(font.width("GEMINI NEXTGEN"), font.width(metaLine)) + 10;
         int boxH = 25;
         graphics.fill(3, 3, 4 + boxW + 1, 4 + boxH + 1, GeminiMark.withAlpha(accent, 52));
         graphics.fill(4, 4, 4 + boxW, 4 + boxH, GeminiMark.withAlpha(this.surface, 222));
         graphics.fill(4, 6, 4 + boxW, 14, GeminiMark.withAlpha(accent, 14));
         drawHorizontalGradient(graphics, 4, 4, 4 + boxW, 6, accent, accent2);
         if (hasLogo) {
            GeminiMark.draw(graphics, 6, 7, 18, accent, 1.0F);
         }

         graphics.text(font, "GEMINI", textX, 8, -1, shadow);
         graphics.text(font, "NEXTGEN", textX + font.width("GEMINI "), 8, accent2, shadow);
         graphics.text(font, metaLine, textX, 18, -4733999, false);
         return 35;
      }
   }

   private void drawArrayList(
      GuiGraphicsExtractor graphics, Minecraft client, HudModule hud, ClickGuiModule style, int accent, int accent2, int width, int height, boolean shadow
   ) {
      Font font = client.font;
      long now = Util.getMillis();
      float delta = Math.min(0.05F, Math.max(0.001F, (float)(now - this.lastFrameMs) / 1000.0F));
      this.lastFrameMs = now;
      boolean animateSlide = hud == null || hud.slideAnimations();
      boolean showKeys = hud != null && hud.keys();
      boolean showInfo = hud == null || hud.showInfo();
      String tagStyle = hud == null ? "Plain" : hud.tagStyle();
      boolean lower = hud != null && hud.lowercase();
      List<Module> candidates = new ArrayList<>();
      float stepFactor = 1.0F - (float)Math.exp(-delta * 16.0F);

      for (Module module : this.modules.getModules()) {
         boolean active = module.isEnabled() && module.isListed();
         float current = this.arrayAnimations.getOrDefault(module, active ? 1.0F : 0.0F);
         float target = active ? 1.0F : 0.0F;
         float next = animateSlide ? Mth.lerp(stepFactor, current, target) : target;
         if (Math.abs(next - target) < 0.008F) {
            next = target;
         }

         if (next > 0.008F) {
            this.arrayAnimations.put(module, next);
            candidates.add(module);
         } else {
            this.arrayAnimations.remove(module);
         }
      }

      if (!candidates.isEmpty()) {
         String sort = hud == null ? "Length" : hud.sort();
         Comparator<Module> byLength = Comparator.<Module>comparingInt(modulex -> totalWidth(font, modulex, showKeys, showInfo, tagStyle, lower)).reversed();
         switch (sort) {
            case "Alphabetical":
               candidates.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
               break;
            case "Category":
               candidates.sort(Comparator.<Module>comparingInt(modulex -> modulex.getCategory().ordinal()).thenComparing(byLength));
               break;
            default:
               candidates.sort(byLength);
         }

         boolean right = hud == null || hud.listRight();
         boolean bottom = hud != null && hud.listBottom();
         boolean background = hud == null || hud.background();
         boolean bar = hud == null || hud.sideBar();
         boolean glowOutline = hud == null || hud.glowOutline();
         float opacity = hud == null ? 0.68F : hud.opacity();
         String colors = hud == null ? "Pulsing Rainbow" : hud.colors();
         float totalHeight = 0.0F;

         for (Module module : candidates) {
            totalHeight += 11.0F * easeOutCubic(this.arrayAnimations.getOrDefault(module, 1.0F));
         }

         float cursorY = bottom ? height - 4.0F - totalHeight : 4.0F;
         int prevBoxLeft = -1;
         int prevBoxRight = -1;

         for (int i = 0; i < candidates.size(); i++) {
            Module module = candidates.get(i);
            float rawAnim = this.arrayAnimations.getOrDefault(module, 1.0F);
            float anim = easeOutCubic(rawAnim);
            String label = label(module, showKeys, lower);
            String info = showInfo ? formatInfo(module.info(), tagStyle, lower) : null;
            int labelWidth = totalWidth(font, module, showKeys, showInfo, tagStyle, lower);
            int rowColor = computeRowColor(colors, accent, accent2, module, i, candidates.size(), now);
            int nextColor = computeRowColor(colors, accent, accent2, module, Math.min(candidates.size() - 1, i + 1), candidates.size(), now);
            int slideOffset = Math.round((1.0F - anim) * (labelWidth + 12));
            int boxLeft = right ? width - labelWidth - 10 + slideOffset : -slideOffset;
            int boxRight = right ? width + slideOffset : labelWidth + 10 - slideOffset;
            int y = Math.round(cursorY);
            int rowBottom = y + 10;
            if (background) {
               if (glowOutline) {
                  graphics.fill(boxLeft - 1, y - 1, boxRight + 1, rowBottom + 1, GeminiMark.withAlpha(rowColor, (int)(36.0F * anim)));
               }

               graphics.fill(boxLeft, y - 1, boxRight, rowBottom, GeminiMark.withAlpha(this.surface, (int)(255.0F * opacity * anim)));
               graphics.fill(boxLeft, y - 1, boxRight, y + 3, GeminiMark.withAlpha(rowColor, (int)(16.0F * anim)));
            }

            if (glowOutline) {
               int innerEdgeX = right ? boxLeft - 1 : boxRight;
               graphics.fill(innerEdgeX, y - 1, innerEdgeX + 1, rowBottom, GeminiMark.withAlpha(rowColor, (int)(205.0F * anim)));
               if (i == 0) {
                  graphics.fill(boxLeft - 1, y - 2, boxRight, y - 1, GeminiMark.withAlpha(rowColor, (int)(205.0F * anim)));
               } else if (right && prevBoxLeft != -1 && boxLeft != prevBoxLeft) {
                  int stepL = Math.min(prevBoxLeft, boxLeft) - 1;
                  int stepR = Math.max(prevBoxLeft, boxLeft);
                  graphics.fill(stepL, y - 1, stepR, y, GeminiMark.withAlpha(rowColor, (int)(205.0F * anim)));
               } else if (!right && prevBoxRight != -1 && boxRight != prevBoxRight) {
                  int stepL = Math.min(prevBoxRight, boxRight);
                  int stepR = Math.max(prevBoxRight, boxRight) + 1;
                  graphics.fill(stepL, y - 1, stepR, y, GeminiMark.withAlpha(rowColor, (int)(205.0F * anim)));
               }

               if (i == candidates.size() - 1) {
                  graphics.fill(boxLeft - 1, rowBottom, boxRight, rowBottom + 1, GeminiMark.withAlpha(rowColor, (int)(205.0F * anim)));
               }
            }

            if (bar) {
               int barLeft = right ? width - 2 : 0;
               int barRight = right ? width : 2;
               graphics.fill(barLeft, y - 1, barRight, y + 5, GeminiMark.withAlpha(rowColor, (int)(255.0F * anim)));
               graphics.fill(barLeft, y + 5, barRight, rowBottom, GeminiMark.withAlpha(nextColor, (int)(255.0F * anim)));
            }

            int textX = right ? boxLeft + 4 : boxLeft + 6;
            int textAlpha = Math.max(8, (int)(255.0F * anim));
            graphics.text(font, label, textX, y, GeminiMark.withAlpha(rowColor, textAlpha), shadow);
            if (info != null) {
               graphics.text(font, info, textX + font.width(label) + 4, y, GeminiMark.withAlpha(-4733999, textAlpha), shadow);
            }

            prevBoxLeft = boxLeft;
            prevBoxRight = boxRight;
            cursorY += 11.0F * anim;
         }
      }
   }

   private static int computeRowColor(String mode, int accent, int accent2, Module module, int index, int total, long now) {
      return switch (mode) {
         case "Accent" -> accent;
         case "Category" -> module.getCategory().accent();
         case "Rainbow" -> ColorSetting.hueColor((float)((now / 22L + index * 16L) % 360L) / 360.0F, 0.65F, 1.0F);
         case "Pulsing Rainbow" -> {
            float hue = (float)((now / 20L + index * 18L) % 360L) / 360.0F;
            float pulse = 0.78F + 0.22F * Mth.sin((float)(now / 220.0 + index * 0.45));
            yield ColorSetting.hueColor(hue, 0.72F, Mth.clamp(pulse, 0.55F, 1.0F));
         }
         case "Neon Wave" -> {
            float wave = 0.5F + 0.5F * Mth.sin((float)(now / 260.0 - index * 0.38));
            yield lerp(accent, accent2, wave);
         }
         default -> lerp(accent, accent2, total <= 1 ? 0.0F : (float)index / (total - 1));
      };
   }

   private static void drawHorizontalGradient(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colorLeft, int colorRight) {
      int width = Math.max(1, right - left);
      int steps = Math.min(24, width);

      for (int i = 0; i < steps; i++) {
         int sx = left + width * i / steps;
         int ex = left + width * (i + 1) / steps;
         float t = steps <= 1 ? 0.0F : (float)i / (steps - 1);
         graphics.fill(sx, top, ex, bottom, lerp(colorLeft, colorRight, t));
      }
   }

   private static int totalWidth(Font font, Module module, boolean keys, boolean info, String tagStyle, boolean lower) {
      String extra = info ? formatInfo(module.info(), tagStyle, lower) : null;
      return font.width(label(module, keys, lower)) + (extra == null ? 0 : 4 + font.width(extra));
   }

   private static String label(Module module, boolean keys, boolean lower) {
      String base = keys && module.getKeybind() != -1 ? module.getName() + " [" + GeminiKeys.displayName(module.getKeybind()) + "]" : module.getName();
      return lower ? base.toLowerCase(Locale.ROOT) : base;
   }

   private static String formatInfo(String raw, String tagStyle, boolean lower) {
      if (raw != null && !raw.isEmpty()) {
         String text = lower ? raw.toLowerCase(Locale.ROOT) : raw;

         return switch (tagStyle) {
            case "Brackets [Info]" -> "[" + text + "]";
            case "Dot · Info" -> "· " + text;
            case "Angle <Info>" -> "<" + text + ">";
            default -> text;
         };
      } else {
         return null;
      }
   }

   public static int lerp(int from, int to, float t) {
      int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
      int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
      int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private int drawWaypoints(GuiGraphicsExtractor graphics, Minecraft client, int accent, int startY, boolean shadow) {
      WaypointsModule waypoints = this.modules.byClass(WaypointsModule.class).orElse(null);
      if (waypoints != null && waypoints.isEnabled() && client.level != null) {
         int y = startY + 2;

         for (WaypointsModule.Waypoint waypoint : waypoints.waypoints()) {
            if (waypoints.visible(waypoint)) {
               double dx = waypoint.x() + 0.5 - client.player.getX();
               double dz = waypoint.z() + 0.5 - client.player.getZ();
               int distance = (int)Math.sqrt(dx * dx + dz * dz);
               graphics.text(client.font, "o " + waypoint.name() + "  " + distance + "m", 4, y, accent, shadow);
               y += 11;
            }
         }

         return y;
      } else {
         return startY;
      }
   }

   private void drawCoordinates(GuiGraphicsExtractor graphics, Minecraft client, int accent, int width, int height, boolean shadow) {
      CoordinatesModule coords = this.modules.byClass(CoordinatesModule.class).orElse(null);
      if (coords != null && coords.isEnabled()) {
         Font font = client.font;
         BlockPos pos = client.player.blockPosition();
         List<String> lines = new ArrayList<>();
         List<Integer> colors = new ArrayList<>();
         lines.add(pos.getX() + "  " + pos.getY() + "  " + pos.getZ());
         colors.add(-1511689);
         if (coords.showNether() && client.level != null) {
            boolean nether = client.level.dimension() == Level.NETHER;
            boolean overworld = client.level.dimension() == Level.OVERWORLD;
            if (nether || overworld) {
               double factor = nether ? 8.0 : 0.125;
               lines.add((nether ? "Overworld " : "Nether ") + Math.round(client.player.getX() * factor) + "  " + Math.round(client.player.getZ() * factor));
               colors.add(-6641995);
            }
         }

         if (coords.showDirection()) {
            String facing = facing(client.player.getYRot());
            String biome = "";
            if (coords.showBiome() && client.level != null) {
               biome = "  ·  " + client.level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath().replace('_', ' ')).orElse("");
            }

            lines.add(facing + biome);
            colors.add(accent);
         } else if (coords.showBiome() && client.level != null) {
            lines.add(client.level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath().replace('_', ' ')).orElse(""));
            colors.add(accent);
         }

         String position = coords.position();
         boolean right = position.endsWith("Right");
         boolean bottom = position.startsWith("Bottom");
         int y = bottom ? height - 6 - lines.size() * 11 - 10 : 38;

         for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int x = right ? width - 4 - font.width(line) : 4;
            if (!right && !bottom) {
               y = Math.max(y, 38);
            }

            graphics.text(font, line, x, y + i * 11, colors.get(i), shadow);
         }
      }
   }

   private void drawEspTags(GuiGraphicsExtractor graphics, Minecraft client, int width, boolean shadow) {
      EspModule esp = this.modules.byClass(EspModule.class).orElse(null);
      if (esp != null && esp.isEnabled() && esp.tags() && client.level != null) {
         List<Entity> shown = new ArrayList<>();

         for (Entity entity : client.level.entitiesForRendering()) {
            if (esp.shouldGlow(entity) && !(entity instanceof ItemEntity)) {
               shown.add(entity);
            }
         }

         shown.sort(Comparator.comparingDouble(entityxx -> entityxx.distanceToSqr(client.player)));
         int y = 34;
         int x = width / 2 + 90;

         for (int i = 0; i < Math.min(shown.size(), esp.maxTags()); i++) {
            Entity entityx = shown.get(i);
            String label = entityx.getName().getString() + "  " + (int)entityx.distanceTo(client.player) + "m";
            int color = 0xFF000000 | esp.color(entityx);
            graphics.text(client.font, label, x, y, color, shadow);
            y += 11;
         }

         if (!shown.isEmpty()) {
            graphics.text(client.font, "ESP", x, 24, -6641995, false);
         }
      }
   }

   private void drawStorage(GuiGraphicsExtractor graphics, Minecraft client, int accent, int width, int height, boolean shadow) {
      StorageEspModule storage = this.modules.byClass(StorageEspModule.class).orElse(null);
      if (storage != null && storage.isEnabled() && !storage.hits().isEmpty()) {
         int y = height / 2 - 40;
         String title = "Storage";
         graphics.text(client.font, title, width - 6 - client.font.width(title), y, accent, shadow);
         y += 11;

         for (StorageEspModule.Hit hit : storage.hits()) {
            double dx = hit.pos().getX() + 0.5 - client.player.getX();
            double dy = hit.pos().getY() + 0.5 - client.player.getY();
            double dz = hit.pos().getZ() + 0.5 - client.player.getZ();
            String label = hit.label() + "  " + (int)Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
            graphics.text(client.font, label, width - 6 - client.font.width(label), y, -2761496, shadow);
            y += 11;
            if (y > height - 40) {
               break;
            }
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

   private record Toast(String title, String body, boolean enabled, long createdAt, long durationMs) {
   }
}
