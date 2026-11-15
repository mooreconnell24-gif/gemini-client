package gemini.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import gemini.Gemini;
import gemini.diags.MixinReport;
import gemini.input.KeybindConflicts;
import gemini.input.KeybindReport;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.util.SingleplayerGate;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Gemini's home screen, shown in place of the vanilla title screen: an animated particle backdrop with mouse
 * parallax, glass navigation buttons, the sandbox switch and a live status bar.
 *
 * <p>The swap happens in {@link gemini.mixin.TitleScreenMixin} (every screen change goes through one method
 * there), with {@link SandboxUi#onScreenInit} as the fallback. It is controlled by the persisted
 * {@code geminiTitleScreen} option: the "Vanilla menu" link here switches it off and opens the vanilla screen,
 * and the "Gemini menu" button added to the vanilla screen switches it back on.
 *
 * <p>The buttons are real vanilla buttons (see {@link GlassButton}), so Tab, the arrow keys, Enter and the
 * narrator work as they do everywhere else. Multiplayer and Realms open the same screens the vanilla buttons
 * open, which means the sandbox refusal flow is exactly the vanilla-title one.
 */
public final class GeminiTitleScreen extends Screen {
   private static final int PARTICLES = 64;
   private static final int STATUS_ITEMS = 5;
   private static final int STATUS_GAP = 13;
   private static final long STATUS_INTERVAL_MS = 1000L;
   private static final String VERSION_BADGE = "Gemini v" + Gemini.VERSION;
   // Particle state lives in flat arrays that are allocated once per screen; nothing is allocated per frame.
   private final float[] particleX = new float[PARTICLES];
   private final float[] particleY = new float[PARTICLES];
   private final float[] velocityX = new float[PARTICLES];
   private final float[] velocityY = new float[PARTICLES];
   private final float[] depth = new float[PARTICLES];
   private final float[] screenX = new float[PARTICLES];
   private final float[] screenY = new float[PARTICLES];
   private final String[] statusFull = new String[STATUS_ITEMS];
   private final String[] statusShort = new String[STATUS_ITEMS];
   private final int[] statusFullWidth = new int[STATUS_ITEMS];
   private final int[] statusShortWidth = new int[STATUS_ITEMS];
   private final int[] statusColor = new int[STATUS_ITEMS];
   private final long openedAt = Util.getMillis();
   private TitleLayout layout = TitleLayout.compute(320, 240);
   private GlassButton[] geminiButtons = new GlassButton[0];
   private GlassButton[] fading = new GlassButton[0];
   private float parallaxX;
   private float parallaxY;
   private float fade;
   private long lastFrame;
   private long statusAt;
   private int shownFps = -1;
   private Component credits;
   private int creditsWidth;

   public GeminiTitleScreen() {
      super(Component.translatable("gemini.title.screen"));
      Random random = new Random();
      for (int i = 0; i < PARTICLES; i++) {
         this.particleX[i] = random.nextFloat();
         this.particleY[i] = random.nextFloat();
         double angle = random.nextDouble() * Math.PI * 2.0;
         float speed = 0.006F + random.nextFloat() * 0.014F;
         this.velocityX[i] = (float)Math.cos(angle) * speed;
         this.velocityY[i] = (float)Math.sin(angle) * speed;
         this.depth[i] = 0.25F + random.nextFloat() * 0.75F;
      }

      for (int i = 0; i < STATUS_ITEMS; i++) {
         this.statusFull[i] = "";
         this.statusShort[i] = "";
      }
   }

   // ---- swapping in for the vanilla title screen ------------------------------------------------------------

   /**
    * Whether {@code screen} is a vanilla title screen that the Gemini home screen should stand in for. Only the
    * exact vanilla class counts (never a subclass another mod made), and the demo keeps its own menu.
    */
   public static boolean replaces(Screen screen) {
      if (screen == null || screen.getClass() != TitleScreen.class) {
         return false;
      }

      Gemini gemini = Gemini.getInstance();
      return gemini != null && gemini.config() != null && gemini.config().geminiTitleScreen() && !Minecraft.getInstance().isDemo();
   }

   /** The screen to show instead of {@code screen}: a Gemini home screen for a vanilla title screen, otherwise the screen itself. */
   public static Screen swap(Screen screen) {
      return replaces(screen) ? new GeminiTitleScreen() : screen;
   }

   // ---- opening the menus -----------------------------------------------------------------------------------

   /**
    * Opens the presets and config manager. This is the single place that decides which screen that is (the pause
    * dock uses it too): the dashboard on its "Presets &amp; Configs" page. {@code parent} is the screen the player
    * came from; closing the dashboard returns there.
    */
   public static void openConfigs(Screen parent) {
      Minecraft.getInstance().gui.setScreen(new WindowGuiScreen(WindowGuiScreen.Page.PROFILES, parent));
   }

   // ---- widgets ---------------------------------------------------------------------------------------------

   @Override
   protected void init() {
      this.layout = TitleLayout.compute(this.width, this.height);
      TitleLayout l = this.layout;
      boolean online = this.minecraft.allowsMultiplayer();
      GlassButton singleplayer = this.nav(0, "singleplayer", () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)));
      GlassButton multiplayer = this.nav(
         1,
         "multiplayer",
         () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))
      );
      GlassButton realms = this.nav(2, "realms", () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)));
      GlassButton clickGui = this.nav(3, "clickgui", () -> this.minecraft.gui.setScreen(new ClickGuiScreen()));
      GlassButton windowGui = this.nav(4, "windowgui", () -> this.minecraft.gui.setScreen(new WindowGuiScreen()));
      GlassButton configs = this.nav(5, "configs", () -> openConfigs(this));
      GlassButton options = this.nav(6, "options", () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)));
      GlassButton quit = this.nav(7, "quit", this.minecraft::stop);
      if (!online) {
         multiplayer.active = false;
         realms.active = false;
         Tooltip disabled = Tooltip.create(Component.translatable("title.multiplayer.disabled"));
         multiplayer.setTooltip(disabled);
         realms.setTooltip(disabled);
      }

      GlassButton vanilla = this.addRenderableWidget(
         new GlassButton(6, l.footerY(), Math.min(96, this.font.width(Component.translatable("gemini.title.vanilla")) + 20), TitleLayout.FOOTER_HEIGHT, Component.translatable("gemini.title.vanilla"), button -> this.openVanilla())
      );
      vanilla.setTooltip(Tooltip.create(Component.translatable("gemini.title.vanilla.tooltip")));
      Component sandboxLabel = Component.translatable("gemini.title.sandbox");
      int switchWidth = this.font.width(sandboxLabel) + 46;
      GlassButton sandbox = this.addRenderableWidget(
         new GlassButton(
            this.width - 6 - switchWidth,
            l.footerY(),
            switchWidth,
            TitleLayout.FOOTER_HEIGHT,
            sandboxLabel,
            button -> this.toggleSandbox(button),
            SingleplayerGate::sandboxMode
         )
      );
      sandbox.setTooltip(Tooltip.create(SandboxUi.tooltip()));
      this.geminiButtons = new GlassButton[]{clickGui, windowGui, configs};
      this.fading = new GlassButton[]{singleplayer, multiplayer, realms, clickGui, windowGui, configs, options, quit, vanilla, sandbox};
      this.applySandboxState();
      this.credits = Component.translatable("title.credits");
      this.creditsWidth = this.font.width(this.credits);
      // Leave the footer text out when it would run into the two footer buttons.
      if (vanilla.getRight() + 8 + this.creditsWidth + 8 > sandbox.getX()) {
         this.credits = null;
      }

      this.statusAt = 0L;
      this.shownFps = -1;
   }

   private GlassButton nav(int index, String key, Runnable action) {
      TitleLayout l = this.layout;
      GlassButton button = new GlassButton(
         l.buttonX(index), l.buttonY(index), l.buttonWidth(), l.buttonHeight(), Component.translatable("gemini.title." + key), pressed -> action.run()
      );
      button.setTooltip(Tooltip.create(Component.translatable("gemini.title." + key + ".tooltip")));
      return this.addRenderableWidget(button);
   }

   /** ClickGUI, WindowGUI and Presets &amp; Configs are only for a switched-on Gemini; off, they explain why. */
   private void applySandboxState() {
      boolean on = SingleplayerGate.sandboxMode();
      String[] keys = {"clickgui", "windowgui", "configs"};
      for (int i = 0; i < this.geminiButtons.length; i++) {
         this.geminiButtons[i].active = on;
         this.geminiButtons[i].setTooltip(Tooltip.create(Component.translatable(on ? "gemini.title." + keys[i] + ".tooltip" : "gemini.title.disabled.tooltip")));
      }
   }

   private void toggleSandbox(net.minecraft.client.gui.components.Button button) {
      SandboxUi.toggleSandbox(this.minecraft);
      button.setTooltip(Tooltip.create(SandboxUi.tooltip()));
      this.applySandboxState();
      this.statusAt = 0L;
   }

   private void openVanilla() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.config() != null) {
         // Switched off first, so the vanilla screen opened next is left alone by the swap.
         gemini.config().setGeminiTitleScreen(false);
      }

      this.minecraft.gui.setScreen(new TitleScreen());
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ---- rendering -------------------------------------------------------------------------------------------

   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.fade = Glass.animations() ? Glass.easeOutCubic((now - this.openedAt) / 450.0F) : 1.0F;
      float targetX = this.width <= 0 ? 0.0F : Mth.clamp((float)mouseX / this.width - 0.5F, -0.5F, 0.5F) * 2.0F;
      float targetY = this.height <= 0 ? 0.0F : Mth.clamp((float)mouseY / this.height - 0.5F, -0.5F, 0.5F) * 2.0F;
      this.parallaxX = Glass.approach(this.parallaxX, targetX, 4.0F, delta);
      this.parallaxY = Glass.approach(this.parallaxY, targetY, 4.0F, delta);
      int accent = Glass.accent();
      int accent2 = Glass.accent2();
      // Slow gradient: the two accents breathe in and out of a near-black base at different rates.
      double seconds = (now % 3600000L) / 1000.0;
      float waveA = (float)(0.5 + 0.5 * Math.sin(seconds * 0.23));
      float waveB = (float)(0.5 + 0.5 * Math.sin(seconds * 0.17 + 2.1));
      int base = Glass.lerp(0xFF04060A, Glass.withAlpha(Glass.surface(0), 255), 0.6F);
      g.fillGradient(0, 0, this.width, this.height, Glass.lerp(base, accent, 0.07F + 0.09F * waveA), Glass.lerp(0xFF020306, accent2, 0.05F + 0.10F * waveB));
      int bandY = Math.round(this.height * (0.30F + 0.25F * waveB));
      Glass.gradientBar(g, 0, bandY, this.width, bandY + this.height / 4, Glass.withAlpha(accent, 0), Glass.withAlpha(accent2, Math.round(16.0F + 14.0F * waveA)));
      this.extractParticles(g, delta, accent, accent2);
   }

   private void extractParticles(GuiGraphicsExtractor g, float delta, int accent, int accent2) {
      float reach = Mth.clamp(Math.min(this.width, this.height) * 0.22F, 44.0F, 90.0F);
      for (int i = 0; i < PARTICLES; i++) {
         float x = this.particleX[i] + this.velocityX[i] * delta;
         float y = this.particleY[i] + this.velocityY[i] * delta;
         // Wrap a little outside the screen so particles drift in and out instead of popping.
         if (x < -0.06F) {
            x += 1.12F;
         } else if (x > 1.06F) {
            x -= 1.12F;
         }

         if (y < -0.06F) {
            y += 1.12F;
         } else if (y > 1.06F) {
            y -= 1.12F;
         }

         this.particleX[i] = x;
         this.particleY[i] = y;
         // Nearer particles (larger depth) shift further against the mouse.
         this.screenX[i] = x * this.width - this.parallaxX * this.depth[i] * 16.0F;
         this.screenY[i] = y * this.height - this.parallaxY * this.depth[i] * 10.0F;
      }

      float reachSq = reach * reach;
      for (int i = 0; i < PARTICLES; i++) {
         for (int j = i + 1; j < PARTICLES; j++) {
            float dx = this.screenX[i] - this.screenX[j];
            float dy = this.screenY[i] - this.screenY[j];
            float distSq = dx * dx + dy * dy;
            if (distSq < reachSq) {
               float closeness = 1.0F - (float)Math.sqrt(distSq) / reach;
               int alpha = Math.round(closeness * 70.0F * this.fade);
               if (alpha > 2) {
                  Glass.line(g, this.screenX[i], this.screenY[i], this.screenX[j], this.screenY[j], Glass.withAlpha(accent, alpha));
               }
            }
         }
      }

      for (int i = 0; i < PARTICLES; i++) {
         int x = Math.round(this.screenX[i]);
         int y = Math.round(this.screenY[i]);
         int size = this.depth[i] > 0.7F ? 2 : 1;
         int color = Glass.withAlpha(Glass.lerp(accent, accent2, this.depth[i]), Math.round((70.0F + 150.0F * this.depth[i]) * this.fade));
         g.fill(x - size + 1, y - size + 1, x + size, y + size, color);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      for (GlassButton button : this.fading) {
         button.setAlpha(this.fade);
      }

      this.extractHeader(g);
      if (this.credits != null && this.fade > 0.1F) {
         g.text(this.font, this.credits, (this.width - this.creditsWidth) / 2, this.layout.footerY() + 4, Glass.fade(Glass.TEXT_FAINT, this.fade), false);
      }

      this.extractStatusBar(g);
      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   private void extractHeader(GuiGraphicsExtractor g) {
      if (this.fade <= 0.1F) {
         return;
      }

      TitleLayout l = this.layout;
      int logo = l.logoSize();
      float scale = logo >= 36 ? 2.0F : 1.5F;
      int nameWidth = Math.round(this.font.width(Gemini.NAME) * scale);
      int badgeWidth = this.font.width(VERSION_BADGE) + 10;
      int total = logo + 8 + Math.max(nameWidth, badgeWidth);
      // The header floats a few pixels with the mouse, in the same direction as the far particles.
      int x = (this.width - total) / 2 - Math.round(this.parallaxX * 4.0F);
      int y = l.headerY() - Math.round(this.parallaxY * 2.0F);
      GeminiMark.draw(g, x, y, logo, -1, Glass.pulse(4000L));
      int textX = x + logo + 8;
      g.pose().pushMatrix();
      g.pose().translate(textX, y + 1);
      g.pose().scale(scale, scale);
      g.text(this.font, Gemini.NAME, 0, 0, Glass.fade(Glass.TEXT, this.fade), true);
      g.pose().popMatrix();
      int badgeY = y + logo - 12;
      Glass.panel(g, textX, badgeY, textX + badgeWidth, badgeY + 12, 5, Glass.surface(1), Glass.accent(), this.fade, 0.35F);
      g.text(this.font, VERSION_BADGE, textX + 5, badgeY + 2, Glass.fade(Glass.lerp(Glass.TEXT_DIM, Glass.accent2(), 0.35F), this.fade), false);
   }

   // ---- status bar ------------------------------------------------------------------------------------------

   private void setStatus(int index, String full, String brief, int color) {
      this.statusFull[index] = full;
      this.statusShort[index] = brief;
      this.statusFullWidth[index] = this.font.width(full);
      this.statusShortWidth[index] = this.font.width(brief);
      this.statusColor[index] = color;
   }

   /** Everything but the frame rate is looked up at most once a second; the keybind scan in particular is not free. */
   private void refreshStatus(long now) {
      int fps = this.minecraft.getFps();
      if (fps != this.shownFps) {
         this.shownFps = fps;
         this.setStatus(0, "FPS " + fps, fps + " FPS", Glass.TEXT);
      }

      if (this.statusAt != 0L && now - this.statusAt < STATUS_INTERVAL_MS && now >= this.statusAt) {
         return;
      }

      this.statusAt = now;
      Gemini gemini = Gemini.getInstance();
      String profile = gemini == null || gemini.config() == null ? null : gemini.config().activeProfile();
      this.setStatus(1, "Profile: " + (profile == null ? "none" : profile), profile == null ? "No profile" : profile, Glass.TEXT_DIM);
      int conflicts = gemini == null || gemini.modules() == null ? 0 : KeybindConflicts.find(KeybindReport.snapshot(gemini.modules())).size();
      if (conflicts == 0) {
         this.setStatus(2, "Keybinds: No conflicts", "No conflicts", Glass.OK);
      } else {
         String text = conflicts + (conflicts == 1 ? " conflict" : " conflicts");
         this.setStatus(2, "Keybinds: " + text, text, Glass.WARN);
      }

      int applied = MixinReport.appliedCount();
      int failed = MixinReport.failedCount();
      this.setStatus(
         3, "Mixins: " + applied + " applied, " + failed + " failed", failed == 0 ? "Mixins OK" : failed + " mixins failed", failed == 0 ? Glass.OK : Glass.ERROR
      );
      boolean sandbox = SingleplayerGate.sandboxMode();
      this.setStatus(4, sandbox ? "Sandbox: On (offline)" : "Sandbox: Off (Gemini dormant)", sandbox ? "Sandbox on" : "Gemini off", sandbox ? Glass.INFO : Glass.WARN);
   }

   private void extractStatusBar(GuiGraphicsExtractor g) {
      this.refreshStatus(Util.getMillis());
      int top = this.layout.statusY();
      g.fill(0, top, this.width, this.height, Glass.withAlpha(Glass.surface(0), Math.round(214.0F * this.fade)));
      Glass.gradientBar(g, 0, top, this.width, top + 1, Glass.fade(Glass.withAlpha(Glass.accent(), 170), this.fade), Glass.fade(Glass.withAlpha(Glass.accent2(), 170), this.fade));
      if (this.fade <= 0.1F) {
         return;
      }

      int room = this.width - 12;
      int fullTotal = STATUS_GAP * (STATUS_ITEMS - 1);
      int shortTotal = fullTotal;
      for (int i = 0; i < STATUS_ITEMS; i++) {
         fullTotal += this.statusFullWidth[i];
         shortTotal += this.statusShortWidth[i];
      }

      boolean brief = fullTotal > room;
      int total = brief ? shortTotal : fullTotal;
      // Even the short forms can be too wide on a very narrow window; shrink the row rather than clip it.
      float scale = total > room && total > 0 ? (float)room / total : 1.0F;
      g.pose().pushMatrix();
      g.pose().translate(6.0F, top + 4 + (1.0F - scale) * 4.0F);
      g.pose().scale(scale, scale);
      int x = 0;
      for (int i = 0; i < STATUS_ITEMS; i++) {
         g.text(this.font, brief ? this.statusShort[i] : this.statusFull[i], x, 0, Glass.fade(this.statusColor[i], this.fade), false);
         x += brief ? this.statusShortWidth[i] : this.statusFullWidth[i];
         if (i < STATUS_ITEMS - 1) {
            g.fill(x + STATUS_GAP / 2, 0, x + STATUS_GAP / 2 + 1, 8, Glass.fade(Glass.withAlpha(Glass.TEXT_FAINT, 120), this.fade));
            x += STATUS_GAP;
         }
      }

      g.pose().popMatrix();
   }
}
