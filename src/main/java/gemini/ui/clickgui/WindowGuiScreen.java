package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.ListSetting;
import gemini.module.setting.Setting;
import gemini.ui.Fuzzy;
import gemini.ui.GeminiMark;
import gemini.ui.Glass;
import gemini.ui.Theme;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.ListControl;
import gemini.ui.clickgui.controls.SettingControl;
import gemini.ui.clickgui.controls.Widgets;
import gemini.ui.clickgui.window.BaritonePage;
import gemini.ui.clickgui.window.DashboardPage;
import gemini.ui.clickgui.window.KeybindsPage;
import gemini.ui.clickgui.window.ProfilesPage;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The Gemini window menu: one centred, rounded window over the blurred and dimmed game. A sidebar lists the
 * categories and the pages (presets, keybinds, Baritone, mods) with a selection pill that slides between them; the
 * top bar holds the search, the filters and the theme swatches; the module area is a grid of cards, each with its
 * name, a one-line description and a switch. A click on a card opens its settings in place, under the card.
 *
 * <p>Drawing and hit-testing share one pass: every clickable element registers a hit rectangle while it is drawn,
 * so input always matches what is on screen. Pages register theirs through {@link DashboardPage.Host}.
 *
 * <p>Everything that moves is eased and frame-rate independent (the window scales and fades in and out, the pill
 * and the switches ride springs, cards unfold, the list glides after the wheel); with the ClickGUI "Animations"
 * setting off it all happens at once.
 */
public final class WindowGuiScreen extends Screen implements DashboardPage.Host {
   private static final int SIDEBAR = 126;
   private static final int TOP_BAR = 40;
   private static final int HEADING = 22;
   private static final int CARD_H = 36;
   private static final int CARD_GAP = 6;
   private static final int RADIUS = 8;
   /** Gap between the content column's edges and everything in it (the pages use the same). */
   private static final int INSET = 10;
   /** Height of one text link in the sidebar's bottom group. */
   private static final int LINK_ROW = 13;
   private static final int FOOTER = 20;
   private static final int BIND_ROW = 20;
   private static final int MORE_ROW = 18;
   private static final String FAVORITES = "Favorites";
   private static final String[] FILTERS = new String[]{"All", "On", "Bound", FAVORITES};
   private static final Page[] PAGE_TABS = {Page.PROFILES, Page.KEYBINDS, Page.BARITONE, Page.MODS};
   private static Category lastCategory = Category.COMBAT;
   private static String lastModule;
   private static String rememberedSearch = "";
   private static Page lastPage = Page.MODULES;
   /** Modules whose folded settings were opened with "More settings", by name; kept while the game runs. */
   private static final java.util.Set<String> unfolded = new java.util.HashSet<>();
   private final Screen parent;
   /** The screen this menu was opened over; without a world its background is drawn behind the blur. */
   private final Screen backdrop;
   private final List<WindowGuiScreen.Hit> hits = new ArrayList<>();
   private final Map<Module, WindowGuiScreen.Card> cards = new HashMap<>();
   private final Map<Setting<?>, SettingControl> controls = new IdentityHashMap<>();
   private final Map<Module, Fuzzy.Match> matches = new HashMap<>();
   private final Glass.Hover[] tabHover = new Glass.Hover[Category.values().length + PAGE_TABS.length];
   private final Glass.Hover searchHover = new Glass.Hover();
   private final Glass.Hover themeHover = new Glass.Hover();
   private final Glass.Spring pillY = new Glass.Spring(Float.NaN, 240.0F, 0.86F);
   private final Glass.Spring filterX = new Glass.Spring(Float.NaN, 300.0F, 0.9F);
   private final Glass.Spring filterW = new Glass.Spring(Float.NaN, 300.0F, 0.9F);
   private final SmoothScroll listScroll = new SmoothScroll();
   private final ProfilesPage profilesPage = new ProfilesPage(this);
   private final KeybindsPage keybindsPage = new KeybindsPage(this);
   private final BaritonePage baritonePage = new BaritonePage(this);
   private final gemini.ui.mods.ModsPage modsPage = new gemini.ui.mods.ModsPage(this);
   private SettingControl activeControl;
   private long lastFrame = Util.getMillis();
   private float dt;
   /** 0 while closed, 1 when fully open; eases both ways. */
   private float animationProgress;
   private boolean closing;
   private float open;
   private float zoom = 1.0F;
   private float uiScale = 1.0F;
   private int vw;
   private int vh;
   private float pillAlpha;
   private Page page;
   private Category category = lastCategory;
   /** The card the keyboard is on (and the one last clicked). */
   private Module selected;
   /** The card whose settings are open, or null. */
   private Module expanded;
   private boolean keyboardFocus;
   private boolean mouseDown;
   private String search = "";
   private boolean searchFocused;
   private String filter = "All";
   private boolean themeOpen;
   private float themeAnim;
   private int listViewTop;
   private int listViewBottom;
   private int listX;
   private int listW;
   private int columns = 1;
   private int[] cardHeights = new int[0];
   private int[] cardTops = new int[0];
   private int contentHeight;
   private Module binding;
   private boolean bindingMenu;
   /** A setting the search matched in the module just opened: the list scrolls to it once. */
   private String revealSetting;
   private int revealOffset = -1;
   private boolean revealCard;
   private String tip;
   private String flash;
   private long flashUntil;
   private List<Module> shown = List.of();

   /** Which part of the menu is showing. */
   public enum Page {
      MODULES("Modules"),
      PROFILES("Presets & Configs"),
      KEYBINDS("Keybinds"),
      BARITONE("Baritone"),
      MODS("Mods");

      public final String label;

      Page(String label) {
         this.label = label;
      }
   }

   /** The eased state of one module card. */
   private static final class Card {
      final Glass.Hover hover = new Glass.Hover();
      final Glass.Spring on = new Glass.Spring(Float.NaN, 260.0F, 0.82F);
      float expand;
   }

   /** Opens on the page that was showing last time. */
   public WindowGuiScreen() {
      this(null, null);
   }

   /**
    * Opens on {@code page} (null: the page that was showing last time). {@code parent} is shown again when the
    * menu closes; null closes to the game or the title screen as usual.
    */
   public WindowGuiScreen(Page page, Screen parent) {
      super(Component.literal("Gemini Client"));
      this.parent = parent;
      this.backdrop = Menus.backdropFor(parent != null ? parent : Minecraft.getInstance().gui.screen());
      this.page = page == null ? lastPage : page;
      for (int i = 0; i < this.tabHover.length; i++) {
         this.tabHover[i] = new Glass.Hover();
      }

      ClickGuiModule style = style();
      if (style != null && style.keepSearch()) {
         this.search = rememberedSearch;
      }

      this.currentPage().ifPresent(DashboardPage::opened);
   }

   /**
    * Opens the Modules page on {@code module}'s category with its settings open (the search cleared), as the
    * quick-search palette's Shift+Enter does. {@code parent} is shown again when the menu closes.
    */
   public static WindowGuiScreen forModule(Module module, Screen parent) {
      WindowGuiScreen screen = new WindowGuiScreen(Page.MODULES, parent);
      if (module != null) {
         screen.category = module.getCategory();
         screen.selected = module;
         screen.expanded = module;
         screen.card(module).expand = 1.0F;
         screen.search = "";
         screen.filter = "All";
         screen.revealCard = true;
      }

      return screen;
   }

   @Override
   protected void init() {
      // Minecraft 26.3 reads keys through SDL, which only sends typed characters to a screen that asks for them.
      this.minecraft.textInputManager().startTextInput(this);
      ClickGuiModule style = style();
      float base = style == null ? 1.0F : style.scale();
      float scale = base;
      if (style == null || style.autoFit()) {
         scale = Math.min(base, Math.min(this.width / 480.0F, this.height / 330.0F));
      }

      this.uiScale = Mth.clamp(scale, 0.45F, 2.0F);
      this.vw = Math.round(this.width / this.uiScale);
      this.vh = Math.round(this.height / this.uiScale);
      if (this.selected == null && lastModule != null && Gemini.getInstance() != null) {
         this.selected = Gemini.getInstance().modules().getByName(lastModule).orElse(null);
      }
   }

   private java.util.Optional<DashboardPage> currentPage() {
      return java.util.Optional.ofNullable(this.pageFor(this.page));
   }

   private DashboardPage pageFor(Page p) {
      return switch (p) {
         case PROFILES -> this.profilesPage;
         case KEYBINDS -> this.keybindsPage;
         case BARITONE -> this.baritonePage;
         case MODS -> this.modsPage;
         default -> null;
      };
   }

   /** The screen whose background is drawn behind this menu while no world is loaded, or null. */
   Screen backdrop() {
      return this.backdrop;
   }

   private WindowGuiScreen.Card card(Module module) {
      return this.cards.computeIfAbsent(module, key -> new WindowGuiScreen.Card());
   }

   // ------------------------------------------------------------------ host for pages

   @Override
   public Font font() {
      return this.font;
   }

   @Override
   public float fade() {
      return this.open;
   }

   @Override
   public float dt() {
      return this.dt;
   }

   @Override
   public void hit(int x, int y, int w, int h, Object owner, DashboardPage.Click click) {
      this.hits.add(new WindowGuiScreen.Hit(WindowGuiScreen.Kind.PAGE, x, y, w, h, new WindowGuiScreen.PageHit(owner, click)));
   }

   @Override
   public void hit(int x, int y, int w, int h, int clipTop, int clipBottom, Object owner, DashboardPage.Click click) {
      this.hit(WindowGuiScreen.Kind.PAGE, x, y, w, h, new WindowGuiScreen.PageHit(owner, click), clipTop, clipBottom);
   }

   @Override
   public void tip(String text) {
      this.tip = text;
   }

   @Override
   public void notice(String text) {
      this.showNotice(text);
   }

   @Override
   public void beginBind(Module module) {
      if (module.isBindable()) {
         this.binding = module;
         this.bindingMenu = false;
      } else {
         this.showNotice(module.getName() + " can't have a key bind");
      }
   }

   @Override
   public Module bindingModule() {
      return this.binding;
   }

   @Override
   public Screen screen() {
      return this;
   }

   // ------------------------------------------------------------------ rendering

   /**
    * The backdrop: the game (or, without a world, the screen this menu was opened over, else the panorama), blurred
    * and dimmed. The dim fades with the window; the ClickGUI "Blur" and "Dim" switches turn either part off.
    */
   @Override
   public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      long now = Util.getMillis();
      this.dt = Math.min(0.05F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      // Glass.approach jumps straight to the target when animations are off
      this.animationProgress = Glass.approach(this.animationProgress, this.closing ? 0.0F : 1.0F, this.closing ? 20.0F : 11.0F, this.dt);
      this.open = Mth.clamp(this.animationProgress, 0.0F, 1.0F);
      this.zoom = this.open >= 0.999F ? 1.0F : 0.95F + 0.05F * Glass.easeOutCubic(this.open);
      if (this.minecraft.level == null) {
         if (this.backdrop != null) {
            this.backdrop.extractBackground(g, -1, -1, partialTick);
         } else {
            this.extractPanorama(g, partialTick);
         }
      }

      ClickGuiModule style = style();
      Glass.backdrop(g, style == null || style.dim() ? 0.5F : 0.0F, this.open);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int rawMouseX, int rawMouseY, float partialTick) {
      long now = Util.getMillis();
      ClickGuiModule style = style();
      this.hits.clear();
      this.tip = null;
      if (this.closing && this.open <= 0.02F) {
         super.extractRenderState(g, rawMouseX, rawMouseY, partialTick);
         return;
      }

      Category tint = this.page == Page.MODULES && this.search.isBlank() && !FAVORITES.equals(this.filter) ? this.category : null;
      int accent = style == null ? Glass.accent() : style.accentFor(tint);
      int accent2 = style == null ? Glass.accent2() : style.secondaryFor(tint);
      float glass = style == null ? 0.9F : style.opacity();
      int mouseX = (int)Math.floor(this.vx(rawMouseX));
      int mouseY = (int)Math.floor(this.vy(rawMouseY));
      g.pose().pushMatrix();
      g.pose().scale(this.uiScale, this.uiScale);
      g.pose().translate(this.vw / 2.0F * (1.0F - this.zoom), this.vh / 2.0F * (1.0F - this.zoom));
      g.pose().scale(this.zoom, this.zoom);
      int ww = Math.min(Mth.clamp(this.vw - 40, 440, 720), this.vw - 8);
      int wh = Math.min(Mth.clamp(this.vh - 36, 264, 430), this.vh - 8);
      int wx = (this.vw - ww) / 2;
      int wy = (this.vh - wh) / 2;
      int bodyAlpha = Math.round(255.0F * glass);
      // One window: a soft shadow, the sidebar a shade darker than the content, a hairline edge.
      Glass.shadow(g, wx, wy, wx + ww, wy + wh, RADIUS, 22, 8, 0.55F * this.open);
      Glass.roundRect(g, wx, wy, wx + ww, wy + wh, RADIUS, this.c(Glass.surface(1), bodyAlpha));
      // the sidebar is the same window, shaded: one clip, so there is never a seam between the two halves
      g.enableScissor(wx, wy, wx + SIDEBAR, wy + wh);
      Glass.roundRect(g, wx, wy, wx + SIDEBAR + RADIUS * 2, wy + wh, RADIUS, this.c(Glass.surface(0), 215));
      g.disableScissor();
      Glass.roundOutline(g, wx, wy, wx + ww, wy + wh, RADIUS, this.c(Glass.text(), 20));
      this.drawSidebar(g, wx, wy, wh, mouseX, mouseY, accent, accent2);
      int cx = wx + SIDEBAR;
      int cw = ww - SIDEBAR;
      int bodyBottom = wy + wh - FOOTER;
      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         pageView.render(g, cx, wy + 2, cw, bodyBottom - wy - 2, mouseX, mouseY);
      } else {
         this.drawModulesPage(g, cx, wy, cw, bodyBottom, mouseX, mouseY, accent, accent2, now);
      }

      this.drawFooter(g, cx, bodyBottom, cw, accent2, now);
      this.drawThemePopover(g, wx + ww - INSET, wy + TOP_BAR - 6, mouseX, mouseY);
      g.pose().popMatrix();
      super.extractRenderState(g, rawMouseX, rawMouseY, partialTick);
   }

   // ---- sidebar ------------------------------------------------------------------------------------------

   private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int h, int mouseX, int mouseY, int accent, int accent2) {
      GeminiMark.draw(g, x + 13, y + 12, 16, accent, this.open);
      this.txt(g, "Gemini", x + 35, UiFont.centerY(y + 12, 16, FontSize.LARGE), Glass.text(), FontSize.LARGE, FontWeight.BOLD);
      int tabsTop = y + 40;
      if (!SingleplayerGate.allowed()) {
         // Modules can be configured anywhere, but only run in a private single-player world.
         String label = "Settings only";
         int lw = Widgets.chipWidth(label);
         Widgets.chip(g, label, x + 13 + lw, tabsTop - 1, 12, Widgets.raised(), Glass.textDim(), this.open);
         if (inside(mouseX, mouseY, x + 13, tabsTop - 1, lw, 12)) {
            this.tip = plain(SingleplayerGate.denialMessage());
         }

         tabsTop += 17;
      }

      int linksTop = y + h - 10 - 3 * LINK_ROW;
      int count = Category.values().length + PAGE_TABS.length;
      int step = Mth.clamp((linksTop - 8 - tabsTop - 10) / count, 13, 21);
      int th = step - 2;
      List<Object> tabs = this.tabs();
      float targetY = Float.NaN;
      int iy = tabsTop;
      // pass 1: where the selection pill belongs, so it is drawn under the labels
      for (Object tab : tabs) {
         if (tab == PAGE_TABS[0]) {
            iy += 10;
         }

         if (this.tabActive(tab)) {
            targetY = iy - y;
         }

         iy += step;
      }

      boolean showPill = !Float.isNaN(targetY);
      this.pillAlpha = Glass.approach(this.pillAlpha, showPill ? 1.0F : 0.0F, 14.0F, this.dt);
      if (showPill) {
         this.pillY.update(targetY, this.dt);
      }

      if (!Float.isNaN(this.pillY.value) && this.pillAlpha > 0.01F) {
         int top = y + Math.round(this.pillY.value);
         float fade = this.pillAlpha * this.open;
         Glass.roundGradient(
            g, x + 8, top, x + SIDEBAR - 8, top + th, 5, Glass.fade(Glass.withAlpha(accent, 62), fade), Glass.fade(Glass.withAlpha(accent2, 18), fade)
         );
         Glass.roundRect(g, x + 8, top + 4, x + 10, top + th - 4, 1, Glass.fade(accent, fade));
      }

      iy = tabsTop;
      int index = 0;
      for (Object tab : tabs) {
         if (tab == PAGE_TABS[0]) {
            iy += 10;
         }

         this.drawTab(g, tab, this.tabHover[index++], x, iy, th, mouseX, mouseY, accent);
         iy += step;
      }

      // The tools under the tabs are quiet text links, two to a row, lined up with the tab labels.
      int half = (SIDEBAR - 16) / 2;
      this.sidebarLink(g, x + 8, linksTop, half, "Search", WindowGuiScreen.Kind.PALETTE, mouseX, mouseY, accent, false);
      this.sidebarLink(g, x + 8 + half, linksTop, half, "Guide", WindowGuiScreen.Kind.HELP, mouseX, mouseY, accent, false);
      this.sidebarLink(g, x + 8, linksTop + LINK_ROW, half, "Edit HUD", WindowGuiScreen.Kind.HUD_EDIT, mouseX, mouseY, accent, false);
      this.sidebarLink(g, x + 8 + half, linksTop + LINK_ROW, half, "Panels", WindowGuiScreen.Kind.LAYOUT, mouseX, mouseY, accent, false);
      String menuKey = this.bindingMenu ? "Press a key..." : "Menu key: " + GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString();
      this.sidebarLink(g, x + 8, linksTop + 2 * LINK_ROW, SIDEBAR - 16, menuKey, WindowGuiScreen.Kind.MENU_KEY, mouseX, mouseY, accent, this.bindingMenu);
   }

   private List<Object> tabs() {
      List<Object> tabs = new ArrayList<>(List.of(Category.values()));
      tabs.addAll(List.of(PAGE_TABS));
      return tabs;
   }

   private boolean tabActive(Object tab) {
      if (tab instanceof Category cat) {
         return this.page == Page.MODULES && this.search.isBlank() && !FAVORITES.equals(this.filter) && cat == this.category;
      }

      return tab == this.page;
   }

   private void drawTab(GuiGraphicsExtractor g, Object tab, Glass.Hover hover, int x, int iy, int th, int mouseX, int mouseY, int accent) {
      boolean active = this.tabActive(tab);
      boolean over = inside(mouseX, mouseY, x + 8, iy, SIDEBAR - 16, th);
      hover.update(over, over && this.mouseDown, active, this.dt);
      if (!active && hover.hover > 0.01F) {
         Glass.roundRect(g, x + 8, iy, x + SIDEBAR - 8, iy + th, 5, this.c(Glass.text(), Math.round(12.0F * hover.hover)));
      }

      int ty = UiFont.centerY(iy, th);
      int textColor = Glass.lerp(Glass.textDim(), Glass.text(), Math.max(hover.focus, hover.hover * 0.7F));
      int iconColor = Glass.lerp(Glass.lerp(Glass.textFaint(), Glass.textDim(), hover.hover), accent, hover.focus);
      int right = x + SIDEBAR - 14;
      String name;
      String key;
      if (tab instanceof Category cat) {
         key = cat.name();
         List<Module> list = modules(cat);
         int on = 0;
         for (Module module : list) {
            if (module.isEnabled() && module.isListed()) {
               on++;
            }
         }

         // Only the number switched on, and only when there is one; the total is not worth a badge.
         if (on > 0) {
            String count = Integer.toString(on);
            int cw = UiFont.width(count, FontSize.SMALL, FontWeight.REGULAR);
            this.txt(g, count, right - cw, UiFont.centerY(iy, th, FontSize.SMALL), active ? Glass.textDim() : Glass.textFaint(), FontSize.SMALL, FontWeight.REGULAR);
            right -= cw + 4;
         }

         name = cat.displayName();
         this.hit(WindowGuiScreen.Kind.CATEGORY, x + 8, iy, SIDEBAR - 16, th, cat);
         if (over) {
            this.tip = list.size() + " modules, " + on + " on  ·  Tab / Shift+Tab switches tab";
         }
      } else {
         Page p = (Page)tab;
         key = p.name();
         name = p == Page.PROFILES ? "Presets" : p.label;
         if (p == Page.KEYBINDS && Gemini.getInstance() != null) {
            int conflicts = conflictCount();
            if (conflicts > 0) {
               // Meaningful: something to fix. Plain warning-coloured number, no pill.
               String count = Integer.toString(conflicts);
               int cw = UiFont.width(count, FontSize.SMALL, FontWeight.MEDIUM);
               this.txt(g, count, right - cw, UiFont.centerY(iy, th, FontSize.SMALL), Theme.current().warn, FontSize.SMALL, FontWeight.MEDIUM);
               right -= cw + 4;
            }
         } else if (p == Page.BARITONE && gemini.util.BaritoneBridge.isActive()) {
            Glass.roundRect(g, right - 5, iy + th / 2 - 2, right, iy + th / 2 + 3, 2, this.c(Theme.current().ok));
            right -= 8;
         }

         this.hit(WindowGuiScreen.Kind.PAGE_TAB, x + 8, iy, SIDEBAR - 16, th, p);
         if (over) {
            this.tip = switch (p) {
               case PROFILES -> "Saved profiles, export and import, built-in presets";
               case KEYBINDS -> "Every key bind grouped by key, with conflicts to fix";
               case MODS -> "Find mods and resource packs on Modrinth and manage your mods folder";
               default -> "Live pathing status and bot quick starts";
            };
         }
      }

      Icons.draw(g, Icons.named(key), x + 15, iy + (th - 10) / 2, 10, this.c(iconColor));
      FontWeight weight = active ? FontWeight.MEDIUM : FontWeight.REGULAR;
      this.txt(g, UiFont.ellipsize(name, right - (x + 31) - 2, FontSize.BODY, weight), x + 31, ty, textColor, FontSize.BODY, weight);
   }

   /** Conflicting keys for the sidebar badge: the same cached count the title screen and the pause dock show. */
   private static int conflictCount() {
      return gemini.input.BindWarnings.conflictCount(Gemini.getInstance().modules());
   }

   private void sidebarLink(GuiGraphicsExtractor g, int x, int y, int w, String label, WindowGuiScreen.Kind kind, int mouseX, int mouseY, int accent, boolean active) {
      boolean over = inside(mouseX, mouseY, x, y, w, LINK_ROW);
      String text = UiFont.ellipsize(label, w - 8, FontSize.SMALL, FontWeight.REGULAR);
      int color = active ? accent : (over ? Glass.text() : Glass.textFaint());
      this.txt(g, text, x + 7, UiFont.centerY(y, LINK_ROW, FontSize.SMALL), color, FontSize.SMALL, FontWeight.REGULAR);
      this.hit(kind, x, y, w, LINK_ROW, null);
      if (over) {
         this.tip = switch (kind) {
            case MENU_KEY -> "Click, then press the key that opens this menu";
            case PALETTE -> "Quick search over modules, settings, commands and pages (" + GeminiKeys.paletteHint() + ")";
            case HELP -> "What's new, controls, every command and about";
            case LAYOUT -> "Switch to the draggable category panels (change back in ClickGUI > Layout)";
            default -> "Drag HUD widgets around the screen";
         };
      }
   }

   // ---- modules page -------------------------------------------------------------------------------------

   private void drawModulesPage(GuiGraphicsExtractor g, int cx, int wy, int cw, int bodyBottom, int mouseX, int mouseY, int accent, int accent2, long now) {
      this.shown = this.visibleModules();
      if (this.selected != null && !this.shown.contains(this.selected) && this.search.isBlank()) {
         this.selected = null;
      }

      if (this.expanded != null && !this.shown.contains(this.expanded)) {
         this.expanded = null;
      }

      this.drawTopBar(g, cx, wy, cw, mouseX, mouseY, accent, now);
      this.drawHeading(g, cx, wy + TOP_BAR, cw);
      this.listX = cx + INSET;
      this.listW = cw - INSET * 2;
      this.listViewTop = wy + TOP_BAR + HEADING;
      this.listViewBottom = bodyBottom;
      this.drawModuleGrid(g, mouseX, mouseY, accent, accent2);
   }

   private void drawTopBar(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, int accent, long now) {
      int barY = y + 10;
      int barH = 20;
      int right = x + w - INSET;
      // theme button: a dot in the current accent pair
      int themeL = right - barH;
      boolean overTheme = inside(mouseX, mouseY, themeL, barY, barH, barH);
      this.themeHover.update(overTheme, overTheme && this.mouseDown, this.themeOpen, this.dt);
      Glass.roundRect(g, themeL, barY, right, barY + barH, 5, this.c(this.themeHover.fill(Glass.surface(2), Widgets.raised())));
      Glass.roundGradient(g, themeL + 5, barY + 5, right - 5, barY + barH - 5, 5, this.c(Glass.accent()), this.c(Glass.accent2()));
      this.hit(WindowGuiScreen.Kind.THEME_BUTTON, themeL, barY, barH, barH, null);
      if (overTheme) {
         this.tip = "Theme: " + Theme.current().name() + "  ·  click to pick another";
      }

      right = themeL - 6;
      // filters: one segmented control, the highlight slides to the active one
      int[] widths = new int[FILTERS.length];
      int filtersW = 2;
      for (int i = 0; i < FILTERS.length; i++) {
         widths[i] = (FAVORITES.equals(FILTERS[i]) ? 9 : UiFont.width(FILTERS[i], FontSize.SMALL, FontWeight.MEDIUM)) + 14;
         filtersW += widths[i];
      }

      int bedL = right - filtersW;
      Glass.roundRect(g, bedL, barY, right, barY + barH, 5, this.c(Glass.surface(2)));
      int fx = bedL + 1;
      int activeX = fx;
      int activeW = widths[0];
      for (int i = 0; i < FILTERS.length; i++) {
         if (FILTERS[i].equals(this.filter)) {
            activeX = fx;
            activeW = widths[i];
         }

         fx += widths[i];
      }

      float px = this.filterX.update(activeX - bedL, this.dt);
      float pw = this.filterW.update(activeW, this.dt);
      Glass.roundRect(g, bedL + Math.round(px), barY + 1, bedL + Math.round(px + pw), barY + barH - 1, 4, this.c(Widgets.raised()));
      fx = bedL + 1;
      for (int i = 0; i < FILTERS.length; i++) {
         String f = FILTERS[i];
         boolean active = f.equals(this.filter);
         boolean over = inside(mouseX, mouseY, fx, barY, widths[i], barH);
         int color = active ? Glass.text() : (over ? Glass.lerp(Glass.textDim(), Glass.text(), 0.6F) : Glass.textDim());
         if (FAVORITES.equals(f)) {
            Icons.draw(g, active ? Icons.Icon.STAR : Icons.Icon.STAR_OUTLINE, fx + 7, barY + 5, 10, this.c(active ? accent : color));
         } else {
            this.txt(g, f, fx + 7, UiFont.centerY(barY, barH, FontSize.SMALL), color, FontSize.SMALL, FontWeight.MEDIUM);
         }

         this.hit(WindowGuiScreen.Kind.FILTER, fx, barY, widths[i], barH, f);
         if (over) {
            this.tip = switch (f) {
               case "On" -> "Only show modules that are switched on";
               case "Bound" -> "Only show modules with a key bind";
               case FAVORITES -> "Your starred modules from every category";
               default -> "Show every module";
            };
         }

         fx += widths[i];
      }

      // search takes the rest
      int sx = x + INSET;
      int sw = bedL - 6 - sx;
      boolean overSearch = inside(mouseX, mouseY, sx, barY, sw, barH);
      this.searchHover.update(overSearch, false, this.searchFocused, this.dt);
      Glass.roundRect(g, sx, barY, sx + sw, barY + barH, 5, this.c(this.searchHover.fill(Glass.surface(2), Widgets.raised())));
      if (this.searchHover.focus > 0.01F) {
         Glass.roundOutline(g, sx, barY, sx + sw, barY + barH, 5, this.c(accent, Math.round(150.0F * this.searchHover.focus)));
      }

      Icons.draw(g, Icons.Icon.SEARCH, sx + 6, barY + 5, 10, this.c(Glass.lerp(Glass.textFaint(), accent, this.searchHover.focus)));
      int textX = sx + 21;
      int room = sw - 21 - (this.search.isEmpty() ? 6 : 18);
      int textY = UiFont.centerY(barY, barH);
      if (this.search.isEmpty()) {
         this.txt(g, UiFont.ellipsize(this.searchFocused ? "Type to search" : "Search modules and settings", room), textX, textY, Glass.textFaint());
         if (this.searchFocused && now / 450L % 2L == 0L) {
            g.fill(textX - 1, barY + 5, textX, barY + barH - 5, this.c(Glass.text()));
         }
      } else {
         String text = this.search;
         while (UiFont.width(text) > room && text.length() > 1) {
            text = text.substring(1);
         }

         this.txt(g, text, textX, textY, Glass.text());
         if (this.searchFocused && now / 450L % 2L == 0L) {
            int caret = textX + UiFont.width(text) + 1;
            g.fill(caret, barY + 5, caret + 1, barY + barH - 5, this.c(Glass.text()));
         }

         boolean overClear = inside(mouseX, mouseY, sx + sw - 18, barY, 18, barH);
         Icons.draw(g, Icons.Icon.CLOSE, sx + sw - 14, barY + 6, 8, this.c(overClear ? Glass.text() : Glass.textFaint()));
      }

      this.hit(WindowGuiScreen.Kind.SEARCH, sx, barY, sw, barH, null);
      if (!this.search.isEmpty()) {
         this.hit(WindowGuiScreen.Kind.CLEAR, sx + sw - 18, barY, 18, barH, null);
      }

      if (overSearch) {
         this.tip = "Type anywhere to search  ·  setting names count too  ·  Ctrl+F focuses  ·  Esc clears";
      }
   }

   private void drawHeading(GuiGraphicsExtractor g, int x, int y, int w) {
      String heading = !this.search.isBlank()
         ? "Results for \"" + this.search.trim() + "\""
         : (FAVORITES.equals(this.filter) ? "Favorites" : this.category.displayName());
      int on = 0;
      for (Module module : this.shown) {
         if (module.isEnabled()) {
            on++;
         }
      }

      String count = this.shown.size() + (this.shown.size() == 1 ? " module" : " modules") + (on > 0 ? "  ·  " + on + " on" : "");
      int countW = UiFont.width(count, FontSize.SMALL, FontWeight.REGULAR);
      this.txt(g, count, x + w - INSET - 1 - countW, y + 6, Glass.textFaint(), FontSize.SMALL, FontWeight.REGULAR);
      this.txt(g, UiFont.ellipsize(heading, w - 40 - countW, FontSize.LARGE, FontWeight.MEDIUM), x + INSET + 1, y + 3, Glass.text(), FontSize.LARGE, FontWeight.MEDIUM);
   }

   private void drawModuleGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, int accent, int accent2) {
      int x = this.listX;
      int viewH = this.listViewBottom - this.listViewTop;
      int count = this.shown.size();
      if (count == 0) {
         String empty = this.search.isBlank()
            ? (FAVORITES.equals(this.filter) ? "No favourites yet. Hover a module and click its star." : "Nothing matches this filter.")
            : "No module or setting matches your search.";
         this.txt(g, empty, x + (this.listW - UiFont.width(empty)) / 2, this.listViewTop + 24, Glass.textFaint());
         return;
      }

      int gridW = this.listW;
      this.columns = CardGrid.columns(gridW, CARD_GAP);
      int cardW = CardGrid.cardWidth(gridW, this.columns, CARD_GAP);
      if (this.cardHeights.length < count) {
         this.cardHeights = new int[count + 16];
         this.cardTops = new int[count + 16];
      }

      this.revealOffset = -1;
      for (int i = 0; i < count; i++) {
         Module module = this.shown.get(i);
         WindowGuiScreen.Card card = this.card(module);
         card.expand = Glass.approach(card.expand, module == this.expanded ? 1.0F : 0.0F, 13.0F, this.dt);
         int height = CARD_H;
         if (card.expand > 0.001F) {
            int body = this.settingsBody(null, module, 0, 0, cardW, SettingControl.NO_MOUSE, SettingControl.NO_MOUSE, 0, 0, accent, accent2);
            height += Math.round(body * Glass.easeInOut(card.expand));
         }

         this.cardHeights[i] = height;
      }

      this.contentHeight = CardGrid.layout(this.cardHeights, count, this.columns, CARD_GAP, this.cardTops) + 8;
      if (this.revealCard && this.expanded != null) {
         // bring the card that was just opened (or the setting the search matched in it) into view, once
         int index = this.shown.indexOf(this.expanded);
         WindowGuiScreen.Card card = this.card(this.expanded);
         if (index >= 0 && (card.expand > 0.98F || !Glass.animations())) {
            int top = this.cardTops[index];
            if (this.revealOffset >= 0) {
               this.listScroll.reveal(top + CARD_H + this.revealOffset - 24, top + CARD_H + this.revealOffset + 44, viewH);
            } else {
               this.listScroll.reveal(top - 4, top + this.cardHeights[index] + 4, viewH);
            }

            this.revealCard = false;
            this.revealSetting = null;
         }
      }

      this.listScroll.update(this.dt, this.contentHeight - viewH, Glass.animations());
      int scroll = this.listScroll.pixels();
      g.enableScissor(x - 2, this.listViewTop, x + this.listW, this.listViewBottom);
      boolean inView = mouseY >= this.listViewTop && mouseY < this.listViewBottom;
      for (int i = 0; i < count; i++) {
         Module module = this.shown.get(i);
         int cardX = x + CardGrid.columnX(CardGrid.column(i, this.columns), gridW, this.columns, CARD_GAP);
         int cardY = this.listViewTop + this.cardTops[i] - scroll;
         int height = this.cardHeights[i];
         if (cardY + height < this.listViewTop || cardY > this.listViewBottom) {
            this.card(module).hover.update(false, false, this.dt);
            continue;
         }

         this.drawCard(g, module, cardX, cardY, cardW, height, inView ? mouseX : SettingControl.NO_MOUSE, mouseY, accent, accent2);
      }

      g.disableScissor();
      if (scroll > 0) {
         // cards scrolled under the heading fade out instead of being cut off by a hard edge
         int shade = Math.round(255.0F * Math.min(1.0F, scroll / 8.0F));
         Glass.gradientV(g, x - 2, this.listViewTop, x + this.listW + 2, this.listViewTop + 7, this.c(Glass.surface(1), shade), this.c(Glass.surface(1), 0));
      }

      Widgets.scrollbar(g, x + this.listW + 3, this.listViewTop + 2, viewH - 6, this.contentHeight, this.listScroll.value(), this.open);
   }

   /** A module that cannot be switched on right now (no world, sandbox off...). The ClickGUI module is never toggled. */
   private static boolean blocked(Module module) {
      return !(module instanceof ClickGuiModule) && !module.canActivate();
   }

   private void drawCard(GuiGraphicsExtractor g, Module module, int x, int y, int w, int h, int mouseX, int mouseY, int accent, int accent2) {
      WindowGuiScreen.Card card = this.card(module);
      boolean blocked = blocked(module);
      boolean switchedOn = !(module instanceof ClickGuiModule) && (blocked ? module.isSavedEnabled() : module.isEnabled());
      boolean over = mouseX != SettingControl.NO_MOUSE && inside(mouseX, mouseY, x, y, w, CARD_H);
      boolean focused = this.keyboardFocus && module == this.selected;
      card.hover.update(over, over && this.mouseDown, focused, this.dt);
      float on = Mth.clamp(card.on.update(switchedOn ? 1.0F : 0.0F, this.dt), 0.0F, 1.0F);
      float lit = blocked ? on * 0.35F : on;
      boolean searchMode = !this.search.isBlank();
      // The card: a raised surface; a module that is on gets a wash of the accent and an accent edge.
      int fill = card.hover.fill(Glass.surface(2), Glass.lerp(Glass.surface(2), Glass.surface(3), 0.7F));
      fill = Theme.mix(fill, accent, 0.11F * lit);
      Glass.roundRect(g, x, y, x + w, y + h, 6, this.c(fill));
      if (lit > 0.01F) {
         Glass.roundOutline(g, x, y, x + w, y + h, 6, this.c(accent, Math.round(76.0F * lit)));
      }

      if (card.hover.focus > 0.01F) {
         Glass.roundOutline(g, x - 1, y - 1, x + w + 1, y + h + 1, 7, this.c(accent2, Math.round(200.0F * card.hover.focus)));
      }

      // The card is registered before its switch so a click on the switch toggles instead of opening.
      this.hit(WindowGuiScreen.Kind.CARD, x, y, w, CARD_H, module, this.listViewTop, this.listViewBottom);
      int right = x + w - 10;
      if (!(module instanceof ClickGuiModule)) {
         int sx = right - Widgets.TOGGLE_W;
         Widgets.toggle(g, sx, y + (CARD_H - Widgets.TOGGLE_H) / 2, on, Glass.lerp(accent, accent2, 0.25F), !blocked, this.open);
         this.hit(WindowGuiScreen.Kind.SWITCH, sx - 5, y, Widgets.TOGGLE_W + 15, CARD_H, module, this.listViewTop, this.listViewBottom);
         right = sx - 7;
      }

      ClickGuiModule style = style();
      boolean starred = style != null && style.isFavorite(module);
      if (starred || card.hover.hover > 0.2F) {
         boolean overStar = over && mouseX >= right - 13 && mouseX < right + 3;
         int starColor = starred ? accent2 : (overStar ? Glass.text() : Glass.textFaint());
         Icons.draw(g, starred ? Icons.Icon.STAR : Icons.Icon.STAR_OUTLINE, right - 10, y + (CARD_H - 10) / 2, 10, this.c(starColor, starred ? 255 : Math.round(255.0F * card.hover.hover)));
         this.hit(WindowGuiScreen.Kind.STAR, right - 13, y, 16, CARD_H, module, this.listViewTop, this.listViewBottom);
         if (overStar) {
            this.tip = starred ? "Remove from favourites" : "Add to favourites";
         }

         right -= 15;
      }

      String key = this.binding == module ? "Press a key" : (module.getKeybind() == -1 ? null : GeminiKeys.displayName(module.getKeybind()));
      if (key != null && !"NONE".equals(key)) {
         boolean capturing = this.binding == module;
         String label = module.isHoldBind() && !capturing ? key + " hold" : key;
         right = Widgets.chip(
            g, label, right, y + (CARD_H - 12) / 2, 12, capturing ? accent : Glass.lerp(fill, Glass.text(), 0.09F), capturing ? Theme.onColor(accent) : Glass.textDim(), this.open
         ) - 5;
      }

      int textX = x + 11;
      int nameColor = blocked ? Glass.textFaint() : Glass.lerp(Glass.lerp(Glass.textDim(), Glass.text(), 0.5F + 0.5F * card.hover.hover), Glass.text(), on);
      this.txt(g, UiFont.ellipsize(module.getName(), right - textX - 4, FontSize.BODY, FontWeight.MEDIUM), textX, y + 8, nameColor, FontSize.BODY, FontWeight.MEDIUM);
      Fuzzy.Match match = searchMode ? this.matches.get(module) : null;
      String sub;
      int subColor = Glass.textFaint();
      if (match != null && match.setting() != null) {
         // the search found this module through one of its settings: say which
         sub = "Setting: " + match.setting();
         subColor = Glass.textDim();
      } else {
         sub = searchMode || FAVORITES.equals(this.filter) ? module.getCategory().displayName() + "  ·  " + module.getDescription() : module.getDescription();
      }

      this.txt(g, UiFont.ellipsize(sub, right - textX - 2, FontSize.SMALL, FontWeight.REGULAR), textX, y + 22, subColor, FontSize.SMALL, FontWeight.REGULAR);
      if (over && this.tip == null) {
         this.tip = blocked ? plain(module.blockedReason()) + "  ·  you can still change its settings" : module.getDescription();
      }

      int bodyH = h - CARD_H;
      if (bodyH > 0) {
         int clipTop = Math.max(this.listViewTop, y + CARD_H);
         int clipBottom = Math.min(this.listViewBottom, y + h);
         g.fill(x + 10, y + CARD_H, x + w - 10, y + CARD_H + 1, this.c(Widgets.divider()));
         if (clipBottom > clipTop) {
            g.enableScissor(x, y + CARD_H, x + w, y + h);
            this.settingsBody(g, module, x, y + CARD_H, w, mouseX, mouseY, clipTop, clipBottom, accent, accent2);
            g.disableScissor();
         }
      }
   }

   /** The visible settings of a module, in order. */
   private static List<Setting<?>> visibleSettings(Module module) {
      List<Setting<?>> settings = new ArrayList<>();
      for (Setting<?> setting : module.settings()) {
         if (setting.visible()) {
            settings.add(setting);
         }
      }

      return settings;
   }

   /**
    * The settings under an open card: the reason it cannot run here (if any), the key bind, the settings and the
    * "More settings" row. With {@code g} null nothing is drawn and no hit is registered: the call only measures.
    * Returns the full height.
    */
   private int settingsBody(
      GuiGraphicsExtractor g, Module module, int x, int y, int w, int mouseX, int mouseY, int clipTop, int clipBottom, int accent, int accent2
   ) {
      boolean draw = g != null;
      boolean live = draw && mouseX != SettingControl.NO_MOUSE && mouseY >= clipTop && mouseY < clipBottom;
      int ry = y + 5;
      boolean blocked = blocked(module);
      if (blocked) {
         if (draw) {
            String reason = plain(module.blockedReason());
            // Without a world the full reason doesn't fit the card; the short form says the same thing.
            String shownReason = this.minecraft.player == null ? "Can be switched on in a world" : reason;
            this.txt(g, UiFont.ellipsize(shownReason, w - 22, FontSize.SMALL, FontWeight.REGULAR), x + 11, ry + 3, Glass.textFaint(), FontSize.SMALL, FontWeight.REGULAR);
            if (live && inside(mouseX, mouseY, x, ry, w, 14)) {
               this.tip = reason + "  ·  settings changed here apply when it can run";
            }
         }

         ry += 14;
      }

      if (module.isBindable()) {
         if (draw) {
            this.drawBindRow(g, module, x + 3, ry, w - 6, live ? mouseX : SettingControl.NO_MOUSE, mouseY, accent, clipTop, clipBottom);
         }

         ry += BIND_ROW;
      }

      List<Setting<?>> settings = visibleSettings(module);
      int total = settings.size();
      if (this.revealSetting != null && module == this.expanded) {
         // the search jumped to a setting: unfold the module if that setting is past the fold
         for (int i = 0; i < total; i++) {
            if (settings.get(i).name().equals(this.revealSetting) && SettingDisclosure.needsUnfold(i, total)) {
               unfolded.add(module.getName());
            }
         }
      }

      // Long setting lists show the first few and a "More settings" row (see SettingDisclosure).
      boolean searching = !this.search.isBlank();
      boolean unfoldedNow = unfolded.contains(module.getName());
      int shownCount = SettingDisclosure.shown(total, unfoldedNow, searching);
      boolean moreRow = SettingDisclosure.foldable(total) && !searching;
      String query = this.search.trim();
      for (int i = 0; i < shownCount; i++) {
         Setting<?> setting = settings.get(i);
         SettingControl control = this.control(module, setting);
         if (control == null) {
            continue;
         }

         int rh = control.height();
         if (!draw && setting.name().equals(this.revealSetting) && module == this.expanded) {
            this.revealOffset = ry - y;
         }

         if (draw && ry + rh >= clipTop && ry <= clipBottom) {
            if (!query.isEmpty() && Fuzzy.substringScore(query, setting.name()) > 0) {
               // a setting the search matched: a wash of the accent so it stands out in the list
               Glass.roundRect(g, x + 5, ry, x + w - 5, ry + rh, 3, this.c(accent, 34));
            }

            boolean over = live && inside(mouseX, mouseY, x + 3, ry, w - 6, rh);
            control.setOpacity(this.open);
            control.setAccent(accent, accent2);
            control.render(g, x + 3, ry, w - 6, live ? mouseX : SettingControl.NO_MOUSE, live ? mouseY : SettingControl.NO_MOUSE, this.dt);
            this.hit(WindowGuiScreen.Kind.CONTROL, x + 3, ry, w - 6, rh, control, clipTop, clipBottom);
            if (over) {
               String hint = control.hint();
               this.tip = hint == null ? setting.description() : setting.description() + "  ·  " + hint;
            }
         }

         ry += rh;
      }

      if (moreRow) {
         if (draw) {
            String label = unfoldedNow ? "Fewer settings" : "More settings (" + (total - shownCount) + ")";
            boolean over = live && inside(mouseX, mouseY, x + 3, ry, w - 6, MORE_ROW);
            int color = over ? Glass.text() : Glass.textDim();
            Icons.draw(g, unfoldedNow ? Icons.Icon.CHEVRON_DOWN : Icons.Icon.CHEVRON_RIGHT, x + 10, ry + 5, 8, this.c(color));
            this.txt(g, label, x + 22, UiFont.centerY(ry, MORE_ROW, FontSize.SMALL), color, FontSize.SMALL, FontWeight.MEDIUM);
            this.hit(WindowGuiScreen.Kind.MORE, x + 3, ry, w - 6, MORE_ROW, module, clipTop, clipBottom);
         }

         ry += MORE_ROW;
      }

      if (total == 0 && !module.isBindable()) {
         if (draw) {
            this.txt(g, "No settings", x + 11, ry + 3, Glass.textFaint(), FontSize.SMALL, FontWeight.REGULAR);
         }

         ry += 14;
      }

      return ry + 5 - y;
   }

   private void drawBindRow(GuiGraphicsExtractor g, Module module, int x, int y, int w, int mouseX, int mouseY, int accent, int clipTop, int clipBottom) {
      boolean over = mouseX != SettingControl.NO_MOUSE && inside(mouseX, mouseY, x, y, w, BIND_ROW);
      Widgets.rowHover(g, x, y, w, BIND_ROW, over ? 1.0F : 0.0F, this.open);
      if (over) {
         this.tip = "Click and press a key to bind  ·  right-click clears  ·  Bind Mode picks Toggle or Hold";
      }

      this.txt(g, "Keybind", x + Widgets.PAD, UiFont.centerY(y, BIND_ROW), Widgets.label(module.getKeybind() == -1));
      boolean capturing = this.binding == module;
      String key;
      if (capturing) {
         key = "Press a key";
      } else {
         key = module.getKeybind() == -1 ? "None" : GeminiKeys.displayName(module.getKeybind());
         if (module.getKeybind() != -1 && module.isHoldBind()) {
            key = key + " (hold)";
         }
      }

      Widgets.chip(
         g, key, x + w - Widgets.PAD, y + 4, 12, capturing ? accent : (over ? Widgets.raisedHover() : Widgets.raised()),
         capturing ? Theme.onColor(accent) : (module.getKeybind() == -1 ? Glass.textDim() : Glass.text()), this.open
      );
      this.hit(WindowGuiScreen.Kind.BIND, x, y, w, BIND_ROW, module, clipTop, clipBottom);
   }

   private SettingControl control(Module module, Setting<?> setting) {
      SettingControl control = this.controls.get(setting);
      if (control == null) {
         control = SettingControl.of(setting);
         if (control == null) {
            return null;
         }

         control.setOnChange(WindowGuiScreen::save);
         if (control instanceof ListControl list) {
            list.setOpener(() -> {
               try {
                  this.minecraft.gui.setScreen(new ListEditorScreen(this, module, (ListSetting)setting));
               } catch (RuntimeException e) {
                  // block, item and entity lists need the game's registries, which only a loaded world has
                  this.showNotice("This list can be edited once a world is loaded");
               }
            });
         }

         this.controls.put(setting, control);
      }

      return control;
   }

   private SettingControl focusedControl() {
      for (SettingControl control : this.controls.values()) {
         if (control.focused()) {
            return control;
         }
      }

      return null;
   }

   // ---- theme switcher -----------------------------------------------------------------------------------

   /** The swatch popover under the theme button: one dot per preset; a click recolours everything at once. */
   private void drawThemePopover(GuiGraphicsExtractor g, int right, int top, int mouseX, int mouseY) {
      this.themeAnim = Glass.approach(this.themeAnim, this.themeOpen ? 1.0F : 0.0F, 18.0F, this.dt);
      if (this.themeAnim <= 0.01F) {
         return;
      }

      List<Theme.Palette> presets = Theme.PRESETS;
      int w = ThemeSwatches.width();
      int h = ThemeSwatches.height(presets.size());
      float fade = this.themeAnim * this.open;
      int x = right - w;
      int y = top + Math.round((1.0F - Glass.easeOutCubic(this.themeAnim)) * -4.0F);
      Glass.shadow(g, x, y, x + w, y + h, 6, 14, 5, 0.5F * fade);
      Glass.roundRect(g, x, y, x + w, y + h, 6, Glass.fade(Glass.surface(3), fade));
      Glass.roundOutline(g, x, y, x + w, y + h, 6, Glass.fade(Glass.withAlpha(Glass.text(), 26), fade));
      if (this.themeOpen) {
         this.hit(WindowGuiScreen.Kind.THEME_POPOVER, x, y, w, h, null);
      }

      String current = Theme.current().name();
      String label = current;
      for (int i = 0; i < presets.size(); i++) {
         Theme.Palette preset = presets.get(i);
         int cellX = x + ThemeSwatches.cellX(i);
         int cellY = y + ThemeSwatches.cellY(i);
         boolean over = ThemeSwatches.indexAt(mouseX - x, mouseY - y, presets.size()) == i;
         boolean active = preset.name().equals(current);
         if (active || over) {
            Glass.roundOutline(g, cellX + 1, cellY + 1, cellX + ThemeSwatches.CELL - 1, cellY + ThemeSwatches.CELL - 1, 10, Glass.fade(Glass.withAlpha(Glass.text(), active ? 230 : 110), fade));
         }

         Glass.roundGradient(g, cellX + 4, cellY + 4, cellX + ThemeSwatches.CELL - 4, cellY + ThemeSwatches.CELL - 4, 7, Glass.fade(preset.accent, fade), Glass.fade(preset.accent2, fade));
         if (this.themeOpen) {
            this.hit(WindowGuiScreen.Kind.THEME, cellX, cellY, ThemeSwatches.CELL, ThemeSwatches.CELL, preset.name());
         }

         if (over) {
            label = preset.name();
         }
      }

      Controls.text(g, label, x + (w - UiFont.width(label, FontSize.SMALL, FontWeight.MEDIUM)) / 2, y + h - 14, Glass.textDim(), fade, FontSize.SMALL, FontWeight.MEDIUM);
   }

   // ---- footer -------------------------------------------------------------------------------------------

   private void drawFooter(GuiGraphicsExtractor g, int x, int y, int w, int accent2, long now) {
      boolean flashing = this.flash != null && now < this.flashUntil;
      DashboardPage pageView = this.pageFor(this.page);
      String text;
      if (this.bindingMenu) {
         text = "Press the key that should open this menu  ·  Esc cancels";
      } else if (this.binding != null) {
         text = "Binding " + this.binding.getName() + "  ·  press a key  ·  Esc cancels  ·  Delete clears";
      } else if (flashing) {
         text = this.flash;
      } else if (this.tip != null) {
         text = this.tip;
      } else {
         text = pageView == null ? "Click a module for its settings  ·  right-click toggles  ·  middle-click binds a key" : pageView.hint();
      }

      text = plain(text);
      boolean loud = flashing || this.binding != null || this.bindingMenu;
      g.fill(x + INSET, y, x + w - INSET, y + 1, this.c(Widgets.divider()));
      this.txt(
         g, UiFont.ellipsize(text, w - 26, FontSize.SMALL, FontWeight.REGULAR), x + INSET + 1, UiFont.centerY(y + 1, FOOTER - 1, FontSize.SMALL),
         loud ? accent2 : Glass.textFaint(), FontSize.SMALL, FontWeight.REGULAR
      );
   }

   // ------------------------------------------------------------------ input

   /** Screen x to the window's virtual coordinates (undoing the GUI scale and the open/close zoom). */
   private double vx(double raw) {
      return (raw / this.uiScale - this.vw / 2.0F * (1.0F - this.zoom)) / this.zoom;
   }

   private double vy(double raw) {
      return (raw / this.uiScale - this.vh / 2.0F * (1.0F - this.zoom)) / this.zoom;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubled) {
      if (this.closing) {
         return true;
      }

      double mx = this.vx(rawEvent.x());
      double my = this.vy(rawEvent.y());
      int button = Keys.glfwButton(rawEvent.button());
      if (this.binding != null || this.bindingMenu) {
         this.binding = null;
         this.bindingMenu = false;
         return true;
      }

      this.mouseDown = button == 0;
      this.keyboardFocus = false;
      WindowGuiScreen.Hit hit = null;
      for (int i = this.hits.size() - 1; i >= 0; i--) {
         if (this.hits.get(i).contains(mx, my)) {
            hit = this.hits.get(i);
            break;
         }
      }

      if (this.themeOpen && (hit == null || hit.kind != WindowGuiScreen.Kind.THEME && hit.kind != WindowGuiScreen.Kind.THEME_POPOVER)) {
         // a click anywhere else only closes the swatches
         this.themeOpen = false;
         return true;
      }

      Object owner = hit == null ? null : (hit.kind == WindowGuiScreen.Kind.CONTROL ? hit.target : (hit.kind == WindowGuiScreen.Kind.PAGE ? ((WindowGuiScreen.PageHit)hit.target).owner : null));
      for (SettingControl control : this.controls.values()) {
         if (control != owner) {
            control.blur();
         }
      }

      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         pageView.blurExcept(owner);
      }

      if (hit == null) {
         this.searchFocused = false;
         return super.mouseClicked(rawEvent, doubled);
      }

      if (hit.kind != WindowGuiScreen.Kind.SEARCH && hit.kind != WindowGuiScreen.Kind.CLEAR) {
         this.searchFocused = false;
      }

      switch (hit.kind) {
         case PAGE -> ((WindowGuiScreen.PageHit)hit.target).click.click(button, mx, my, doubled);
         case PAGE_TAB -> this.showPage((Page)hit.target);
         case SEARCH -> this.searchFocused = true;
         case CLEAR -> {
            this.leaveSearch();
            this.searchFocused = true;
         }
         case FILTER -> {
            this.filter = (String)hit.target;
            this.listScroll.jumpTo(0.0F);
         }
         case CATEGORY -> this.selectCategory((Category)hit.target);
         case CARD -> {
            Module module = (Module)hit.target;
            this.selected = module;
            if (button == 1) {
               this.toggle(module);
            } else if (button == 2) {
               this.beginBind(module);
            } else {
               this.card(module).hover.flash();
               this.setExpanded(module, this.expanded != module);
            }
         }
         case SWITCH -> {
            this.selected = (Module)hit.target;
            this.toggle((Module)hit.target);
         }
         case MORE -> {
            String name = ((Module)hit.target).getName();
            if (!unfolded.remove(name)) {
               unfolded.add(name);
            }
         }
         case STAR -> {
            ClickGuiModule style = style();
            if (style != null) {
               Module module = (Module)hit.target;
               boolean starred = style.toggleFavorite(module);
               this.showNotice(starred ? module.getName() + " added to favourites" : module.getName() + " removed from favourites");
            }
         }
         case BIND -> {
            Module module = (Module)hit.target;
            if (button == 1) {
               module.setKeybind(-1);
               gemini.input.BindWarnings.invalidate();
               this.showNotice(module.getName() + " unbound");
            } else {
               this.beginBind(module);
            }
         }
         case CONTROL -> {
            SettingControl control = (SettingControl)hit.target;
            if (button == 2) {
               // middle click puts any setting back to its default
               control.blur();
               control.setting().reset();
               save();
            } else if (control.mouseClicked(mx, my, button, doubled)) {
               if (control.dragging()) {
                  this.activeControl = control;
               }
            } else if (button == 1) {
               control.setting().reset();
               save();
            }
         }
         case THEME_BUTTON -> this.themeOpen = !this.themeOpen;
         case THEME -> {
            ClickGuiModule style = style();
            if (style != null) {
               style.setTheme((String)hit.target);
            }
         }
         case THEME_POPOVER -> {
         }
         case LAYOUT -> {
            ClickGuiModule style = style();
            if (style != null) {
               style.setPanelLayout(true);
            }

            this.minecraft.gui.setScreen(new ClickGuiScreen());
         }
         case HUD_EDIT -> this.minecraft.gui.setScreen(ClickGuiScreen.hudEditor());
         case PALETTE -> {
            this.blurAll();
            gemini.ui.palette.PaletteScreen.open(this.minecraft);
         }
         case HELP -> gemini.ui.help.HelpScreen.open(gemini.ui.help.HelpScreen.Tab.WHATS_NEW, this);
         case MENU_KEY -> {
            this.bindingMenu = true;
            this.binding = null;
         }
      }

      return true;
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent rawEvent, double deltaX, double deltaY) {
      int button = Keys.glfwButton(rawEvent.button());
      if (this.activeControl != null) {
         this.activeControl.mouseDragged(this.vx(rawEvent.x()), this.vy(rawEvent.y()), button);
         return true;
      }

      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null && pageView.mouseDragged(this.vx(rawEvent.x()), this.vy(rawEvent.y()), button)) {
         return true;
      }

      return super.mouseDragged(rawEvent, deltaX, deltaY);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent rawEvent) {
      int button = Keys.glfwButton(rawEvent.button());
      this.mouseDown = false;
      if (this.activeControl != null) {
         SettingControl control = this.activeControl;
         this.activeControl = null;
         control.mouseReleased(this.vx(rawEvent.x()), this.vy(rawEvent.y()), button);
      }

      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         pageView.mouseReleased(this.vx(rawEvent.x()), this.vy(rawEvent.y()), button);
      }

      return super.mouseReleased(rawEvent);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      ClickGuiModule style = style();
      float speed = style == null ? 2.0F : style.scrollSpeed();
      double mx = this.vx(x);
      double my = this.vy(y);
      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         return pageView.mouseScrolled(mx, my, scrollY * speed * 12.0F) || super.mouseScrolled(x, y, scrollX, scrollY);
      }

      if (mx >= this.listX - 4 && mx < this.listX + this.listW + 6 && my >= this.listViewTop && my < this.listViewBottom) {
         this.listScroll.scrollBy((float)(-scrollY * speed * (CARD_H + CARD_GAP) / 2.0F));
         return true;
      } else {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (this.closing) {
         return true;
      }

      int key = event.key();
      if (this.bindingMenu) {
         if (key != Keys.ESCAPE) {
            GeminiKeys.OPEN_GUI.setKey(InputConstants.getKey(event));
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            save();
            String clash = gemini.input.BindWarnings.afterMenuKey(Gemini.getInstance().modules());
            if (clash != null) {
               this.showNotice(clash);
            }
         }

         this.bindingMenu = false;
         return true;
      } else if (this.binding != null) {
         if (key != Keys.ESCAPE) {
            if (key == Keys.DELETE || key == Keys.BACKSPACE) {
               this.binding.setKeybind(-1);
               gemini.input.BindWarnings.invalidate();
               this.showNotice(this.binding.getName() + " unbound");
            } else {
               this.binding.setKeybind(key);
               String clash = gemini.input.BindWarnings.afterBind(Gemini.getInstance().modules(), this.binding, key);
               this.showNotice(clash != null ? clash : this.binding.getName() + " bound to " + GeminiKeys.displayName(key));
            }

            gemini.input.BindWarnings.invalidate();
            this.keybindsPage.opened();
         }

         this.binding = null;
         return true;
      }

      if (GeminiKeys.opensPalette(event)) {
         this.blurAll();
         gemini.ui.palette.PaletteScreen.open(this.minecraft);
         return true;
      }

      SettingControl focused = this.page == Page.MODULES ? this.focusedControl() : null;
      if (focused != null && focused.keyPressed(event)) {
         return true;
      }

      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null && pageView.focused() && pageView.keyPressed(event)) {
         return true;
      }

      if (key == Keys.ESCAPE) {
         if (this.themeOpen) {
            this.themeOpen = false;
         } else if (pageView == null && !this.search.isEmpty()) {
            this.leaveSearch();
         } else {
            this.onClose();
         }

         return true;
      } else if (!this.searchFocused && GeminiKeys.OPEN_GUI.matches(event)) {
         this.onClose();
         return true;
      } else if (key == Keys.F && event.hasControlDown()) {
         this.showPage(Page.MODULES);
         this.searchFocused = true;
         return true;
      } else if (key == Keys.TAB) {
         this.cycleTab(event.hasShiftDown() ? -1 : 1);
         return true;
      } else if (pageView != null) {
         return super.keyPressed(event);
      } else if (key == Keys.BACKSPACE) {
         if (!this.search.isEmpty()) {
            this.search = event.hasControlDown() ? "" : this.search.substring(0, this.search.offsetByCodePoints(this.search.length(), -1));
            this.listScroll.jumpTo(0.0F);
         }

         return true;
      } else if (key == Keys.DOWN || key == Keys.UP || key == Keys.LEFT || key == Keys.RIGHT) {
         this.moveSelection(key == Keys.LEFT ? -1 : (key == Keys.RIGHT ? 1 : 0), key == Keys.UP ? -1 : (key == Keys.DOWN ? 1 : 0));
         return true;
      } else if (Keys.isEnter(key)) {
         if (this.selected != null && this.shown.contains(this.selected)) {
            this.keyboardFocus = true;
            if (event.hasShiftDown()) {
               this.setExpanded(this.selected, this.expanded != this.selected);
            } else {
               this.toggle(this.selected);
            }
         }

         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      if (this.closing || this.binding != null || this.bindingMenu) {
         return true;
      }

      SettingControl focused = this.page == Page.MODULES ? this.focusedControl() : null;
      if (focused != null) {
         return focused.charTyped(event);
      }

      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null && pageView.focused()) {
         return pageView.charTyped(event);
      }

      if (event.isAllowedChatCharacter() && this.search.length() < 32) {
         // typing anywhere searches modules, from any page
         this.showPage(Page.MODULES);
         this.search = this.search + event.codepointAsString();
         this.searchFocused = true;
         this.listScroll.jumpTo(0.0F);
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   // ------------------------------------------------------------------ lifecycle

   /** Starts the close animation; the screen really closes from {@link #tick()} once it has faded out. */
   @Override
   public void onClose() {
      if (this.closing) {
         return;
      }

      this.blurAll();
      this.binding = null;
      this.bindingMenu = false;
      this.themeOpen = false;
      if (Glass.animations() && this.animationProgress > 0.05F) {
         this.closing = true;
      } else {
         this.finishClose();
      }
   }

   private void finishClose() {
      if (this.parent != null) {
         this.minecraft.gui.setScreen(this.parent);
      } else {
         super.onClose();
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.closing && (this.animationProgress <= 0.03F || Util.getMillis() - this.lastFrame > 500L)) {
         this.closing = false;
         this.finishClose();
      }
   }

   @Override
   public void added() {
      super.added();
      // coming back from the list editor or reopening: never stay stuck in a half-finished close
      this.closing = false;
      this.mouseDown = false;
      this.lastFrame = Util.getMillis();
   }

   @Override
   public void removed() {
      this.blurAll();
      this.minecraft.textInputManager().stopTextInput(this);
      lastCategory = this.category;
      lastModule = this.selected == null ? null : this.selected.getName();
      rememberedSearch = this.search;
      lastPage = this.page;
      save();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private void blurAll() {
      for (SettingControl control : this.controls.values()) {
         control.blur();
      }

      this.activeControl = null;
      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         pageView.blurExcept(null);
      }
   }

   // ------------------------------------------------------------------ actions

   private void showPage(Page target) {
      if (target != this.page) {
         this.blurAll();
         this.page = target;
         this.binding = null;
         this.themeOpen = false;
         DashboardPage pageView = this.pageFor(target);
         if (pageView != null) {
            pageView.opened();
         }
      }
   }

   /** Tab / Shift+Tab: through the categories, then the pages, and around. */
   private void cycleTab(int step) {
      List<Object> tabs = this.tabs();
      Object current = this.page == Page.MODULES ? this.category : this.page;
      int index = Math.max(0, tabs.indexOf(current));
      Object next = tabs.get(Math.floorMod(index + step, tabs.size()));
      if (next instanceof Category cat) {
         this.selectCategory(cat);
      } else {
         this.showPage((Page)next);
      }
   }

   /** Clears the search but stays on the module you picked from the results by opening its category. */
   private void leaveSearch() {
      this.search = "";
      this.searchFocused = false;
      this.listScroll.jumpTo(0.0F);
      if (this.selected != null) {
         this.category = this.selected.getCategory();
         if (this.expanded == this.selected) {
            this.revealCard = true;
         }
      }
   }

   private void selectCategory(Category cat) {
      this.showPage(Page.MODULES);
      boolean changed = cat != this.category || !this.search.isEmpty() || FAVORITES.equals(this.filter);
      this.category = cat;
      this.search = "";
      this.searchFocused = false;
      if (FAVORITES.equals(this.filter)) {
         this.filter = "All";
      }

      if (changed) {
         this.listScroll.jumpTo(0.0F);
         this.expanded = null;
         this.selected = null;
      }
   }

   /** Opens or closes a card's settings; only one card is open at a time. */
   private void setExpanded(Module module, boolean open) {
      this.blurAll();
      if (open) {
         this.expanded = module;
         this.revealCard = true;
         Fuzzy.Match match = this.search.isBlank() ? null : this.matches.get(module);
         this.revealSetting = match == null ? null : match.setting();
      } else if (this.expanded == module) {
         this.expanded = null;
      }
   }

   /** Moves the keyboard focus one card across ({@code dx}) or down ({@code dy}) and keeps it in view. */
   private void moveSelection(int dx, int dy) {
      List<Module> list = this.shown;
      if (!list.isEmpty()) {
         int index = this.selected == null ? -1 : list.indexOf(this.selected);
         int next = this.keyboardFocus || index < 0 ? CardGrid.step(index, list.size(), this.columns, dx, dy) : index;
         this.selected = list.get(next);
         this.keyboardFocus = true;
         if (next < this.cardTops.length) {
            this.listScroll.reveal(this.cardTops[next] - 4, this.cardTops[next] + this.cardHeights[next] + 4, this.listViewBottom - this.listViewTop);
         }
      }
   }

   private void toggle(Module module) {
      if (module instanceof ClickGuiModule) {
         return;
      }

      if (!module.isSavedEnabled() && !module.canActivate()) {
         // refused by Module.canActivate(): say why instead of silently doing nothing
         this.showNotice(module.blockedReason());
      } else {
         module.toggle();
      }
   }

   private void showNotice(String message) {
      if (message != null) {
         this.flash = plain(message);
         this.flashUntil = Util.getMillis() + 2600L;
      }
   }

   private List<Module> visibleModules() {
      if (Gemini.getInstance() == null) {
         return List.of();
      }

      String query = this.search.trim().toLowerCase(Locale.ROOT);
      boolean favoritesOnly = FAVORITES.equals(this.filter);
      ClickGuiModule style = style();
      List<Module> list = new ArrayList<>();
      this.matches.clear();
      for (Module module : query.isEmpty() && !favoritesOnly ? modules(this.category) : Gemini.getInstance().modules().getModules()) {
         if (favoritesOnly && (style == null || !style.isFavorite(module))) {
            continue;
         }

         if (!query.isEmpty()) {
            Fuzzy.Match match = Fuzzy.moduleMatch(query, module);
            if (!match.matched()) {
               continue;
            }

            this.matches.put(module, match);
         }

         if ("On".equals(this.filter) && !module.isEnabled() || "Bound".equals(this.filter) && module.getKeybind() == -1) {
            continue;
         }

         list.add(module);
      }

      Comparator<Module> byName = Comparator.comparing(m -> m.getName().toLowerCase(Locale.ROOT));
      if (query.isEmpty()) {
         list.sort(byName);
      } else {
         // Best fuzzy match first (name hits beat description and setting hits), then alphabetical.
         list.sort(Comparator.<Module>comparingInt(m -> -this.matches.get(m).score()).thenComparing(byName));
      }

      return list;
   }

   private static List<Module> modules(Category category) {
      return Gemini.getInstance() == null ? List.of() : Gemini.getInstance().modules().of(category);
   }

   private static ClickGuiModule style() {
      return Gemini.getInstance() == null ? null : Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
   }

   private static void save() {
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().config().save();
      }
   }

   // ------------------------------------------------------------------ helpers

   /** {@code color} faded with the window's open/close animation. */
   private int c(int color) {
      return Glass.fade(color, this.open);
   }

   /** {@code color} at {@code alpha} (0..255), faded with the window. */
   private int c(int color, int alpha) {
      return Glass.withAlpha(color, Math.round(alpha * this.open));
   }

   private void txt(GuiGraphicsExtractor g, String text, int x, int y, int color) {
      Controls.text(g, text, x, y, color, this.open, FontSize.BODY, FontWeight.REGULAR);
   }

   private void txt(GuiGraphicsExtractor g, String text, int x, int y, int color, FontSize size, FontWeight weight) {
      Controls.text(g, text, x, y, color, this.open, size, weight);
   }

   private void hit(WindowGuiScreen.Kind kind, int x, int y, int w, int h, Object target) {
      this.hits.add(new WindowGuiScreen.Hit(kind, x, y, w, h, target));
   }

   /** Registers a hit clipped to a scrolling viewport so half-hidden rows only react where they are visible. */
   private void hit(WindowGuiScreen.Kind kind, int x, int y, int w, int h, Object target, int clipTop, int clipBottom) {
      int top = Math.max(y, clipTop);
      int bottom = Math.min(y + h, clipBottom);
      if (bottom > top) {
         this.hits.add(new WindowGuiScreen.Hit(kind, x, top, w, bottom - top, target));
      }
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private static String plain(String text) {
      return text == null ? "" : text.replaceAll("§.", "");
   }

   private static enum Kind {
      CATEGORY,
      PAGE_TAB,
      PAGE,
      CARD,
      SWITCH,
      STAR,
      FILTER,
      SEARCH,
      CLEAR,
      BIND,
      CONTROL,
      THEME_BUTTON,
      THEME_POPOVER,
      THEME,
      LAYOUT,
      HUD_EDIT,
      MENU_KEY,
      PALETTE,
      HELP,
      MORE;
   }

   private record PageHit(Object owner, DashboardPage.Click click) {
   }

   private record Hit(WindowGuiScreen.Kind kind, int x, int y, int w, int h, Object target) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
