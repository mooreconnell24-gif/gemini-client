package com.inferno.fabric.client;

import com.inferno.fabric.Alerts;
import com.inferno.fabric.InfernoConfig;
import com.inferno.fabric.InfernoMod;
import com.inferno.fabric.SecurityProfile;
import com.inferno.fabric.admin.AdminRank;
import com.inferno.fabric.admin.AdminService;
import com.inferno.fabric.admin.BanList;
import com.inferno.fabric.admin.ExemptStore;
import com.inferno.fabric.admin.OpAuth;
import com.inferno.fabric.checks.Check;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PanelScreen extends Screen {
   private static final Identifier LOGO = Identifier.fromNamespaceAndPath("inferno", "textures/gui/logo.png");
   private int W = 340;
   private int H = 236;
   private static final int HEADER = 34;
   private static final String[] TABS = new String[]{"Alerts", "Checks", "Players", "Admin", "Settings"};
   private static final int BG = -267122937;
   private static final int PANEL = -14808312;
   private static final int ROW = -14020597;
   private static final int ROW_HOVER = -12970482;
   private static final int ORANGE = -34273;
   private static final int RED = -2084324;
   private static final int GREEN = -11546278;
   private static final int GRAY = -7701900;
   private static final int WHITE = -1;
   private static int tab;
   private static PanelScreen current;
   private static String expanded = "";
   private static String alertFilter = "";
   private long copiedAt;
   private int x;
   private int y;
   private boolean dragging;
   private int dragOffsetX;
   private int dragOffsetY;
   private int mouseX;
   private int mouseY;
   private double scroll;
   private int contentHeight;
   private final List<PanelScreen.Button> buttons = new ArrayList<>();
   private long openAt = System.currentTimeMillis();
   private float openAnim;
   private float tabAnim;
   private int lastTab = -1;
   private String passwordBuf = "";
   private boolean passwordFocus;
   private String loginMsg = "";
   private long loginMsgAt;
   private long clickFlashAt;
   private int clickFlashX;
   private int clickFlashY;

   public static int[] size() {
      int[] nArray;
      if (current == null) {
         int[] nArray2 = new int[]{340, 0};
         nArray = nArray2;
         nArray2[1] = 236;
      } else {
         int[] nArray3 = new int[2];
         nArray3[0] = current.W;
         nArray = nArray3;
         nArray3[1] = current.H;
      }

      return nArray;
   }

   public static int[] position() {
      int[] nArray;
      if (current == null) {
         int[] nArray2 = new int[]{0, 0};
         nArray = nArray2;
         nArray2[1] = 0;
      } else {
         int[] nArray3 = new int[2];
         nArray3[0] = current.x;
         nArray = nArray3;
         nArray3[1] = current.y;
      }

      return nArray;
   }

   public static int currentTab() {
      return tab;
   }

   public PanelScreen() {
      super(Component.literal("Inferno Panel"));
      current = this;
   }

   protected void init() {
      InfernoConfig cfg = InfernoConfig.get();
      OpAuth.ensureDefaults(cfg);
      this.W = Math.max(280, Math.min(380, this.width - 8));
      this.H = Math.max(170, Math.min(260, this.height - 8));
      this.x = cfg.panelX < 0 ? (this.width - this.W) / 2 : cfg.panelX;
      this.y = cfg.panelY < 0 ? (this.height - this.H) / 2 : cfg.panelY;
      this.clamp();
      this.openAt = System.currentTimeMillis();
      this.openAnim = 0.0F;
      this.tabAnim = 1.0F;
      this.passwordBuf = "";
      this.passwordFocus = false;
      this.loginMsg = "";
   }

   private void clamp() {
      this.x = Math.max(0, Math.min(this.x, this.width - this.W));
      this.y = Math.max(0, Math.min(this.y, this.height - this.H));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, 1073741824);
   }

   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      boolean anim = InfernoConfig.get().panelAnimations;
      float t = Math.min(1.0F, (float)(System.currentTimeMillis() - this.openAt) / 280.0F);
      this.openAnim = anim ? easeOutBack(t) : 1.0F;
      if (this.lastTab != tab) {
         this.lastTab = tab;
         this.tabAnim = 0.0F;
      }

      this.tabAnim = Math.min(1.0F, this.tabAnim + (anim ? 0.12F : 1.0F));
      if (this.dragging) {
         this.x = mouseX - this.dragOffsetX;
         this.y = mouseY - this.dragOffsetY;
         this.clamp();
      }

      this.buttons.clear();
      int pulse = (int)(8.0 + 6.0 * Math.sin(System.currentTimeMillis() / 220.0));
      int border = anim ? 16742943 | 192 + pulse << 24 : -34273;
      ctx.fill(this.x + 4, this.y + 4, this.x + this.W + 4, this.y + this.H + 4, 1879048192);
      ctx.fill(this.x, this.y, this.x + this.W, this.y + this.H, -267122937);
      ctx.fillGradient(this.x, this.y, this.x + this.W, this.y + 34, -11922936, -15071483);
      int shim = (int)(System.currentTimeMillis() / 8L % Math.max(1, this.W - 20));
      ctx.fill(this.x + 6 + shim, this.y + 33, this.x + 26 + shim, this.y + 34, 1728031300);
      frame(ctx, this.x, this.y, this.W, this.H, border);
      ctx.fill(this.x + 1, this.y + 34, this.x + this.W - 1, this.y + 34 + 1, -5036530);
      ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, this.x + 6, this.y + 5, 0.0F, 0.0F, 24, 24, 24, 24);
      String selfUuid = this.minecraft.player != null ? this.minecraft.player.getStringUUID() : "";
      AdminRank rank = OpAuth.rankOf(selfUuid);
      ctx.text(this.font, "§6§lINFERNO §c§lANTI-CHEAT", this.x + 36, this.y + 7, -1, true);
      ctx.text(this.font, "§7" + rank.colored() + " §8| §7drag §8· §71-5 §8· §7Esc", this.x + 36, this.y + 19, -1, false);
      int cx = this.x + this.W - 18;
      boolean overClose = this.in(cx, this.y + 6, cx + 12, this.y + 18);
      ctx.fill(cx, this.y + 6, cx + 12, this.y + 18, overClose ? -2084324 : -10872304);
      ctx.text(this.font, "×", cx + 3, this.y + 8, -1, false);
      this.buttons.add(new PanelScreen.Button(cx, this.y + 6, cx + 12, this.y + 18, () -> this.onClose()));
      int tabW = (this.W - 12) / TABS.length;
      int i = 0;

      while (i < TABS.length) {
         int tx = this.x + 6 + i * tabW;
         int ty = this.y + 34 + 5;
         boolean active = tab == i;
         boolean hover = this.in(tx, ty, tx + tabW - 3, ty + 16);
         ctx.fill(tx, ty, tx + tabW - 3, ty + 16, active ? -5031406 : (hover ? -12970482 : -14020597));
         if (active) {
            ctx.fill(tx, ty + 15, tx + tabW - 3, ty + 16, -34273);
         }

         String label = TABS[i];
         int badge = i == 0 ? Alerts.recent().size() : 0;
         String shown = badge > 0 ? label + " §c" + Math.min(badge, 99) : label;
         int textW = this.font.width(shown);
         ctx.text(this.font, shown, tx + (tabW - 3 - textW) / 2, ty + 4, active ? -1 : -7701900, false);
         int index = i++;
         this.buttons.add(new PanelScreen.Button(tx, ty, tx + tabW - 3, ty + 16, () -> this.setTab(index)));
      }

      int top = this.y + 34 + 26;
      int bottom = this.y + this.H - 6;
      ctx.fill(this.x + 6, top - 2, this.x + this.W - 6, bottom, -14808312);
      ctx.enableScissor(this.x + 6, top - 2, this.x + this.W - 6, bottom);
      int start = top - (int)this.scroll;
      MinecraftServer server = InfernoMod.server;
      int end;
      if (server == null && tab != 4) {
         end = this.message(ctx, start, "§eOpen a single-player world to use the panel.", "§7Host LAN or use /infernoadmin on a dedicated Fabric server.");
      } else {
         switch (tab) {
            case 0:
               end = this.drawAlerts(ctx, start, top, bottom);
               break;
            case 1:
               end = this.drawChecks(ctx, start, top, bottom);
               break;
            case 2:
               end = this.drawPlayers(ctx, start, top, bottom, server);
               break;
            case 3:
               end = this.drawAdmin(ctx, start, top, bottom, server);
               break;
            default:
               end = this.drawSettings(ctx, start, top, bottom);
         }
      }

      ctx.disableScissor();
      this.contentHeight = end - start;
      int visible = bottom - top;
      if (this.contentHeight > visible) {
         int barH = Math.max(12, visible * visible / this.contentHeight);
         int barY = top + (int)((visible - barH) * (this.scroll / (this.contentHeight - visible)));
         ctx.fill(this.x + this.W - 9, barY, this.x + this.W - 7, barY + barH, -34273);
      }

      if (System.currentTimeMillis() - this.clickFlashAt < 180L) {
         ctx.fill(this.clickFlashX - 6, this.clickFlashY - 6, this.clickFlashX + 6, this.clickFlashY + 6, 1728031283);
      }
   }

   private ServerPlayer localPlayer() {
      MinecraftServer server = InfernoMod.server;
      return server != null && this.minecraft.player != null ? server.getPlayerList().getPlayer(this.minecraft.player.getUUID()) : null;
   }

   private static float easeOutBack(float x) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float t = x - 1.0F;
      return 1.0F + c3 * t * t * t + c1 * t * t;
   }

   private int message(GuiGraphicsExtractor ctx, int yy, String line1, String line2) {
      ctx.text(this.font, line1, this.x + 14, yy + 8, -1, false);
      ctx.text(this.font, line2, this.x + 14, yy + 20, -1, false);
      return yy + 34;
   }

   private int statusRow(GuiGraphicsExtractor ctx, int yy, int top, int bottom) {
      if (yy + 16 > top && yy < bottom) {
         String reason = null;
         boolean known = false;
         MinecraftServer server = InfernoMod.server;
         ServerPlayer self;
         if (server != null && this.minecraft.player != null && (self = server.getPlayerList().getPlayer(this.minecraft.player.getUUID())) != null) {
            known = true;
            reason = InfernoMod.ENGINE.notCheckedReason(self);
         }

         String text = !known
            ? "§7Checking your world"
            : (reason == null ? "§a● Checking you §8- cheats you turn on will show up here" : "§e● Paused for you: §f" + reason);
         ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 14, -14020597);
         ctx.text(this.font, this.trim(text, this.W - 36), this.x + 14, yy + 3, -1, false);
      }

      return yy + 18;
   }

   private int drawAlerts(GuiGraphicsExtractor ctx, int yy, int top, int bottom) {
      List<Alerts.Alert> all = Alerts.recent();
      yy = this.statusRow(ctx, yy, top, bottom);
      if (all.isEmpty()) {
         return this.message(ctx, yy, "§a✔ No alerts yet.", "§7Cheats that get caught will show up here.");
      }

      ArrayList<String> names = new ArrayList<>();

      for (Alerts.Alert alert : all) {
         if (!names.contains(alert.player)) {
            names.add(alert.player);
         }
      }

      if (!alertFilter.isEmpty() && !names.contains(alertFilter)) {
         alertFilter = "";
      }

      ArrayList<Alerts.Alert> alerts = new ArrayList<>();

      for (Alerts.Alert a : all) {
         if (alertFilter.isEmpty() || a.player.equals(alertFilter)) {
            alerts.add(a);
         }
      }

      if (yy + 16 > top && yy < bottom) {
         String string = "§7Showing: §f" + (alertFilter.isEmpty() ? "All players" : alertFilter) + " §8▼";
         int fw = this.font.width(string) + 12;
         boolean overFilter = this.in(this.x + 8, yy, this.x + 8 + fw, yy + 14);
         ctx.fill(this.x + 8, yy, this.x + 8 + fw, yy + 14, overFilter ? -12970482 : -14020597);
         ctx.text(this.font, string, this.x + 14, yy + 3, -1, false);
         if (yy >= top) {
            this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + 8 + fw, yy + 14, () -> {
               int i = names.indexOf(alertFilter);
               alertFilter = i + 1 < names.size() ? names.get(i + 1) : "";
               this.scroll = 0.0;
            }));
         }

         boolean copied = System.currentTimeMillis() - this.copiedAt < 2000L;
         String copy = copied ? "§a✔ Copied" : "Copy report";
         int cw = this.font.width("Copy report") + 12;
         int cx = this.x + this.W - 12 - cw;
         int clw = this.font.width("Clear") + 12;
         int clx = cx - 6 - clw;
         boolean overClear = this.in(clx, yy, clx + clw, yy + 14);
         ctx.fill(clx, yy, clx + clw, yy + 14, overClear ? -8771052 : -11920880);
         ctx.text(this.font, "Clear", clx + 6, yy + 3, -1, false);
         if (yy >= top) {
            this.buttons.add(new PanelScreen.Button(clx, yy, clx + clw, yy + 14, Alerts::clearLog));
         }

         ctx.fill(cx, yy, cx + cw, yy + 14, this.in(cx, yy, cx + cw, yy + 14) ? -8771052 : -11920880);
         ctx.text(this.font, copy, cx + 6, yy + 3, -1, false);
         if (yy >= top) {
            this.buttons.add(new PanelScreen.Button(cx, yy, cx + cw, yy + 14, () -> {
               this.minecraft.keyboardHandler.setClipboard(this.report(alerts));
               this.copiedAt = System.currentTimeMillis();
            }));
         }
      }

      yy += 18;

      for (Alerts.Alert a : alerts) {
         if (yy + 24 > top - 30 && yy < bottom) {
            boolean rowHover = this.in(this.x + 8, yy, this.x + this.W - 12, yy + 22) && this.mouseY >= top && this.mouseY < bottom;
            ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 22, rowHover ? -12970482 : -14020597);
            if (rowHover && yy >= top) {
               String who = a.player;
               this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + this.W - 12, yy + 22, () -> {
                  alertFilter = alertFilter.equals(who) ? "" : who;
                  this.scroll = 0.0;
               }));
            }

            ctx.fill(this.x + 8, yy, this.x + 10, yy + 22, -2084324);
            ctx.text(this.font, "§c" + a.check + " §8• §f" + a.player, this.x + 14, yy + 3, -1, false);
            String right = "§cVL " + a.vl + " §8" + ago(a.time);
            ctx.text(this.font, right, this.x + this.W - 16 - this.font.width(right), yy + 3, -1, false);
            ctx.text(this.font, "§7" + this.trim(a.detail, this.W - 40), this.x + 14, yy + 13, -1, false);
         }

         yy += 24;
      }

      return yy;
   }

   private int drawChecks(GuiGraphicsExtractor ctx, int yy, int top, int bottom) {
      InfernoConfig cfg = InfernoConfig.get();
      int total = InfernoMod.ENGINE.all().size();
      int enabled = 0;

      for (Check c : InfernoMod.ENGINE.all()) {
         enabled += c.enabled() ? 1 : 0;
      }

      if (yy + 16 > top && yy < bottom) {
         ctx.text(this.font, "§7" + enabled + " of " + total + " on", this.x + 12, yy + 3, -1, false);
         String[] labels = new String[]{"All on", "All off"};
         int bx = this.x + this.W - 12;

         for (int i = labels.length - 1; i >= 0; i--) {
            int bw = this.font.width(labels[i]) + 12;
            bx -= bw;
            boolean over = this.in(bx, yy, bx + bw, yy + 14);
            ctx.fill(bx, yy, bx + bw, yy + 14, over ? -8771052 : -11920880);
            ctx.text(this.font, labels[i], bx + 6, yy + 3, -1, false);
            boolean on = i == 0;
            if (yy >= top) {
               this.buttons.add(new PanelScreen.Button(bx, yy, bx + bw, yy + 14, () -> setChecks(cfg, null, on)));
            }

            bx -= 6;
         }
      }

      yy += 18;
      Check.Category current = null;

      for (Check c : InfernoMod.ENGINE.all()) {
         if (c.category() != current) {
            Check.Category cat = current = c.category();
            if (yy > top - 20 && yy < bottom) {
               boolean over = this.in(this.x + 8, yy, this.x + this.W - 12, yy + 14) && this.mouseY >= top && this.mouseY < bottom;
               if (over) {
                  ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 14, -12970482);
               }

               ctx.text(this.font, "§6§l" + current.name() + (over ? " §8- click to switch this whole group" : ""), this.x + 12, yy + 4, -1, false);
               if (yy >= top) {
                  this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + this.W - 12, yy + 14, () -> {
                     boolean anyOff = false;

                     for (Check k : InfernoMod.ENGINE.all()) {
                        anyOff |= k.category() == cat && !k.enabled();
                     }

                     setChecks(cfg, cat, anyOff);
                  }));
               }
            }

            yy += 16;
         }

         boolean on = c.enabled();
         int rowTop = yy;
         if (yy + 20 > top - 20 && yy < bottom) {
            boolean hover = this.in(this.x + 8, yy, this.x + this.W - 12, yy + 18) && this.mouseY >= top && this.mouseY < bottom;
            ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 18, hover ? -12970482 : -14020597);
            ctx.text(this.font, (on ? "§f" : "§8") + c.displayName(), this.x + 14, yy + 5, -1, false);
            int sx = this.x + this.W - 44;
            ctx.fill(sx, yy + 4, sx + 26, yy + 14, on ? -13730510 : -11912650);
            ctx.fill(on ? sx + 14 : sx + 2, yy + 5, on ? sx + 25 : sx + 13, yy + 13, on ? -11546278 : -7701900);
            if (hover) {
               ctx.text(this.font, "§8" + this.trim(c.description(), 150), this.x + 120, yy + 5, -1, false);
            } else {
               int flags = Alerts.flagCount(c.id());
               if (flags > 0) {
                  String count = "§c" + flags + (flags == 1 ? " flag" : " flags");
                  ctx.text(this.font, count, this.x + this.W - 52 - this.font.width(count), yy + 5, -1, false);
               }
            }
         }

         if (rowTop >= top - 18 && rowTop < bottom - 4) {
            this.buttons
               .add(
                  new PanelScreen.Button(
                     this.x + 8, Math.max(rowTop, top), this.x + this.W - 12, Math.min(rowTop + 18, bottom), () -> InfernoConfig.get().toggle(c.id())
                  )
               );
         }

         yy += 20;
      }

      return yy;
   }

   private static void setChecks(InfernoConfig cfg, Check.Category category, boolean on) {
      for (Check c : InfernoMod.ENGINE.all()) {
         if (category == null || c.category() == category) {
            cfg.checks.put(c.id(), on);
         }
      }

      cfg.save();
   }

   private int drawPlayers(GuiGraphicsExtractor ctx, int yy, int top, int bottom, MinecraftServer server) {
      for (ServerPlayer p : new ArrayList<ServerPlayer>(server.getPlayerList().getPlayers())) {
         String uuid = p.getStringUUID();
         int s = Alerts.suspicion(uuid);
         int color = s == 0 ? -11546278 : (s < 25 ? -8118 : (s < 50 ? -34273 : -2084324));
         if (yy + 34 > top - 30 && yy < bottom) {
            ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 32, -14020597);
            ctx.fill(this.x + 8, yy, this.x + 10, yy + 32, color);
            String you = uuid.equals(Alerts.hostUuid) ? " §8(you)" : "";
            ctx.text(this.font, "§f§l" + p.getName().getString() + you, this.x + 14, yy + 4, -1, false);
            int bx = this.x + 14;
            int bw = 120;
            ctx.fill(bx, yy + 17, bx + bw, yy + 23, -12966880);
            ctx.fill(bx, yy + 17, bx + bw * s / 100, yy + 23, color);
            ctx.text(this.font, s + "% " + label(s), bx + bw + 6, yy + 16, color, false);
            String worst = worst(uuid);
            if (worst != null) {
               ctx.text(this.font, "§7" + worst, this.x + this.W - 16 - this.font.width(worst), yy + 4, -1, false);
            }

            int n2 = this.x + this.W - 56;
            boolean hover = this.in(n2, yy + 15, n2 + 40, yy + 27);
            ctx.fill(n2, yy + 15, n2 + 40, yy + 27, hover ? -8771052 : -11920880);
            ctx.text(this.font, "Reset", n2 + 8, yy + 17, -1, false);
            if (yy >= top && yy + 32 <= bottom) {
               this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + this.W - 12, yy + 32, () -> expanded = expanded.equals(uuid) ? "" : uuid));
            }

            if (yy + 15 >= top && yy + 27 <= bottom) {
               this.buttons.add(new PanelScreen.Button(n2, yy + 15, n2 + 40, yy + 27, () -> Alerts.clear(uuid)));
            }

            ctx.text(this.font, expanded.equals(uuid) ? "§8▲" : "§8▼", this.x + this.W - 22, yy + 24, -1, false);
         }

         yy += 36;
         if (expanded.equals(uuid)) {
            if (yy > top - 12 && yy < bottom) {
               double k = InfernoMod.ENGINE.mitigation(uuid);
               String live = "§7Live: §f"
                  + String.format(Locale.ROOT, "%.2f", InfernoMod.ENGINE.state(p).lastHorizontal)
                  + " b/t §8• §7combat "
                  + InfernoMod.ENGINE.combatSuspicion(uuid)
                  + "%"
                  + (k > 0.0 ? " §8• §emitigated: " + Math.round(k * 50.0) + "% of hits miss" : "");
               ctx.text(this.font, live, this.x + 18, yy, -1, false);
            }

            yy += 12;
            Map<String, Integer> vls = Alerts.violations(uuid);
            if (vls.isEmpty()) {
               if (yy > top - 12 && yy < bottom) {
                  ctx.text(this.font, "§a✔ No violations", this.x + 18, yy, -1, false);
               }

               yy += 12;
            } else {
               ArrayList<Entry<String, Integer>> sorted = new ArrayList<>(vls.entrySet());
               sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
               int limit = Math.max(1, InfernoConfig.get().kickVl);

               for (Entry entry : sorted) {
                  if (yy > top - 12 && yy < bottom) {
                     String name = (String)entry.getKey();

                     for (Check c : InfernoMod.ENGINE.all()) {
                        if (c.id().equals(entry.getKey())) {
                           name = c.displayName();
                        }
                     }

                     int barW = 60 * Math.min((Integer)entry.getValue(), limit) / limit;
                     ctx.fill(this.x + 18, yy + 2, this.x + 78, yy + 7, -12966880);
                     ctx.fill(this.x + 18, yy + 2, this.x + 18 + barW, yy + 7, (Integer)entry.getValue() * 2 >= limit ? -2084324 : -34273);
                     ctx.text(this.font, "§f" + name + " §cVL " + entry.getValue() + "§8/" + limit, this.x + 84, yy, -1, false);
                  }

                  yy += 11;
               }
            }

            yy += 6;
         }
      }

      return yy;
   }

   private int drawAdmin(GuiGraphicsExtractor ctx, int yy, int top, int bottom, MinecraftServer server) {
      if (server == null) {
         return this.message(ctx, yy, "§eNo local server.", "§7Open a world to manage players.");
      }

      ServerPlayer self = this.localPlayer();
      String selfId = self != null ? self.getStringUUID() : (this.minecraft.player != null ? this.minecraft.player.getStringUUID() : "");
      AdminRank rank = OpAuth.rankOf(selfId);
      if (!rank.atLeast(AdminRank.HELPER)) {
         if (yy + 14 > top && yy < bottom) {
            ctx.text(this.font, "§6§lOP Login", this.x + 12, yy + 2, -1, false);
         }

         yy += 16;
         if (yy + 18 > top && yy < bottom) {
            boolean focus = this.passwordFocus;
            boolean blink = System.currentTimeMillis() / 400L % 2L == 0L;
            String dots = "*".repeat(Math.min(24, this.passwordBuf.length())) + (focus && blink ? "_" : "");
            ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 18, focus ? -12970482 : -14020597);
            frame(ctx, this.x + 8, yy, this.W - 20, 18, focus ? -34273 : -7701900);
            ctx.text(this.font, "§7Pass: §f" + (dots.isEmpty() ? "§8click & type…" : dots), this.x + 14, yy + 5, -1, false);
            this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + this.W - 12, yy + 18, () -> this.passwordFocus = true));
         }

         yy += 22;
         if (yy + 18 > top && yy < bottom) {
            int bx = this.x + 8;
            bx = this.adminBtn(ctx, bx, yy, "Login", this::submitPassword);
            bx = this.adminBtn(ctx, bx, yy, "Clear", () -> {
               this.passwordBuf = "";
               this.loginMsg = "";
            });
            this.adminBtn(ctx, bx, yy, "Hint", () -> {
               this.loginMsg = "§7Owner password is set in config (default §f4566§7)";
               this.loginMsgAt = System.currentTimeMillis();
            });
         }

         yy += 22;
         if (!this.loginMsg.isEmpty() && System.currentTimeMillis() - this.loginMsgAt < 4000L && yy + 14 > top && yy < bottom) {
            ctx.text(this.font, this.loginMsg, this.x + 12, yy + 2, -1, false);
            yy += 14;
         }

         yy += 4;
         return this.message(ctx, yy, "§eEnter OP password for Owner/Admin access.", "§7Or use §f/inferno login <pass>§7 — Owner: §f4566");
      } else {
         InfernoConfig cfg = InfernoConfig.get();
         if (yy + 16 > top && yy < bottom) {
            ctx.text(this.font, "§6Control Center  " + rank.colored() + " §8· /infernoadmin", this.x + 12, yy + 3, -1, false);
         }

         yy += 16;
         if (yy + 18 > top && yy < bottom) {
            int bx = this.x + 8;
            bx = this.adminBtn(ctx, bx, yy, "Punish: " + AdminService.punishMode(), () -> {
               String cur = AdminService.punishMode();
               AdminService.setPunishMode("none".equals(cur) ? "kick" : ("kick".equals(cur) ? "ban" : "none"));
            });
            bx = this.adminBtn(ctx, bx, yy, "MAX", () -> SecurityProfile.applyMaximum(cfg));
            bx = this.adminBtn(ctx, bx, yy, "Unban all", () -> {
               if (rank.atLeast(AdminRank.OWNER)) {
                  for (BanList.BanEntry e : new ArrayList<>(BanList.all())) {
                     BanList.unban(e.uuid);
                  }

                  this.loginMsg = "§aCleared all bans";
                  this.loginMsgAt = System.currentTimeMillis();
               } else {
                  this.loginMsg = "§cOwner only";
                  this.loginMsgAt = System.currentTimeMillis();
               }
            });
            this.adminBtn(ctx, bx, yy, "Logout", () -> {
               OpAuth.logout(selfId);
               this.passwordBuf = "";
               this.loginMsg = "§7Logged out";
               this.loginMsgAt = System.currentTimeMillis();
            });
         }

         yy += 22;
         if (!this.loginMsg.isEmpty() && System.currentTimeMillis() - this.loginMsgAt < 3500L) {
            if (yy + 12 > top && yy < bottom) {
               ctx.text(this.font, this.loginMsg, this.x + 12, yy, -1, false);
            }

            yy += 14;
         }

         if (yy + 14 > top && yy < bottom) {
            ctx.text(
               this.font, "§8Bans: §f" + BanList.all().size() + " §8· Online: §f" + server.getPlayerList().getPlayers().size(), this.x + 12, yy + 2, -1, false
            );
         }

         yy += 16;

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String uuid = p.getStringUUID();
            String name = p.getName().getString();
            int slide = InfernoConfig.get().panelAnimations ? (int)((1.0F - this.tabAnim) * 10.0F) : 0;
            if (yy + 46 > top - 20 && yy < bottom) {
               boolean hot = this.in(this.x + 8, yy, this.x + this.W - 12, yy + 44);
               ctx.fill(this.x + 8 + slide, yy, this.x + this.W - 12, yy + 44, hot ? -12970482 : -14020597);
               ctx.fill(this.x + 8 + slide, yy, this.x + 10 + slide, yy + 44, Alerts.suspicion(uuid) >= 50 ? -2084324 : -34273);
               String tags = (AdminService.isFrozen(uuid) ? " §bFROZEN" : "")
                  + (ExemptStore.isExempt(uuid) ? " §aEXEMPT" : "")
                  + (uuid.equals(selfId) ? " §8(you)" : "");
               ctx.text(this.font, "§f§l" + name + tags + " §8" + Alerts.suspicion(uuid) + "%", this.x + 14 + slide, yy + 4, -1, false);
               int bx = this.x + 12 + slide;
               int by = yy + 18;
               if (rank.atLeast(AdminRank.MOD)) {
                  bx = this.adminBtn(ctx, bx, by, "Kick", () -> AdminService.kick(p, "Kicked by " + rank.label()));
               }

               if (rank.atLeast(AdminRank.ADMIN)) {
                  bx = this.adminBtn(ctx, bx, by, "Ban", () -> AdminService.ban(p, "Banned by " + rank.label(), rank.label(), cfg.defaultBanMinutes));
               }

               bx = this.adminBtn(ctx, bx, by, AdminService.isFrozen(uuid) ? "Unfreeze" : "Freeze", () -> AdminService.freeze(p, !AdminService.isFrozen(uuid)));
               bx = this.adminBtn(ctx, bx, by, ExemptStore.isExempt(uuid) ? "Unexempt" : "Exempt", () -> {
                  if (ExemptStore.isExempt(uuid)) {
                     AdminService.unexempt(p);
                  } else {
                     AdminService.exempt(p, 0L);
                  }
               });
               bx = this.adminBtn(ctx, bx, by, "TP", () -> {
                  if (self != null) {
                     AdminService.teleportTo(self, p);
                  }
               });
               bx = this.adminBtn(ctx, bx, by, "VL", () -> AdminService.clearVl(uuid));
               this.adminBtn(ctx, bx, by, "Spec", () -> {
                  if (self != null) {
                     AdminService.spectate(self, p);
                  }
               });
            }

            yy += 48;
         }

         return yy;
      }
   }

   private void submitPassword() {
      String id = this.minecraft.player != null ? this.minecraft.player.getStringUUID() : "";
      AdminRank r = OpAuth.tryLogin(id, this.passwordBuf);
      if (r.atLeast(AdminRank.HELPER)) {
         this.loginMsg = "§aLogged in as " + r.colored();
         this.passwordBuf = "";
         this.passwordFocus = false;
      } else {
         this.loginMsg = "§cWrong password";
      }

      this.loginMsgAt = System.currentTimeMillis();
   }

   private int adminBtn(GuiGraphicsExtractor ctx, int bx, int by, String label, Runnable action) {
      int bw = this.font.width(label) + 10;
      boolean hover = this.in(bx, by, bx + bw, by + 14);
      int bg = hover ? -8771052 : -11920880;
      if (hover && InfernoConfig.get().panelAnimations) {
         ctx.fill(bx - 1, by - 1, bx + bw + 1, by + 15, 1157605939);
      }

      ctx.fill(bx, by, bx + bw, by + 14, bg);
      ctx.text(this.font, label, bx + 5, by + 3, -1, false);
      this.buttons.add(new PanelScreen.Button(bx, by, bx + bw, by + 14, () -> {
         this.clickFlashAt = System.currentTimeMillis();
         this.clickFlashX = bx + bw / 2;
         this.clickFlashY = by + 7;
         action.run();
      }));
      return bx + bw + 4;
   }

   private int drawSettings(GuiGraphicsExtractor ctx, int yy, int top, int bottom) {
      InfernoConfig cfg = InfernoConfig.get();
      yy = this.toggle(ctx, yy, top, bottom, "Alerts in chat", "Show cheat alerts in your chat", cfg.chatAlerts, () -> cfg.chatAlerts = !cfg.chatAlerts);
      yy = this.toggle(ctx, yy, top, bottom, "Pull cheaters back", "Undo fly / speed / step moves", cfg.setbacks, () -> cfg.setbacks = !cfg.setbacks);
      yy = this.toggle(
         ctx, yy, top, bottom, "Hard lock", "Glue cheaters in place until they land clean (anti-fly)", cfg.hardLock, () -> cfg.hardLock = !cfg.hardLock
      );
      yy = this.toggle(
         ctx, yy, top, bottom, "Strict mode", "Tighter limits / shorter exempts (LiquidBounce etc.)", cfg.strictMode, () -> cfg.strictMode = !cfg.strictMode
      );
      yy = this.toggle(
         ctx, yy, top, bottom, "Block cheated hits", "Cancel hits that fail reach / aura checks", cfg.cancelHits, () -> cfg.cancelHits = !cfg.cancelHits
      );
      yy = this.toggle(
         ctx, yy, top, bottom, "Block cheated blocks", "Cancel scaffold / nuker / far placing", cfg.cancelBlocks, () -> cfg.cancelBlocks = !cfg.cancelBlocks
      );
      yy = this.toggle(
         ctx,
         yy,
         top,
         bottom,
         "Silent mitigation",
         "Suspected cheaters' hits quietly miss",
         cfg.combatMitigation,
         () -> cfg.combatMitigation = !cfg.combatMitigation
      );
      yy = this.toggle(
         ctx, yy, top, bottom, "Warn offenders", "Warn on screen at 50% and 80% of the kick limit", cfg.warnPlayers, () -> cfg.warnPlayers = !cfg.warnPlayers
      );
      yy = this.toggle(
         ctx,
         yy,
         top,
         bottom,
         "Kick LAN cheaters",
         "Kick friends on your LAN world (never you)",
         cfg.kickLanCheaters,
         () -> cfg.kickLanCheaters = !cfg.kickLanCheaters
      );
      yy = this.toggle(
         ctx, yy, top, bottom, "Ban at kick VL", "Also ban when VL hits the kick threshold", cfg.banOnKickVl, () -> cfg.banOnKickVl = !cfg.banOnKickVl
      );
      yy = this.toggle(
         ctx,
         yy,
         top,
         bottom,
         "Persist VL on quit",
         "Keep violation levels after reconnect",
         cfg.persistVlOnQuit,
         () -> cfg.persistVlOnQuit = !cfg.persistVlOnQuit
      );
      yy = this.toggle(
         ctx,
         yy,
         top,
         bottom,
         "Panel animations",
         "Shimmer, pulse, tab slide, click flash",
         cfg.panelAnimations,
         () -> cfg.panelAnimations = !cfg.panelAnimations
      );
      if ((
                  yy = this.toggle(
                     ctx, yy, top, bottom, "Debug HUD", "Your live speed, air time and last flag on screen", cfg.debugHud, () -> cfg.debugHud = !cfg.debugHud
                  )
               )
               + 20
            > top
         && yy < bottom) {
         ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 18, -14020597);
         ctx.text(this.font, "§fKick / 100% suspicion at VL §6" + cfg.kickVl, this.x + 14, yy + 5, -1, false);
         int mx = this.x + this.W - 58;
         ctx.fill(mx, yy + 3, mx + 16, yy + 15, -11920880);
         ctx.text(this.font, "-", mx + 6, yy + 5, -1, false);
         ctx.fill(mx + 22, yy + 3, mx + 38, yy + 15, -11920880);
         ctx.text(this.font, "+", mx + 27, yy + 5, -1, false);
         this.buttons.add(new PanelScreen.Button(mx, yy + 3, mx + 16, yy + 15, () -> {
            cfg.kickVl = Math.max(1, cfg.kickVl - 1);
            cfg.save();
         }));
         this.buttons.add(new PanelScreen.Button(mx + 22, yy + 3, mx + 38, yy + 15, () -> {
            cfg.kickVl = Math.min(200, cfg.kickVl + 1);
            cfg.save();
         }));
      }

      yy += 20;
      if (yy + 14 > top && yy < bottom) {
         ctx.text(this.font, "§6Check limits §8- lower = stricter, but more false flags", this.x + 12, yy + 3, -1, false);
      }

      yy += 14;

      for (Entry<String, Double> limit : new ArrayList<>(cfg.limits.entrySet())) {
         yy = this.limitRow(ctx, yy, top, bottom, cfg, limit.getKey(), limit.getValue());
      }

      yy += 4;
      if (yy + 20 > top && yy < bottom) {
         String[] labels = new String[]{"MAX SECURITY", "Send test alert", "Reset limits", "Clear alerts"};
         Runnable[] actions = new Runnable[]{() -> SecurityProfile.applyMaximum(cfg), Alerts::test, () -> {
            for (String key : new ArrayList<>(cfg.limits.keySet())) {
               double def = InfernoConfig.defaultLimit(key);
               if (def > 0.0) {
                  cfg.limits.put(key, def);
               }
            }

            cfg.save();
         }, Alerts::clearLog};
         int bx = this.x + 8;

         for (int i = 0; i < labels.length; i++) {
            int bw;
            boolean hover = this.in(bx, yy, bx + (bw = this.font.width(labels[i]) + 16), yy + 18);
            ctx.fill(bx, yy, bx + bw, yy + 18, hover ? -8771052 : -11920880);
            ctx.text(this.font, labels[i], bx + 8, yy + 5, -1, false);
            if (yy >= top && yy + 18 <= bottom) {
               this.buttons.add(new PanelScreen.Button(bx, yy, bx + bw, yy + 18, actions[i]));
            }

            bx += bw + 6;
         }
      }

      int var10003 = this.x + 12;
      yy += 24;
      ctx.text(this.font, "§8Settings are saved in config/inferno-anticheat.json", var10003, yy, -1, false);
      return yy + 14;
   }

   private int limitRow(GuiGraphicsExtractor ctx, int yy, int top, int bottom, InfernoConfig cfg, String key, double value) {
      double def = InfernoConfig.defaultLimit(key);
      double step = def < 2.0 ? 0.05 : (def < 10.0 ? 0.1 : (def < 100.0 ? 1.0 : 25.0));
      if (yy + 20 > top && yy < bottom) {
         boolean changed = def > 0.0 && Math.abs(value - def) > 1.0E-9;
         ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 18, -14020597);
         ctx.text(this.font, "§f" + limitName(key) + " §6" + format(value) + (changed ? " §8(def " + format(def) + ")" : ""), this.x + 14, yy + 5, -1, false);
         int mx = this.x + this.W - 80;
         ctx.fill(mx, yy + 3, mx + 16, yy + 15, -11920880);
         ctx.text(this.font, "-", mx + 6, yy + 5, -1, false);
         ctx.fill(mx + 22, yy + 3, mx + 38, yy + 15, -11920880);
         ctx.text(this.font, "+", mx + 27, yy + 5, -1, false);
         if (changed) {
            ctx.fill(mx + 44, yy + 3, mx + 60, yy + 15, -11920880);
            ctx.text(this.font, "↺", mx + 48, yy + 5, -1, false);
         }

         if (yy >= top && yy + 18 <= bottom) {
            double max = def > 0.0 ? def * 10.0 : value * 10.0 + step;
            this.buttons.add(new PanelScreen.Button(mx, yy + 3, mx + 16, yy + 15, () -> setLimit(cfg, key, Math.max(step, value - step))));
            this.buttons.add(new PanelScreen.Button(mx + 22, yy + 3, mx + 38, yy + 15, () -> setLimit(cfg, key, Math.min(max, value + step))));
            if (changed) {
               this.buttons.add(new PanelScreen.Button(mx + 44, yy + 3, mx + 60, yy + 15, () -> setLimit(cfg, key, def)));
            }
         }
      }

      return yy + 20;
   }

   private static void setLimit(InfernoConfig cfg, String key, double value) {
      cfg.limits.put(key, Math.round(value * 1000.0) / 1000.0);
      cfg.save();
   }

   private static String format(double v) {
      return v == Math.rint(v) ? String.valueOf((long)v) : String.format(Locale.ROOT, "%.2f", v);
   }

   private static String limitName(String key) {
      return switch (key) {
         case "reach.max-reach" -> "Reach: max distance";
         case "speed.max-speed" -> "Speed: max per move";
         case "speed-average.max-average" -> "Speed (Average): max";
         case "fly.max-air-ms" -> "Fly: max air time (ms)";
         case "timer.max-ahead-ms" -> "Timer: max ahead (ms)";
         case "autoclicker.max-cps" -> "AutoClicker: max clicks/s";
         case "fastplace.max-per-second" -> "FastPlace: max blocks/s";
         case "nuker.max-per-second" -> "Nuker: max blocks/s";
         case "block-reach.max-distance" -> "Reach (Blocks): max distance";
         case "autofish.max-reaction-ms" -> "AutoFish: max reaction (ms)";
         case "aim-smooth.max-variance" -> "Aim Smooth: max variance";
         case "regen.min-interval-ms" -> "Regen: min interval (ms)";
         case "fastuse.min-ticks" -> "Fast Use: min ticks";
         case "strafe.max-air-accel" -> "Strafe: max air accel";
         case "blink.max-gap-ms" -> "Blink: max packet gap (ms)";
         case "boat-fly.max-air-ms" -> "Boat Fly: max air (ms)";
         case "elytra-speed.max" -> "Elytra Speed: max";
         case "invalid-motion.max" -> "Invalid Motion: max delta";
         case "fastbreak.min-ms" -> "Fast Break: min time (ms)";
         case "inventory-clean.max-cps" -> "Inventory Clean: max cps";
         case "multi-angle.max-deg" -> "Multi Angle: max degrees";
         default -> key;
      };
   }

   private int toggle(GuiGraphicsExtractor ctx, int yy, int top, int bottom, String name, String help, boolean on, Runnable flip) {
      if (yy + 20 > top && yy < bottom) {
         boolean hover = this.in(this.x + 8, yy, this.x + this.W - 12, yy + 18);
         ctx.fill(this.x + 8, yy, this.x + this.W - 12, yy + 18, hover ? -12970482 : -14020597);
         ctx.text(this.font, "§f" + name + (hover ? " §8- " + help : ""), this.x + 14, yy + 5, -1, false);
         int sx = this.x + this.W - 44;
         ctx.fill(sx, yy + 4, sx + 26, yy + 14, on ? -13730510 : -11912650);
         ctx.fill(on ? sx + 14 : sx + 2, yy + 5, on ? sx + 25 : sx + 13, yy + 13, on ? -11546278 : -7701900);
         if (yy >= top && yy + 18 <= bottom) {
            this.buttons.add(new PanelScreen.Button(this.x + 8, yy, this.x + this.W - 12, yy + 18, () -> {
               flip.run();
               InfernoConfig.get().save();
            }));
         }
      }

      return yy + 20;
   }

   private void setTab(int index) {
      tab = Math.floorMod(index, TABS.length);
      this.scroll = 0.0;
   }

   private void scrollBy(double amount) {
      int visible = this.y + this.H - 6 - (this.y + 34 + 26);
      this.scroll = Math.max(0.0, Math.min(Math.max(0, this.contentHeight - visible), this.scroll + amount));
   }

   public boolean charTyped(CharacterEvent event) {
      if (this.passwordFocus && tab == 3) {
         String ch = event.codepointAsString();
         if (ch != null && !ch.isEmpty() && this.passwordBuf.length() < 32) {
            this.passwordBuf = this.passwordBuf + ch;
            return true;
         }
      }

      return super.charTyped(event);
   }

   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      if (this.passwordFocus && tab == 3) {
         if (key == 259 || key == 14) {
            if (!this.passwordBuf.isEmpty()) {
               this.passwordBuf = this.passwordBuf.substring(0, this.passwordBuf.length() - 1);
            }

            return true;
         }

         if (key == 257 || key == 335 || key == 28) {
            this.submitPassword();
            return true;
         }
      }

      boolean shift = (event.modifiers() & 1) != 0;
      int page = this.H - 34 - 40;
      if (key >= 30 && key <= 30 + TABS.length - 1) {
         this.setTab(key - 30);
      } else if (key != 79 && (key != 43 || shift)) {
         if (key != 80 && (key != 43 || !shift)) {
            if (key == 81) {
               this.scrollBy(20.0);
            } else if (key == 82) {
               this.scrollBy(-20.0);
            } else if (key == 78) {
               this.scrollBy(page);
            } else if (key == 75) {
               this.scrollBy(-page);
            } else if (key == 74) {
               this.scroll = 0.0;
            } else {
               if (key != 77) {
                  return super.keyPressed(event);
               }

               this.scrollBy(1000000.0);
            }
         } else {
            this.setTab(tab - 1);
         }
      } else {
         this.setTab(tab + 1);
      }

      return true;
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() != 1) {
         return super.mouseClicked(click, doubled);
      }

      this.mouseX = (int)click.x();
      this.mouseY = (int)click.y();
      ArrayList<PanelScreen.Button> ordered = new ArrayList<>(this.buttons);

      for (int i = ordered.size() - 1; i >= 0; i--) {
         PanelScreen.Button b = ordered.get(i);
         if (b.contains(this.mouseX, this.mouseY)) {
            b.action().run();
            return true;
         }
      }

      if (this.in(this.x, this.y, this.x + this.W, this.y + 34)) {
         this.dragging = true;
         this.dragOffsetX = this.mouseX - this.x;
         this.dragOffsetY = this.mouseY - this.y;
         return true;
      } else {
         return true;
      }
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.dragging) {
         this.dragging = false;
         InfernoConfig cfg = InfernoConfig.get();
         cfg.panelX = this.x;
         cfg.panelY = this.y;
         cfg.save();
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      return this.dragging || super.mouseDragged(click, offsetX, offsetY);
   }

   public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
      int visible = this.y + this.H - 6 - (this.y + 34 + 26);
      this.scroll = Math.max(0.0, Math.min(Math.max(0, this.contentHeight - visible), this.scroll - vertical * 16.0));
      return true;
   }

   private boolean in(int x1, int y1, int x2, int y2) {
      return this.mouseX >= x1 && this.mouseX < x2 && this.mouseY >= y1 && this.mouseY < y2;
   }

   private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
      ctx.fill(x, y, x + w, y + 1, color);
      ctx.fill(x, y + h - 1, x + w, y + h, -5036530);
      ctx.fill(x, y, x + 1, y + h, color);
      ctx.fill(x + w - 1, y, x + w, y + h, color);
   }

   private String trim(String s, int maxWidth) {
      if (this.font.width(s) <= maxWidth) {
         return s;
      }

      while (s.length() > 1 && this.font.width(s + "...") > maxWidth) {
         s = s.substring(0, s.length() - 1);
      }

      return s + "...";
   }

   private String report(List<Alerts.Alert> alerts) {
      StringBuilder b = new StringBuilder("Inferno Anti Cheat report - ").append(alertFilter.isEmpty() ? "all players" : alertFilter).append('\n');
      MinecraftServer server = InfernoMod.server;
      if (!alertFilter.isEmpty() && server != null) {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getName().getString().equals(alertFilter)) {
               String uuid = p.getStringUUID();
               b.append("Suspicion: ").append(Alerts.suspicion(uuid)).append("%\n");

               for (Entry<String, Integer> e : Alerts.violations(uuid).entrySet()) {
                  b.append("  ").append(e.getKey()).append(": VL ").append(e.getValue()).append('\n');
               }
            }
         }
      }

      b.append("Recent alerts (newest first):\n");
      int n = 0;

      for (Alerts.Alert a : alerts) {
         if (n++ >= 50) {
            break;
         }

         b.append("  ")
            .append(ago(a.time))
            .append(" | ")
            .append(a.player)
            .append(" | ")
            .append(a.check)
            .append(" | ")
            .append(a.detail)
            .append(" | VL ")
            .append(a.vl)
            .append('\n');
      }

      return b.toString();
   }

   private static String ago(long time) {
      long s = (System.currentTimeMillis() - time) / 1000L;
      return s < 60L ? s + "s ago" : (s < 3600L ? s / 60L + "m ago" : s / 3600L + "h ago");
   }

   private static String label(int s) {
      return s == 0 ? "Clean" : (s < 25 ? "Low" : (s < 50 ? "Medium" : (s < 75 ? "High" : "Very High")));
   }

   private static String worst(String uuid) {
      String best = null;
      int bestVl = 0;

      for (Entry<String, Integer> e : Alerts.violations(uuid).entrySet()) {
         if (e.getValue() > bestVl) {
            bestVl = e.getValue();
            best = e.getKey();
         }
      }

      if (best == null) {
         return null;
      }

      for (Check c : InfernoMod.ENGINE.all()) {
         if (c.id().equals(best)) {
            return c.displayName() + " VL " + bestVl;
         }
      }

      return best + " VL " + bestVl;
   }

   private record Button(int x1, int y1, int x2, int y2, Runnable action) {
      boolean contains(int mx, int my) {
         return mx >= this.x1 && mx < this.x2 && my >= this.y1 && my < this.y2;
      }
   }
}
