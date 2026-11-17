package gemini.ui;

import com.mojang.realmsclient.RealmsMainScreen;
import gemini.Gemini;
import gemini.diags.MixinReport;
import gemini.input.GeminiKeys;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.Setting;
import gemini.ui.clickgui.Menus;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.ui.help.HelpScreen;
import gemini.ui.palette.PaletteScreen;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Gemini's home screen, shown in place of the vanilla title screen: a dark backdrop with a slowly drifting network of
 * points (see {@link NetworkField}) tinted by the theme, and one column: the emblem beside the name, the menu
 * (Singleplayer is the main action; under it "Continue" opens the most recently played world directly, when there
 * is one; then Multiplayer and Realms, Options and Quit) and a row of quick-link tiles (the module menu, the mod
 * browser, the guide, quick search). A small card in the bottom-right corner lists the first points of the
 * changelog and opens the guide. The footer carries the version, the theme swatches and Mojang's notice; the
 * "Vanilla menu" link and the sandbox switch sit quietly in the top-right corner. A line of text only appears when
 * something is actually wrong (a game hook failed to load). Everything fades and slides in once when the screen
 * opens.
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
   /** How long one element takes to arrive when the screen opens, and how far apart the elements start. */
   private static final int ENTER_MS = 420;
   private static final int ENTER_STAGGER_MS = 45;
   /** How far (GUI units) an element slides up while it arrives. */
   private static final float ENTER_SLIDE = 7.0F;
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
   /** Every widget with the place it has in the entrance order (see {@link TitleLayout#entrance}). */
   private final List<AbstractWidget> entering = new ArrayList<>();
   private final List<Integer> enterOrder = new ArrayList<>();
   private TitleLayout layout = TitleLayout.compute(320, 240, 2, false);
   private GlassButton menuLink;
   private GlassButton searchLink;
   private NewsCard news;
   private String tagline = "";
   private int versionWidth;
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
      this.entering.clear();
      this.enterOrder.clear();
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

      this.menuLink = this.link(0, "menu", GlassIcon.MODULES, () -> this.minecraft.gui.setScreen(Menus.create()));
      this.link(1, "mods", GlassIcon.MODS, () -> gemini.ui.mods.ModBrowserScreen.open(this));
      this.link(2, "guide", GlassIcon.GUIDE, () -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, this));
      this.searchLink = this.link(3, "search", GlassIcon.SEARCH, () -> this.minecraft.gui.setScreen(new PaletteScreen(this)));
      this.addNews(l);
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
      GlassButton button = new GlassButton(l.x(slot), l.y(slot), l.w(slot), l.buttonHeight(), Component.translatable("gemini.title." + key), pressed -> action.run())
         .raised()
         .radius(TitleLayout.BUTTON_RADIUS);
      if (slot != TitleLayout.OPTIONS && slot != TitleLayout.QUIT) {
         button.leftAligned();
      }

      button.setTooltip(UiFont.tooltip(Component.translatable("gemini.title." + key + ".tooltip")));
      return this.enter(this.addRenderableWidget(button), 1 + Math.min(slot, TitleLayout.OPTIONS));
   }

   /** Registers {@code widget} for the entrance animation as element number {@code order}. */
   private <T extends AbstractWidget> T enter(T widget, int order) {
      this.entering.add(widget);
      this.enterOrder.add(order);
      return widget;
   }

   /** Adds quick link {@code index}: a tile with the icon above its caption, or the caption alone when the column is short of room. */
   private GlassButton link(int index, String key, GlassIcon icon, Runnable action) {
      TitleLayout l = this.layout;
      GlassButton link = new GlassButton(l.linkX(index), l.linksY(), l.linkW(index), l.linksHeight(), Component.translatable("gemini.title." + key), pressed -> action.run());
      if (l.linkTiles()) {
         link.raised().radius(TitleLayout.BUTTON_RADIUS).icon(icon).stacked();
      } else {
         link.plain();
      }

      link.setTooltip(UiFont.tooltip(Component.translatable("gemini.title." + key + ".tooltip")));
      return this.enter(this.addRenderableWidget(link), 6 + index);
   }

   /** The "what's new" card: the first points of the changelog, as many as fit above the footer; clicking it opens the guide. */
   private void addNews(TitleLayout l) {
      this.news = null;
      if (!l.hasCard()) {
         return;
      }

      List<String> points = HelpScreen.teaser(NewsCard.POINTS);
      int room = l.cardBottom() - (TitleLayout.EDGE + TitleLayout.CORNER_HEIGHT + 8);
      for (int count = points.size(); count > 0; count--) {
         NewsCard card = new NewsCard(l.cardX(), l.cardBottom(), l.cardWidth(), points.subList(0, count), pressed -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, this));
         if (card.getHeight() <= room) {
            card.setTooltip(UiFont.tooltip(Component.translatable("gemini.title.guide.tooltip")));
            this.news = this.enter(this.addRenderableWidget(card), 11);
            return;
         }
      }
   }

   /**
    * The corners. Footer: the version and the theme swatches on the left, Mojang's notice on the right. Top
    * right: the vanilla-menu link and the sandbox switch.
    */
   private void addFooter(TitleLayout l) {
      this.versionWidth = UiFont.width(VERSION);
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
      this.enter(sandbox, 12);
      Component vanillaLabel = Component.translatable("gemini.title.vanilla");
      int vanillaWidth = UiFont.width(vanillaLabel) + 2 * Theme.SPACE_XS;
      GlassButton vanilla = this.addRenderableWidget(
         new GlassButton(sandbox.getX() - Theme.SPACE_XL - vanillaWidth, TitleLayout.EDGE, vanillaWidth, TitleLayout.CORNER_HEIGHT, vanillaLabel, button -> this.openVanilla())
            .plain()
      );
      vanilla.setTooltip(UiFont.tooltip(Component.translatable("gemini.title.vanilla.tooltip")));
      this.enter(vanilla, 12);
      int swatchesX = TitleLayout.EDGE + this.versionWidth + Theme.SPACE_XL;
      int swatchesEnd = swatchesX;
      ThemeSwatches swatches = new ThemeSwatches(swatchesX, l.footerY() - 1);
      // Only where the whole row fits; the theme is also a setting of the module menu.
      if (Theme.style() != null && swatchesX + swatches.getWidth() <= this.width - TitleLayout.EDGE) {
         this.enter(this.addRenderableWidget(swatches), 12);
         swatchesEnd = swatchesX + swatches.getWidth();
      }

      this.credits = Component.translatable("title.credits");
      this.creditsWidth = UiFont.width(this.credits);
      this.creditsX = this.width - TitleLayout.EDGE - this.creditsWidth;
      // Leave the notice out when it would run into the swatches.
      if (this.creditsX < swatchesEnd + 10) {
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
      this.tagline = Component.translatable(on ? "gemini.title.tagline.on" : "gemini.title.tagline.off").getString();
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
    * The backdrop: a near-black base in the theme's surface tone, lit a little towards the middle (darker towards
    * the corners), a faint pool of accent light where the menu sits, and the drifting network (see
    * {@link NetworkField}) in three depth layers with eased mouse parallax. Points and links near the menu column
    * are dimmed so the menu reads cleanly. The network is drawn in window pixels, so its lines are one real pixel
    * thin at every GUI scale. With the "Animations" setting off it stands still and ignores the mouse.
    */
   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      boolean animated = Glass.animations();
      this.fade = animated ? Math.min(1.0F, (now - this.openedAt) / 300.0F) : 1.0F;
      TitleLayout l = this.layout;
      extractBase(g, this.width, this.height, mouseX, mouseY, delta);
      // The menu's place: a very soft pool of accent light behind the column.
      float poolX = l.columnX() + l.columnWidth() * 0.5F;
      float poolTop = l.y(TitleLayout.SINGLEPLAYER);
      float poolBottom = l.columnBottom();
      float poolY = (poolTop + poolBottom) * 0.5F;
      glow(g, poolX, poolY, l.columnWidth() * 3.0F, (poolBottom - poolTop) * 2.8F, Glass.withAlpha(Glass.accent(), 30));
      int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
      // The calm zone around the menu column, in window pixels.
      extractNetwork(
         g,
         this.width,
         this.height,
         scale,
         animated ? delta : 0.0F,
         (l.columnX() - 8) * scale,
         l.headerY() * scale,
         (l.columnX() + l.columnWidth() + 8) * scale,
         l.columnBottom() * scale,
         true
      );
      if (this.news != null) {
         // A second, smaller calm patch keeps the card's text off the lines.
         int x1 = this.news.getX();
         int y1 = this.news.getY();
         Glass.shadow(g, x1, y1, this.news.getRight(), this.news.getBottom(), 8, 14, 0, Glass.withAlpha(base(), Math.round(150.0F * this.fade)));
      }
   }

   /**
    * The home screen's backdrop for another Gemini screen opened over it before a world is loaded (the guide, the
    * backups list): the same base and the same shared network, without the menu's calm patch, so the picture
    * behind the window is the one the player just left.
    */
   public static void extractBackdrop(GuiGraphicsExtractor g, int width, int height, int mouseX, int mouseY, float deltaSeconds) {
      Minecraft client = Minecraft.getInstance();
      extractBase(g, width, height, mouseX, mouseY, deltaSeconds);
      int scale = Math.max(1, client.getWindow().getGuiScale());
      extractNetwork(g, width, height, scale, Glass.animations() ? deltaSeconds : 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
   }

   /** The darkest tone of the backdrop: the theme's base surface taken most of the way to black. */
   private static int base() {
      return Glass.lerp(0xFF000000, Glass.surface(0), 0.6F);
   }

   /** Base colour, the lift towards the middle, and this frame's eased parallax and fade-in of the network. */
   private static void extractBase(GuiGraphicsExtractor g, int width, int height, int mouseX, int mouseY, float delta) {
      boolean animated = Glass.animations();
      if (animated) {
         float targetX = width <= 0 ? 0.0F : Math.clamp((float)mouseX / width * 2.0F - 1.0F, -1.0F, 1.0F);
         float targetY = height <= 0 ? 0.0F : Math.clamp((float)mouseY / height * 2.0F - 1.0F, -1.0F, 1.0F);
         parallaxX = Glass.approach(parallaxX, targetX, PARALLAX_SPEED, delta);
         parallaxY = Glass.approach(parallaxY, targetY, PARALLAX_SPEED, delta);
      } else {
         parallaxX = 0.0F;
         parallaxY = 0.0F;
      }

      // The network carries on across visits (Options and back), so it only fades in the first time.
      networkShown = animated ? Math.min(1.0F, networkShown + delta / 0.9F) : 1.0F;
      g.fill(0, 0, width, height, base());
      // Depth: a broad, soft lift of the base towards the middle, tinted by the accent; the corners stay darkest.
      glow(g, width * 0.56F, height * 0.40F, width * 1.25F, height * 1.45F, Glass.lerp(Glass.surface(0), Glass.accent(), 0.16F));
   }

   /** The soft radial glow texture, tinted {@code color}, centred on ({@code cx}, {@code cy}) and {@code w} x {@code h} GUI units large. */
   private static void glow(GuiGraphicsExtractor g, float cx, float cy, float w, float h, int color) {
      int x = Math.round(cx - w * 0.5F);
      int y = Math.round(cy - h * 0.5F);
      g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, 0.0F, 0.0F, Math.round(w), Math.round(h), GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
   }

   /**
    * Draws the shared network for a {@code width} x {@code height} GUI at {@code scale}. With {@code calm}, points
    * and links inside the rectangle {@code zx1..zx2}, {@code zy1..zy2} (window pixels) fade out.
    */
   private static void extractNetwork(
      GuiGraphicsExtractor g, int width, int height, int scale, float delta, float zx1, float zy1, float zx2, float zy2, boolean calm
   ) {
      float shown = Glass.easeInOut(networkShown);
      if (shown <= 0.01F) {
         return;
      }

      int pw = width * scale;
      int ph = height * scale;
      NetworkField f = field(pw, ph);
      f.update(delta, parallaxX, parallaxY);
      float soft = 36.0F * scale;
      int count = f.count();
      for (int i = 0; i < count; i++) {
         SHADE[i] = calm ? NetworkField.calm(f.x(i), f.y(i), zx1, zy1, zx2, zy2, soft, CALM_FLOOR) * shown : shown;
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
      boolean animated = Glass.animations();
      long elapsed = Util.getMillis() - this.openedAt;
      for (int i = 0; i < this.entering.size(); i++) {
         float t = animated ? Glass.easeOutCubic(TitleLayout.entrance(elapsed, this.enterOrder.get(i), ENTER_STAGGER_MS, ENTER_MS)) : 1.0F;
         AbstractWidget widget = this.entering.get(i);
         widget.setAlpha(t);
         float slide = (1.0F - t) * ENTER_SLIDE;
         if (widget instanceof GlassButton button) {
            button.setSlide(slide);
         } else if (widget instanceof NewsCard card) {
            card.slide = slide;
         }
      }

      TitleLayout l = this.layout;
      float head = animated ? Glass.easeOutCubic(TitleLayout.entrance(elapsed, 0, ENTER_STAGGER_MS, ENTER_MS)) : 1.0F;
      if (head > 0.05F && l.header() != TitleLayout.HEADER_NONE) {
         this.extractHeader(g, l, head);
      }

      if (this.fade > 0.1F) {
         int textY = UiFont.centerY(l.footerY(), TitleLayout.FOOTER_HEIGHT - 2);
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

   /** The emblem beside the name in the large UI font, with the sandbox state as one quiet line under it. */
   private void extractHeader(GuiGraphicsExtractor g, TitleLayout l, float shown) {
      boolean full = l.header() == TitleLayout.HEADER_FULL;
      FontSize size = full ? FontSize.HERO : FontSize.TITLE;
      String name = this.title.getString();
      int nameWidth = UiFont.width(name, size, FontWeight.BOLD);
      int logo = l.logoSize();
      int group = (logo > 0 ? logo + TitleLayout.HEADER_GAP : 0) + nameWidth;
      int x = l.headerX(group);
      float slide = (1.0F - shown) * ENTER_SLIDE;
      g.pose().pushMatrix();
      g.pose().translate(0.0F, slide);
      if (logo > 0) {
         // Drawn in window pixels at a whole ratio of the texture, so it stays sharp at every GUI scale.
         int scale = Math.max(1, this.minecraft.getWindow().getGuiScale());
         int pixels = l.logoPixels();
         int px = x * scale + (logo * scale - pixels) / 2;
         int py = l.headerY() * scale + (l.headerHeight() * scale - pixels) / 2;
         g.pose().pushMatrix();
         g.pose().scale(1.0F / scale, 1.0F / scale);
         g.blit(
            RenderPipelines.GUI_TEXTURED,
            GeminiMark.LOGO,
            px,
            py,
            0.0F,
            0.0F,
            pixels,
            pixels,
            TitleLayout.LOGO_TEXTURE,
            TitleLayout.LOGO_TEXTURE,
            TitleLayout.LOGO_TEXTURE,
            TitleLayout.LOGO_TEXTURE,
            Glass.withAlpha(0xFFFFFF, Math.round(255.0F * shown))
         );
         g.pose().popMatrix();
         x += logo + TitleLayout.HEADER_GAP;
      }

      UiFont.draw(g, name, x, l.nameY(), Glass.fade(Glass.text(), shown), size, FontWeight.BOLD, false);
      if (full) {
         UiFont.draw(g, this.tagline, x + 1, l.taglineY(), Glass.fade(Glass.textDim(), shown), FontSize.BODY, FontWeight.REGULAR, false);
      }

      g.pose().popMatrix();
   }

   // ---- the "what's new" card -------------------------------------------------------------------------------

   /** The small card in the bottom-right corner: a caption, the first points of the changelog, and where it leads. */
   private static final class NewsCard extends Button {
      static final int POINTS = 3;
      private static final int PAD = 10;
      private static final int LINE = 10;
      private static final int POINT_GAP = 5;
      private static final int RADIUS = 8;
      private final List<List<String>> lines = new ArrayList<>();
      private final Glass.Hover state = new Glass.Hover();
      private final String caption = Component.translatable("gemini.title.news").getString();
      private final String more = Component.translatable("gemini.title.news.more").getString();
      private long lastFrame;
      float slide;

      /** {@code bottom} is the card's bottom edge; its height follows from the wrapped text. */
      NewsCard(int x, int bottom, int width, List<String> points, Button.OnPress onPress) {
         super(x, 0, width, 0, Component.translatable("gemini.title.news"), onPress, DEFAULT_NARRATION);
         int textWidth = width - 2 * PAD - 8;
         int height = PAD + UiFont.capHeight(FontSize.SMALL) + 9;
         for (String point : points) {
            List<String> wrapped = GlassWindow.wrap(point, textWidth, 2, FontSize.SMALL, FontWeight.REGULAR);
            this.lines.add(wrapped);
            height += wrapped.size() * LINE + POINT_GAP;
         }

         height += 3 + UiFont.capHeight(FontSize.SMALL) + PAD;
         this.setHeight(height);
         this.setY(bottom - height);
      }

      @Override
      protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
         long now = Util.getMillis();
         float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
         this.lastFrame = now;
         this.state.update(this.isHoveredOrFocused(), false, delta);
         float a = this.alpha;
         if (a <= 0.05F) {
            return;
         }

         float lit = this.state.hover;
         int x1 = this.getX();
         int y1 = this.getY();
         int x2 = this.getRight();
         int y2 = this.getBottom();
         int accent = Glass.accent();
         g.pose().pushMatrix();
         g.pose().translate(0.0F, this.slide);
         Glass.roundRect(g, x1, y1, x2, y2, RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, Math.round(9.0F + 8.0F * lit)), a));
         Glass.roundOutline(g, x1, y1, x2, y2, RADIUS, Glass.fade(Glass.lerp(Glass.withAlpha(0xFFFFFF, 18), Glass.withAlpha(accent, 120), lit), a));
         int x = x1 + PAD;
         int y = y1 + PAD;
         UiFont.draw(g, this.caption, x, y, Glass.fade(Glass.text(), a), FontSize.SMALL, FontWeight.BOLD, false);
         UiFont.drawRight(g, Gemini.VERSION, x2 - PAD, y, Glass.fade(Glass.textFaint(), a), FontSize.SMALL, FontWeight.REGULAR, false);
         y += UiFont.capHeight(FontSize.SMALL) + 9;
         for (List<String> point : this.lines) {
            Glass.roundRect(g, x, y + 2, x + 3, y + 5, 1, Glass.fade(Glass.lerp(accent, Glass.accent2(), 0.5F), a));
            for (String line : point) {
               UiFont.draw(g, line, x + 8, y, Glass.fade(Glass.textDim(), a), FontSize.SMALL, FontWeight.REGULAR, false);
               y += LINE;
            }

            y += POINT_GAP;
         }

         y += 3;
         int moreColor = Glass.fade(Glass.lerp(Glass.textFaint(), Glass.lerp(accent, Glass.text(), 0.3F), lit), a);
         UiFont.draw(g, this.more, x, y, moreColor, FontSize.SMALL, FontWeight.MEDIUM, false);
         int arrowX = x + UiFont.width(this.more, FontSize.SMALL, FontWeight.MEDIUM) + 1 + Math.round(2.0F * lit);
         GlassIcon.CHEVRON.draw(g, arrowX, y - 2, moreColor);
         g.pose().popMatrix();
      }
   }

   // ---- the theme swatches ----------------------------------------------------------------------------------

   /**
    * One small disc per theme, in the footer: clicking a disc switches the whole client to that theme (the same
    * ClickGUI "Theme" setting the menus offer); Enter or Space while it has focus moves to the next one. The
    * current theme's disc has a ring around it.
    */
   private static final class ThemeSwatches extends Button {
      private static final int DISC = 8;
      private static final int STEP = 12;
      private final String[] names = Theme.themeNames();
      private final float[] hover;
      private int hovered = -2;
      private long lastFrame;

      ThemeSwatches(int x, int y) {
         super(x, y, 0, DISC + 4, Component.translatable("gemini.title.theme"), pressed -> {
         }, DEFAULT_NARRATION);
         int count = 0;
         for (String name : this.names) {
            if (Theme.preset(name) != null) {
               count++;
            }
         }

         this.hover = new float[this.names.length];
         this.setWidth(count * STEP + 2);
      }

      /** Index (into {@link #names}) of the disc under {@code mouseX}, or -1. Only preset themes have a disc. */
      private int at(double mouseX, double mouseY) {
         if (mouseY < this.getY() || mouseY >= this.getBottom()) {
            return -1;
         }

         int slot = (int)Math.floor((mouseX - this.getX() - 1) / STEP);
         int seen = 0;
         for (int i = 0; i < this.names.length; i++) {
            if (Theme.preset(this.names[i]) != null && seen++ == slot) {
               return mouseX >= this.getX() ? i : -1;
            }
         }

         return -1;
      }

      @Override
      public void onPress(InputWithModifiers input) {
         ClickGuiModule style = Theme.style();
         if (style == null) {
            return;
         }

         String next = null;
         if (input instanceof MouseButtonEvent click) {
            int index = this.at(click.x(), click.y());
            next = index < 0 ? null : this.names[index];
         } else {
            // Keyboard: the next preset after the current theme.
            String current = style.themeName();
            int start = 0;
            for (int i = 0; i < this.names.length; i++) {
               if (this.names[i].equals(current)) {
                  start = i + 1;
               }
            }

            for (int i = 0; i < this.names.length && next == null; i++) {
               String candidate = this.names[(start + i) % this.names.length];
               next = Theme.preset(candidate) != null ? candidate : null;
            }
         }

         if (next != null && !next.equals(style.themeName())) {
            for (Setting<?> setting : style.settings()) {
               if (setting instanceof ModeSetting mode && "Theme".equals(mode.name())) {
                  mode.set(next);
               }
            }

            Gemini gemini = Gemini.getInstance();
            if (gemini != null && gemini.config() != null) {
               gemini.config().save();
            }
         }
      }

      @Override
      protected MutableComponent createNarrationMessage() {
         ClickGuiModule style = Theme.style();
         return wrapDefaultNarrationMessage(Component.translatable("gemini.title.theme.narration", style == null ? Theme.DEFAULT : style.themeName()));
      }

      @Override
      protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
         long now = Util.getMillis();
         float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
         this.lastFrame = now;
         float a = this.alpha;
         if (a <= 0.05F) {
            return;
         }

         ClickGuiModule style = Theme.style();
         String current = style == null ? Theme.DEFAULT : style.themeName();
         int over = this.isHovered() ? this.at(mouseX, mouseY) : -1;
         if (over != this.hovered) {
            this.hovered = over;
            this.setTooltip(UiFont.tooltip(over < 0 ? Component.translatable("gemini.title.theme.tooltip") : Component.translatable("gemini.title.theme.named", this.names[over])));
         }

         int x = this.getX() + 1;
         int y = this.getY() + 2;
         for (int i = 0; i < this.names.length; i++) {
            Theme.Palette palette = Theme.preset(this.names[i]);
            if (palette == null) {
               continue;
            }

            this.hover[i] = Glass.approach(this.hover[i], i == over ? 1.0F : 0.0F, Theme.HOVER_SPEED, delta);
            boolean selected = this.names[i].equals(current);
            float strength = selected ? 1.0F : 0.55F + 0.45F * this.hover[i];
            Glass.roundGradient(g, x + 1, y, x + 1 + DISC, y + DISC, DISC / 2, Glass.fade(palette.accent, a * strength), Glass.fade(palette.accent2, a * strength));
            if (selected || this.hover[i] > 0.02F || i == over && this.isFocused()) {
               int ring = Glass.withAlpha(selected ? Glass.text() : palette.accent, Math.round((selected ? 220.0F : 150.0F * this.hover[i]) * a));
               Glass.roundOutline(g, x - 1, y - 2, x + 3 + DISC, y + DISC + 2, (DISC + 4) / 2, ring);
            }

            x += STEP;
         }

      }
   }
}
