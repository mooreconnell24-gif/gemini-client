package gemini.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import gemini.Gemini;
import gemini.diags.MixinReport;
import gemini.input.GeminiKeys;
import gemini.ui.clickgui.Menus;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.ui.font.UiFont;
import gemini.ui.help.HelpScreen;
import gemini.ui.palette.PaletteScreen;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Gemini's home screen, shown in place of the vanilla title screen: a dark backdrop with a slowly drifting network of
 * points (see {@link NetworkField}), the logo, and one column with the menu. Singleplayer is the main action; under it "Continue" opens the most recently played world
 * directly, when there is one. Multiplayer and Realms, then Options and Quit follow, and a row of small text links
 * (the Gemini menu, the guide, quick search) ends the column. The sandbox switch sits quietly in the top-right
 * corner and the footer carries the version, the "Vanilla menu" link and Mojang's notice. A line of text only
 * appears when something is actually wrong (a game hook failed to load).
 *
 * <p>The swap happens in {@link gemini.mixin.TitleScreenMixin} (every screen change goes through one method
 * there), with {@link SandboxUi#onScreenInit} as the fallback. It is controlled by the persisted
 * {@code geminiTitleScreen} option: the "Vanilla menu" link here switches it off and opens the vanilla screen,
 * and the "Gemini menu" button added to the vanilla screen switches it back on.
 *
 * <p>The buttons are real vanilla buttons (see {@link GlassButton}), so Tab, the arrow keys, Enter and the
 * narrator work as they do everywhere else. Multiplayer and Realms open the same screens the vanilla buttons
 * open, which means the sandbox refusal flow is exactly the vanilla-title one. Continue opens the world through
 * {@code WorldOpenFlows.openWorld}, exactly as the vanilla world list does.
 */
public final class GeminiTitleScreen extends Screen {
   private static final String VERSION = "dev".equals(Gemini.VERSION) ? "dev build" : "Gemini " + Gemini.VERSION;
   private static boolean whatsNewChecked;
   /** The soft round glow used for the depth gradient and the light behind the menu (linear filtering, see its .mcmeta). */
   private static final Identifier GLOW = Identifier.fromNamespaceAndPath("gemini", "textures/gui/title_glow.png");
   private static final int GLOW_SIZE = 128;
   /** The deep navy base and the lighter navy it rises to towards the middle. */
   private static final int BASE = 0xFF05080F;
   private static final int LIFT = 0xFF14203A;
   /** Peak opacity (0..255) of links and points per layer, far to near. */
   private static final float[] LINK_ALPHA = {38.0F, 72.0F, 105.0F};
   private static final float[] POINT_ALPHA = {100.0F, 170.0F, 220.0F};
   /** How much of the network is left right behind the menu column: none, so the menu sits on a clean patch. */
   private static final float CALM_FLOOR = 0.0F;
   /** Speed of the eased mouse parallax; low, so the layers glide after the mouse. */
   private static final float PARALLAX_SPEED = 1.6F;
   /** Transparent margin left of the artwork in the logo texture, in texture pixels. */
   private static final int LOGO_ART_LEFT = 12;
   /** Per-point opacity scratch, filled each frame (no per-frame allocation). */
   private static final float[] SHADE = new float[NetworkField.MAX_POINTS];
   /** The network and its parallax are shared by every home screen, so it carries on after Options and back. */
   private static NetworkField field;
   private static float parallaxX;
   private static float parallaxY;
   private static float networkShown;
   private final long openedAt = Util.getMillis();
   private final List<GlassButton> fading = new ArrayList<>();
   /** The tertiary links (label key, action); they are laid out in one row in the order they are added. */
   private final List<String> linkKeys = new ArrayList<>();
   private final List<Runnable> linkActions = new ArrayList<>();
   private TitleLayout layout = TitleLayout.compute(320, 240, 2, false);
   private GlassButton menuLink;
   private GlassButton searchLink;
   private float fade;
   private long lastFrame;
   private LevelSummary recentWorld;
   private Component credits;
   private int creditsWidth;
   private int creditsX;
   private String problem;

   public GeminiTitleScreen() {
      super(Component.translatable("gemini.title.screen"));
      this.loadRecentWorld();
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

   // ---- the most recent world -------------------------------------------------------------------------------

   /**
    * Reads the world list in the background, the way the vanilla world list does, and remembers the most recently
    * played world that can be opened straight away. The menu is rebuilt with a Continue button once it is known.
    */
   private void loadRecentWorld() {
      Minecraft client = Minecraft.getInstance();
      LevelStorageSource source = client.getLevelSource();
      CompletableFuture<List<LevelSummary>> summaries;
      try {
         LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
         if (candidates.isEmpty()) {
            return;
         }

         summaries = source.loadLevelSummaries(candidates);
      } catch (Exception e) {
         // No saves folder or it cannot be read: there is simply nothing to continue.
         return;
      }

      summaries.thenAccept(list -> {
         LevelSummary best = null;
         for (LevelSummary summary : list) {
            if (continuable(summary) && (best == null || summary.getLastPlayed() > best.getLastPlayed())) {
               best = summary;
            }
         }

         LevelSummary found = best;
         if (found != null) {
            client.execute(() -> {
               this.recentWorld = found;
               if (client.gui.screen() == this) {
                  this.rebuildWidgets();
               }
            });
         }
      });
   }

   /**
    * A world Continue may open without asking anything first: not locked by another game, playable in this
    * version without a manual conversion, and not a symbolic link (the vanilla list shows a warning for those).
    */
   private static boolean continuable(LevelSummary summary) {
      return summary != null
         && !(summary instanceof LevelSummary.SymlinkLevelSummary)
         && summary.primaryActionActive()
         && !summary.isLocked()
         && !summary.requiresManualConversion()
         && !summary.isDowngrade();
   }

   private void continueWorld() {
      LevelSummary world = this.recentWorld;
      if (world != null) {
         this.minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> this.minecraft.gui.setScreen(this));
      }
   }

   // ---- widgets ---------------------------------------------------------------------------------------------

   @Override
   protected void init() {
      boolean hasContinue = this.recentWorld != null;
      this.layout = TitleLayout.compute(this.width, this.height, this.minecraft.getWindow().getGuiScale(), hasContinue);
      TitleLayout l = this.layout;
      this.fading.clear();
      boolean online = this.minecraft.allowsMultiplayer();
      this.main(TitleLayout.SINGLEPLAYER, "singleplayer", () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))).primary();
      if (hasContinue) {
         GlassButton resume = this.main(TitleLayout.CONTINUE, "continue", this::continueWorld).detail(this.recentWorld.getLevelName());
         resume.setTooltip(
            UiFont.tooltip(
               Component.translatable("gemini.title.continue.tooltip", this.recentWorld.getLevelName(), TitleLayout.lastPlayed(Util.getEpochMillis() - this.recentWorld.getLastPlayed()))
            )
         );
      }

      GlassButton multiplayer = this.main(
         TitleLayout.MULTIPLAYER,
         "multiplayer",
         () -> this.minecraft.gui.setScreen(this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))
      );
      GlassButton realms = this.main(TitleLayout.REALMS, "realms", () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)));
      this.main(TitleLayout.OPTIONS, "options", () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options)));
      this.main(TitleLayout.QUIT, "quit", this.minecraft::stop);
      if (!online) {
         multiplayer.active = false;
         realms.active = false;
         Tooltip disabled = UiFont.tooltip(Component.translatable("title.multiplayer.disabled"));
         multiplayer.setTooltip(disabled);
         realms.setTooltip(disabled);
      }

      this.linkKeys.clear();
      this.linkActions.clear();
      this.link("menu", () -> this.minecraft.gui.setScreen(Menus.create()));
      this.link("guide", () -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, this));
      this.link("search", () -> this.minecraft.gui.setScreen(new PaletteScreen(this)));
      this.link("mods", () -> gemini.ui.mods.ModBrowserScreen.open(this));
      GlassButton[] links = this.addLinks(l);
      this.menuLink = links[this.linkKeys.indexOf("menu")];
      this.searchLink = links[this.linkKeys.indexOf("search")];
      this.addFooter(l);
      this.applySandboxState();
      this.problem = this.problemText();
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

   private GlassButton main(int slot, String key, Runnable action) {
      TitleLayout l = this.layout;
      GlassButton button = new GlassButton(l.x(slot), l.y(slot), l.w(slot), l.buttonHeight(), Component.translatable("gemini.title." + key), pressed -> action.run()).raised();
      if (slot != TitleLayout.OPTIONS && slot != TitleLayout.QUIT) {
         button.leftAligned();
      }

      button.setTooltip(UiFont.tooltip(Component.translatable("gemini.title." + key + ".tooltip")));
      this.fading.add(button);
      return this.addRenderableWidget(button);
   }

   /** Adds a small text link to the row under the menu. */
   private void link(String key, Runnable action) {
      this.linkKeys.add(key);
      this.linkActions.add(action);
   }

   private GlassButton[] addLinks(TitleLayout l) {
      int count = this.linkKeys.size();
      Component[] labels = new Component[count];
      int[] widths = new int[count];
      for (int i = 0; i < count; i++) {
         labels[i] = Component.translatable("gemini.title." + this.linkKeys.get(i));
         widths[i] = UiFont.width(labels[i]) + 2 * Theme.SPACE_XS;
      }

      int[] x = l.linkX(widths);
      // A left-aligned row starts where the button labels start, so the text lines up down the column.
      int indent = l.centred() ? 0 : Theme.BUTTON_PAD - Theme.SPACE_XS;
      GlassButton[] links = new GlassButton[count];
      for (int i = 0; i < count; i++) {
         Runnable action = this.linkActions.get(i);
         links[i] = this.addRenderableWidget(
            new GlassButton(x[i] + indent, l.linksY(), widths[i], TitleLayout.LINK_HEIGHT, labels[i], pressed -> action.run()).plain()
         );
         links[i].setTooltip(UiFont.tooltip(Component.translatable("gemini.title." + this.linkKeys.get(i) + ".tooltip")));
         this.fading.add(links[i]);
      }

      return links;
   }

   /** Footer: the version and the vanilla-menu link on the left, Mojang's notice on the right; the sandbox switch top right. */
   private void addFooter(TitleLayout l) {
      int textY = l.footerY() + 2;
      int versionWidth = UiFont.width(VERSION);
      Component vanillaLabel = Component.translatable("gemini.title.vanilla");
      int vanillaX = TitleLayout.EDGE + versionWidth + Theme.SPACE_L;
      GlassButton vanilla = this.addRenderableWidget(
         new GlassButton(vanillaX, textY - 2, UiFont.width(vanillaLabel) + 2 * Theme.SPACE_XS, TitleLayout.FOOTER_HEIGHT, vanillaLabel, button -> this.openVanilla())
            .plain()
      );
      vanilla.setTooltip(UiFont.tooltip(Component.translatable("gemini.title.vanilla.tooltip")));
      this.fading.add(vanilla);
      Component sandboxLabel = Component.translatable("gemini.title.sandbox");
      int switchWidth = UiFont.width(sandboxLabel) + 2 * Theme.SPACE_XS + Theme.SPACE_S + 18;
      GlassButton sandbox = this.addRenderableWidget(
         new GlassButton(
               this.width - TitleLayout.EDGE - switchWidth,
               TitleLayout.EDGE,
               switchWidth,
               TitleLayout.CORNER_HEIGHT,
               sandboxLabel,
               button -> this.toggleSandbox(button),
               SingleplayerGate::sandboxMode
            )
            .plain()
      );
      sandbox.setTooltip(UiFont.tooltip(SandboxUi.tooltip()));
      this.fading.add(sandbox);
      this.credits = Component.translatable("title.credits");
      this.creditsWidth = UiFont.width(this.credits);
      this.creditsX = this.width - TitleLayout.EDGE - this.creditsWidth;
      // Leave the notice out when it would run into the vanilla-menu link.
      if (this.creditsX < vanilla.getRight() + 8) {
         this.credits = null;
      }
   }

   /** The Gemini menu and quick search are only for a switched-on Gemini; off, they say why. */
   private void applySandboxState() {
      boolean on = SingleplayerGate.sandboxMode();
      this.menuLink.active = on;
      this.searchLink.active = on;
      this.menuLink.setTooltip(UiFont.tooltip(Component.translatable(on ? "gemini.title.menu.tooltip" : "gemini.title.disabled.tooltip")));
      this.searchLink.setTooltip(UiFont.tooltip(Component.translatable(on ? "gemini.title.search.tooltip" : "gemini.title.disabled.tooltip")));
   }

   private void toggleSandbox(net.minecraft.client.gui.components.Button button) {
      SandboxUi.toggleSandbox(this.minecraft);
      button.setTooltip(UiFont.tooltip(SandboxUi.tooltip()));
      this.applySandboxState();
   }

   private void openVanilla() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null && gemini.config() != null) {
         // Switched off first, so the vanilla screen opened next is left alone by the swap.
         gemini.config().setGeminiTitleScreen(false);
      }

      this.minecraft.gui.setScreen(new TitleScreen());
   }

   /** The one thing worth a line of text on the home screen: game hooks that failed to load. Null when all is well. */
   private String problemText() {
      int failed = MixinReport.failedCount();
      if (failed <= 0) {
         return null;
      }

      return failed == 1 ? "A game hook failed to load; see .version" : failed + " game hooks failed to load; see .version";
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

   /**
    * The backdrop: a deep navy base lit a little towards the middle (darker towards the corners), a faint pool of
    * accent light where the menu sits, and the drifting network (see {@link NetworkField}) in three depth layers
    * with eased mouse parallax. Points and links near the menu column are dimmed so the menu reads cleanly. The
    * network is drawn in window pixels, so its lines are one real pixel thin at every GUI scale. With the
    * "Animations" setting off it stands still and ignores the mouse.
    */
   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      boolean animated = Glass.animations();
      this.fade = animated ? Glass.easeOutCubic((now - this.openedAt) / 400.0F) : 1.0F;
      if (animated) {
         float targetX = this.width <= 0 ? 0.0F : Math.clamp((float)mouseX / this.width * 2.0F - 1.0F, -1.0F, 1.0F);
         float targetY = this.height <= 0 ? 0.0F : Math.clamp((float)mouseY / this.height * 2.0F - 1.0F, -1.0F, 1.0F);
         parallaxX = Glass.approach(parallaxX, targetX, PARALLAX_SPEED, delta);
         parallaxY = Glass.approach(parallaxY, targetY, PARALLAX_SPEED, delta);
      } else {
         parallaxX = 0.0F;
         parallaxY = 0.0F;
      }

      // The network carries on across visits (Options and back), so it only fades in the first time.
      networkShown = animated ? Math.min(1.0F, networkShown + delta / 0.9F) : 1.0F;
      float shown = Glass.easeInOut(networkShown);
      int accent = Glass.accent();
      TitleLayout l = this.layout;
      g.fill(0, 0, this.width, this.height, BASE);
      // Depth: a broad, soft lift of the base towards the middle; the corners keep the darkest navy.
      glow(g, this.width * 0.56F, this.height * 0.40F, this.width * 1.25F, this.height * 1.45F, LIFT);
      // The menu's place: a very soft pool of accent light behind the column.
      float poolX = l.columnX() + l.columnWidth() * 0.5F;
      float poolTop = l.y(TitleLayout.SINGLEPLAYER);
      float poolBottom = l.linksY() + TitleLayout.LINK_HEIGHT;
      float poolY = (poolTop + poolBottom) * 0.5F;
      glow(g, poolX, poolY, l.columnWidth() * 3.0F, (poolBottom - poolTop) * 2.8F, Glass.withAlpha(accent, 30));
      this.extractNetwork(g, animated ? delta : 0.0F, shown);
   }

   /** The soft radial glow texture, tinted {@code color}, centred on ({@code cx}, {@code cy}) and {@code w} x {@code h} GUI units large. */
   private static void glow(GuiGraphicsExtractor g, float cx, float cy, float w, float h, int color) {
      int x = Math.round(cx - w * 0.5F);
      int y = Math.round(cy - h * 0.5F);
      g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0.0F, 0.0F, Math.round(w), Math.round(h), GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
   }

   private void extractNetwork(GuiGraphicsExtractor g, float delta, float shown) {
      if (shown <= 0.01F) {
         return;
      }

      int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
      int pw = this.width * scale;
      int ph = this.height * scale;
      NetworkField f = field(pw, ph);
      f.update(delta, parallaxX, parallaxY);
      TitleLayout l = this.layout;
      // The calm zone around the menu column, in window pixels.
      float zx1 = (l.columnX() - 8) * scale;
      float zx2 = (l.columnX() + l.columnWidth() + 8) * scale;
      float zy1 = l.logoY() * scale;
      float zy2 = (l.linksY() + TitleLayout.LINK_HEIGHT) * scale;
      float soft = 36.0F * scale;
      int count = f.count();
      for (int i = 0; i < count; i++) {
         SHADE[i] = NetworkField.calm(f.x(i), f.y(i), zx1, zy1, zx2, zy2, soft, CALM_FLOOR) * shown;
      }

      int accent = Glass.accent();
      int accent2 = Glass.accent2();
      int lineColor = Glass.lerp(accent, accent2, 0.55F);
      g.pose().pushMatrix();
      g.pose().scale(1.0F / scale, 1.0F / scale);
      for (int layer = 0; layer < NetworkField.LAYERS; layer++) {
         float reach = f.reach(layer);
         float reachSq = reach * reach;
         float strength = LINK_ALPHA[layer];
         int end = f.layerEnd(layer);
         for (int i = f.layerStart(layer); i < end; i++) {
            float xi = f.x(i);
            float yi = f.y(i);
            for (int j = i + 1; j < end; j++) {
               float dx = f.x(j) - xi;
               float dy = f.y(j) - yi;
               float distSq = dx * dx + dy * dy;
               if (distSq >= reachSq || offScreen(xi, yi, f.x(j), f.y(j), pw, ph)) {
                  continue;
               }

               int alpha = Math.round(strength * NetworkField.linkAlpha((float)Math.sqrt(distSq), reach) * Math.min(SHADE[i], SHADE[j]));
               if (alpha >= 2) {
                  Glass.line(g, xi, yi, f.x(j), f.y(j), Glass.withAlpha(lineColor, alpha));
               }
            }
         }
      }

      float unit = Math.min(pw, ph) / 720.0F;
      for (int layer = 0; layer < NetworkField.LAYERS; layer++) {
         float depth = NetworkField.depth(layer);
         int size = Math.max(1, Math.round((0.9F + 1.4F * depth) * unit));
         int color = Glass.lerp(accent2, 0xFFFFFFFF, 0.25F + 0.45F * depth);
         float strength = POINT_ALPHA[layer];
         int end = f.layerEnd(layer);
         for (int i = f.layerStart(layer); i < end; i++) {
            int x = Math.round(f.x(i) - size * 0.5F);
            int y = Math.round(f.y(i) - size * 0.5F);
            if (x + size + 1 < 0 || y + size + 1 < 0 || x - 1 > pw || y - 1 > ph) {
               continue;
            }

            int alpha = Math.round(strength * SHADE[i]);
            if (alpha < 2) {
               continue;
            }

            if (layer > 0) {
               // A one-pixel halo at a quarter of the opacity softens the square into a dot.
               g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Glass.withAlpha(color, alpha / 4));
            }

            g.fill(x, y, x + size, y + size, Glass.withAlpha(color, alpha));
         }
      }

      g.pose().popMatrix();
   }

   /** Whether a link between two points lies entirely beyond one edge of a {@code w} x {@code h} window. */
   private static boolean offScreen(float x1, float y1, float x2, float y2, int w, int h) {
      return x1 < 0.0F && x2 < 0.0F || y1 < 0.0F && y2 < 0.0F || x1 > w && x2 > w || y1 > h && y2 > h;
   }

   /** The shared network, sized for a {@code w} x {@code h} window. */
   private static NetworkField field(int w, int h) {
      if (field == null) {
         field = new NetworkField(Util.getMillis());
      }

      if (field.width() != w || field.height() != h) {
         field.resize(w, h);
      }

      return field;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      for (GlassButton button : this.fading) {
         button.setAlpha(this.fade);
      }

      if (this.fade > 0.1F) {
         TitleLayout l = this.layout;
         if (l.logoPixels() > 0) {
            // Drawn in window pixels at a whole ratio of the texture, so it stays sharp at every GUI scale.
            int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
            int color = Glass.withAlpha(0xFFFFFF, Math.round(255.0F * this.fade));
            int pixels = l.logoPixels();
            // In the left column the artwork (not its transparent margin) lines up with the buttons' left edge.
            int x = l.centred()
               ? l.logoX() * scale + (l.logoSize() * scale - pixels) / 2
               : l.logoX() * scale - LOGO_ART_LEFT * pixels / TitleLayout.LOGO_TEXTURE;
            int y = l.logoY() * scale + (l.logoSize() * scale - pixels) / 2;
            g.pose().pushMatrix();
            g.pose().scale(1.0F / scale, 1.0F / scale);
            g.blit(RenderPipelines.GUI_TEXTURED, GeminiMark.LOGO, x, y, 0.0F, 0.0F, pixels, pixels, TitleLayout.LOGO_TEXTURE, TitleLayout.LOGO_TEXTURE, TitleLayout.LOGO_TEXTURE, TitleLayout.LOGO_TEXTURE, color);
            g.pose().popMatrix();
         }

         int textY = l.footerY() + 2;
         UiFont.draw(g, VERSION, TitleLayout.EDGE, textY, Glass.fade(Glass.textFaint(), this.fade), false);
         if (this.credits != null) {
            UiFont.draw(g, this.credits, this.creditsX, textY, Glass.fade(Glass.textFaint(), this.fade), false);
         }

         if (this.problem != null) {
            UiFont.draw(g, this.problem, TitleLayout.EDGE, l.footerY() - 12, Glass.fade(Glass.ERROR, this.fade), false);
         }
      }

      super.extractRenderState(g, mouseX, mouseY, partialTick);
   }
}
