package gemini.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import gemini.Gemini;
import gemini.diags.MixinReport;
import gemini.input.BindWarnings;
import gemini.input.GeminiKeys;
import gemini.ui.clickgui.Menus;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.ui.help.HelpScreen;
import gemini.ui.palette.PaletteScreen;
import gemini.util.SingleplayerGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Gemini's home screen, shown in place of the vanilla title screen: the vanilla panorama, blurred and dimmed, the
 * logo and name once, the usual main buttons plus one "Gemini" button for the module menu, a row of small links
 * (quick search, guide, presets), the sandbox switch and a status line that stays empty unless there is something
 * worth knowing.
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
   private static final int MAX_STATUS = 4;
   private static final String STATUS_SEPARATOR = "  ·  ";
   private static final long STATUS_INTERVAL_MS = 1000L;
   private static final String NAME = "Gemini";
   private static final String VERSION = "dev".equals(Gemini.VERSION) ? "dev build" : Gemini.VERSION;
   private static final String[] LINK_KEYS = {"search", "guide", "configs"};
   private static boolean whatsNewChecked;
   private final String[] statusText = new String[MAX_STATUS];
   private final int[] statusWidth = new int[MAX_STATUS];
   private final int[] statusColor = new int[MAX_STATUS];
   private final long openedAt = Util.getMillis();
   private int statusCount;
   private TitleLayout layout = TitleLayout.compute(320, 240);
   private GlassButton[] geminiButtons = new GlassButton[0];
   private String[] geminiKeys = new String[0];
   private GlassButton[] fading = new GlassButton[0];
   private float fade;
   private long statusAt;
   private Component credits;
   private int creditsWidth;
   private int separatorWidth;

   public GeminiTitleScreen() {
      super(Component.translatable("gemini.title.screen"));
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
    * Opens the presets and config manager. This is the single place that decides which screen that is: the
    * dashboard on its "Presets &amp; Configs" page. {@code parent} is the screen the player came from; closing the
    * dashboard returns there.
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
      GlassButton singleplayer = this.main(0, "singleplayer", () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)));
      GlassButton multiplayer = this.main(
         1,
         "multiplayer",
         () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))
      );
      GlassButton realms = this.main(2, "realms", () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)));
      GlassButton menu = this.main(3, "menu", () -> this.minecraft.gui.setScreen(Menus.create()));
      GlassButton options = this.main(4, "options", () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)));
      GlassButton quit = this.main(5, "quit", this.minecraft::stop);
      if (!online) {
         multiplayer.active = false;
         realms.active = false;
         Tooltip disabled = Tooltip.create(Component.translatable("title.multiplayer.disabled"));
         multiplayer.setTooltip(disabled);
         realms.setTooltip(disabled);
      }

      Component[] linkLabels = new Component[LINK_KEYS.length];
      int[] linkWidths = new int[LINK_KEYS.length];
      for (int i = 0; i < LINK_KEYS.length; i++) {
         linkLabels[i] = Component.translatable("gemini.title." + LINK_KEYS[i]);
         linkWidths[i] = this.font.width(linkLabels[i]) + 2 * Theme.SPACE_XS;
      }

      int[] linkX = l.linkX(linkWidths);
      GlassButton[] links = new GlassButton[LINK_KEYS.length];
      for (int i = 0; i < LINK_KEYS.length; i++) {
         int link = i;
         links[i] = this.addRenderableWidget(
            new GlassButton(linkX[i], l.linksY(), linkWidths[i], TitleLayout.LINK_HEIGHT, linkLabels[i], pressed -> this.openLink(link)).plain()
         );
         links[i].setTooltip(Tooltip.create(Component.translatable("gemini.title." + LINK_KEYS[i] + ".tooltip")));
      }

      Component vanillaLabel = Component.translatable("gemini.title.vanilla");
      GlassButton vanilla = this.addRenderableWidget(
         new GlassButton(4, l.footerY(), this.font.width(vanillaLabel) + 2 * Theme.SPACE_XS, TitleLayout.FOOTER_HEIGHT, vanillaLabel, button -> this.openVanilla())
            .plain()
      );
      vanilla.setTooltip(Tooltip.create(Component.translatable("gemini.title.vanilla.tooltip")));
      Component sandboxLabel = Component.translatable("gemini.title.sandbox");
      int switchWidth = this.font.width(sandboxLabel) + 2 * Theme.SPACE_XS + Theme.SPACE_S + 18;
      GlassButton sandbox = this.addRenderableWidget(
         new GlassButton(
               this.width - 4 - switchWidth,
               l.footerY(),
               switchWidth,
               TitleLayout.FOOTER_HEIGHT,
               sandboxLabel,
               button -> this.toggleSandbox(button),
               SingleplayerGate::sandboxMode
            )
            .plain()
      );
      sandbox.setTooltip(Tooltip.create(SandboxUi.tooltip()));
      this.geminiButtons = new GlassButton[]{menu, links[0], links[2]};
      this.geminiKeys = new String[]{"menu", LINK_KEYS[0], LINK_KEYS[2]};
      this.fading = new GlassButton[]{singleplayer, multiplayer, realms, menu, options, quit, links[0], links[1], links[2], vanilla, sandbox};
      this.applySandboxState();
      this.credits = Component.translatable("title.credits");
      this.creditsWidth = this.font.width(this.credits);
      int creditsX = (this.width - this.creditsWidth) / 2;
      // Leave the footer text out when it would run into the two footer links.
      if (creditsX < vanilla.getRight() + 8 || creditsX + this.creditsWidth + 8 > sandbox.getX()) {
         this.credits = null;
      }

      this.separatorWidth = this.font.width(STATUS_SEPARATOR);
      this.statusAt = 0L;
      if (!whatsNewChecked) {
         // Once per game start: after an update, "What's new" opens over the home screen by itself.
         whatsNewChecked = true;
         this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() == this) {
               HelpScreen.maybeShowWhatsNew(this);
            }
         });
      }
   }

   /** Ctrl + the quick-search key opens the palette here too (while Gemini is switched on). */
   @Override
   public boolean keyPressed(KeyEvent event) {
      if (GeminiKeys.opensPalette(event) && SingleplayerGate.sandboxMode()) {
         this.minecraft.gui.setScreen(new PaletteScreen(this));
         return true;
      }

      return super.keyPressed(event);
   }

   private GlassButton main(int index, String key, Runnable action) {
      TitleLayout l = this.layout;
      GlassButton button = new GlassButton(
         l.buttonX(index), l.buttonY(index), l.buttonW(index), l.buttonHeight(), Component.translatable("gemini.title." + key), pressed -> action.run()
      );
      button.setTooltip(Tooltip.create(Component.translatable("gemini.title." + key + ".tooltip")));
      return this.addRenderableWidget(button);
   }

   private void openLink(int link) {
      switch (link) {
         case 0 -> this.minecraft.gui.setScreen(new PaletteScreen(this));
         case 1 -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, this);
         default -> openConfigs(this);
      }
   }

   /** The module menu, quick search and presets are only for a switched-on Gemini; off, they explain why. */
   private void applySandboxState() {
      boolean on = SingleplayerGate.sandboxMode();
      for (int i = 0; i < this.geminiButtons.length; i++) {
         this.geminiButtons[i].active = on;
         this.geminiButtons[i].setTooltip(
            Tooltip.create(Component.translatable(on ? "gemini.title." + this.geminiKeys[i] + ".tooltip" : "gemini.title.disabled.tooltip"))
         );
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

   /** The vanilla panorama behind the menu blur, dimmed, with a still vignette toward the top and bottom edges. */
   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      this.fade = Glass.animations() ? Glass.easeOutCubic((Util.getMillis() - this.openedAt) / 400.0F) : 1.0F;
      this.extractPanorama(g, partialTick);
      this.extractBlurredBackground(g);
      g.fill(0, 0, this.width, this.height, Glass.withAlpha(Glass.surface(0), 170));
      int band = this.height / 3;
      g.fillGradient(0, 0, this.width, band, 0x50000000, 0x00000000);
      g.fillGradient(0, this.height - band, this.width, this.height, 0x00000000, 0x60000000);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      for (GlassButton button : this.fading) {
         button.setAlpha(this.fade);
      }

      this.extractHeader(g);
      if (this.credits != null && this.fade > 0.1F) {
         g.text(this.font, this.credits, (this.width - this.creditsWidth) / 2, this.layout.footerY() + 3, Glass.fade(Glass.TEXT_FAINT, this.fade), false);
      }

      this.extractStatus(g);
      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }

   /** The logo and the name, once, with the version in small dim text after the name. */
   private void extractHeader(GuiGraphicsExtractor g) {
      if (this.fade <= 0.1F) {
         return;
      }

      TitleLayout l = this.layout;
      int logo = l.logoSize();
      float scale = logo >= 32 ? 2.0F : 1.5F;
      int nameWidth = Math.round(this.font.width(NAME) * scale);
      int versionWidth = this.font.width(VERSION);
      int total = logo + 8 + nameWidth + 6 + versionWidth;
      int x = (this.width - total) / 2;
      int y = l.headerY();
      GeminiMark.draw(g, x, y, logo, -1, 1.0F);
      int textX = x + logo + 8;
      int nameHeight = Math.round(Theme.FONT_HEIGHT * scale);
      int nameY = y + (logo - nameHeight) / 2;
      g.pose().pushMatrix();
      g.pose().translate(textX, nameY);
      g.pose().scale(scale, scale);
      g.text(this.font, NAME, 0, 0, Glass.fade(Glass.TEXT, this.fade), false);
      g.pose().popMatrix();
      // The version sits on the name's baseline.
      g.text(this.font, VERSION, textX + nameWidth + 6, nameY + nameHeight - Theme.FONT_HEIGHT, Glass.fade(Glass.TEXT_FAINT, this.fade), false);
   }

   // ---- status line -----------------------------------------------------------------------------------------

   private void addStatus(String text, int color) {
      if (this.statusCount < MAX_STATUS) {
         this.statusText[this.statusCount] = text;
         this.statusWidth[this.statusCount] = this.font.width(text);
         this.statusColor[this.statusCount] = color;
         this.statusCount++;
      }
   }

   /**
    * Rebuilt at most once a second (the keybind scan is not free). Only what is worth knowing is listed: that
    * Gemini is switched off, the active profile, key-bind conflicts and game hooks that failed to load. On a normal
    * start this is the profile, or nothing at all.
    */
   private void refreshStatus(long now) {
      if (this.statusAt != 0L && now - this.statusAt < STATUS_INTERVAL_MS && now >= this.statusAt) {
         return;
      }

      this.statusAt = now;
      this.statusCount = 0;
      Gemini gemini = Gemini.getInstance();
      boolean sandbox = SingleplayerGate.sandboxMode();
      if (!sandbox) {
         this.addStatus("Gemini is off; online play is allowed", Glass.TEXT_DIM);
      }

      String profile = gemini == null || gemini.config() == null ? null : gemini.config().activeProfile();
      if (profile != null) {
         this.addStatus("Profile: " + profile, Glass.TEXT_FAINT);
      }

      int conflicts = !sandbox || gemini == null || gemini.modules() == null ? 0 : BindWarnings.conflictCount(gemini.modules());
      if (conflicts > 0) {
         this.addStatus(conflicts + (conflicts == 1 ? " key conflict" : " key conflicts"), Glass.WARN);
      }

      int failed = MixinReport.failedCount();
      if (failed > 0) {
         this.addStatus(failed == 1 ? "A game hook failed to load; see .version" : failed + " game hooks failed to load; see .version", Glass.ERROR);
      }
   }

   private void extractStatus(GuiGraphicsExtractor g) {
      this.refreshStatus(Util.getMillis());
      if (this.statusCount == 0 || this.fade <= 0.1F) {
         return;
      }

      int room = this.width - 8;
      int total = this.separatorWidth * (this.statusCount - 1);
      for (int i = 0; i < this.statusCount; i++) {
         total += this.statusWidth[i];
      }

      // On a very narrow window shrink the line rather than clip it.
      float scale = total > room && total > 0 ? (float)room / total : 1.0F;
      g.pose().pushMatrix();
      g.pose().translate(4.0F, this.layout.statusY() + 2 + (1.0F - scale) * 4.0F);
      g.pose().scale(scale, scale);
      int x = 0;
      for (int i = 0; i < this.statusCount; i++) {
         g.text(this.font, this.statusText[i], x, 0, Glass.fade(this.statusColor[i], this.fade), false);
         x += this.statusWidth[i];
         if (i < this.statusCount - 1) {
            g.text(this.font, STATUS_SEPARATOR, x, 0, Glass.fade(Glass.TEXT_FAINT, this.fade), false);
            x += this.separatorWidth;
         }
      }

      g.pose().popMatrix();
   }
}
