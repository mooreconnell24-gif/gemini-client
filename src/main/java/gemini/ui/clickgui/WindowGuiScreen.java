package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.input.KeybindReport;
import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.Fuzzy;
import gemini.ui.GeminiMark;
import gemini.ui.Glass;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.SettingControl;
import gemini.ui.clickgui.window.BaritonePage;
import gemini.ui.clickgui.window.DashboardPage;
import gemini.ui.clickgui.window.KeybindsPage;
import gemini.ui.clickgui.window.ProfilesPage;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
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
 * The Gemini dashboard: one centred window with a sidebar of category tabs and dedicated pages (presets and
 * configs, keybinds, Baritone), a searchable module list with a settings pane, and the pages themselves. Drawing
 * and hit-testing share one pass: every clickable element registers a hit rectangle while it is drawn, so input
 * always matches what is on screen. Pages register theirs through {@link DashboardPage.Host}.
 *
 * <p>The window scales and fades in when it opens and out when it closes, like the panel menu; with the ClickGUI
 * "Animations" setting off it appears and disappears at once.
 */
public final class WindowGuiScreen extends Screen implements DashboardPage.Host {
   private static final int SIDEBAR = 120;
   private static final int CARD_H = 30;
   private static final int CARD_GAP = 4;
   private static final int FOOTER = 20;
   private static final int LINE = 12;
   private static final String FAVORITES = "★";
   private static final String[] FILTERS = new String[]{"All", "On", "Bound", FAVORITES};
   private static final Page[] PAGE_TABS = {Page.PROFILES, Page.KEYBINDS, Page.BARITONE};
   private static Category lastCategory = Category.COMBAT;
   private static String lastModule;
   private static String rememberedSearch = "";
   private static Page lastPage = Page.MODULES;
   /** Panel shades for this frame, taken from the ClickGUI "Surface" setting. */
   private int surface = 0xFF1A1E27;
   private int surface2 = 0xFF1F2430;
   private int surface3 = 0xFF252B38;
   private final Screen parent;
   private final List<WindowGuiScreen.Hit> hits = new ArrayList<>();
   private final Map<Module, Float> hoverAnim = new HashMap<>();
   private final Map<Module, Float> enableAnim = new HashMap<>();
   private final Map<Setting<?>, Float> settingAnim = new IdentityHashMap<>();
   private final Map<Setting<?>, SettingControl> controls = new IdentityHashMap<>();
   private final Map<Module, Fuzzy.Match> matches = new HashMap<>();
   private final ProfilesPage profilesPage = new ProfilesPage(this);
   private final KeybindsPage keybindsPage = new KeybindsPage(this);
   private final BaritonePage baritonePage = new BaritonePage(this);
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
   private float indicatorY = Float.NaN;
   private float indicatorAlpha;
   private Page page;
   private Category category = lastCategory;
   private Module selected;
   private String search = "";
   private boolean searchFocused;
   private String filter = "All";
   private float listScroll;
   private float listScrollTarget;
   private float settingsScroll;
   private float settingsScrollTarget;
   private int listViewTop;
   private int listViewBottom;
   private int listX;
   private int listW;
   private int settingsViewTop;
   private int settingsViewBottom;
   private int settingsX;
   private int settingsW;
   private Module binding;
   private boolean bindingMenu;
   /** A setting the search matched in the module just picked: the settings pane scrolls to it once. */
   private String revealSetting;
   private String tip;
   private String flash;
   private long flashUntil;
   private List<Module> shown = List.of();
   private boolean revealed;

   /** Which part of the dashboard is showing. */
   public enum Page {
      MODULES("Modules"),
      PROFILES("Presets & Configs"),
      KEYBINDS("Keybinds"),
      BARITONE("Baritone");

      public final String label;

      Page(String label) {
         this.label = label;
      }
   }

   /** Opens on the page that was showing last time. */
   public WindowGuiScreen() {
      this(null, null);
   }

   /**
    * Opens on {@code page} (null: the page that was showing last time). {@code parent} is shown again when the
    * dashboard closes; null closes to the game or the title screen as usual.
    */
   public WindowGuiScreen(Page page, Screen parent) {
      super(Component.literal("Gemini Client"));
      this.parent = parent;
      this.page = page == null ? lastPage : page;
      ClickGuiModule style = style();
      if (style != null && style.keepSearch()) {
         this.search = rememberedSearch;
      }

      this.currentPage().ifPresent(DashboardPage::opened);
   }

   /**
    * Opens the Modules page with {@code module} selected in its category (the search cleared), as the quick-search
    * palette's Shift+Enter does. {@code parent} is shown again when the dashboard closes.
    */
   public static WindowGuiScreen forModule(Module module, Screen parent) {
      WindowGuiScreen screen = new WindowGuiScreen(Page.MODULES, parent);
      if (module != null) {
         screen.category = module.getCategory();
         screen.selected = module;
         screen.search = "";
         screen.filter = "All";
         screen.revealed = false;
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
         default -> null;
      };
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

   /** Vanilla blurs the world behind every menu; the ClickGUI "Blur" switch turns that off for this one. */
   @Override
   protected void extractBlurredBackground(GuiGraphicsExtractor g) {
      ClickGuiModule style = style();
      if (style == null || style.blur()) {
         super.extractBlurredBackground(g);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int rawMouseX, int rawMouseY, float partialTick) {
      long now = Util.getMillis();
      this.dt = Math.min(0.05F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      ClickGuiModule style = style();
      // Glass.approach jumps straight to the target when animations are off
      this.animationProgress = Glass.approach(this.animationProgress, this.closing ? 0.0F : 1.0F, this.closing ? 22.0F : 13.0F, this.dt);
      this.open = Mth.clamp(this.animationProgress, 0.0F, 1.0F);
      this.zoom = 0.94F + 0.06F * this.open;
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
      if (style != null) {
         this.surface = style.surface(1);
         this.surface2 = style.surface(2);
         this.surface3 = style.surface(3);
      }

      if (style == null || style.dim()) {
         g.fill(0, 0, this.width, this.height, Glass.withAlpha(0xFF000000, this.a(150)));
      }

      int mouseX = (int)Math.floor(this.vx(rawMouseX));
      int mouseY = (int)Math.floor(this.vy(rawMouseY));
      g.pose().pushMatrix();
      g.pose().scale(this.uiScale, this.uiScale);
      g.pose().translate(this.vw / 2.0F * (1.0F - this.zoom), this.vh / 2.0F * (1.0F - this.zoom));
      g.pose().scale(this.zoom, this.zoom);
      int ww = Mth.clamp(this.vw - 32, 440, 700);
      int wh = Mth.clamp(this.vh - 32, 270, 420);
      ww = Math.min(ww, this.vw - 8);
      wh = Math.min(wh, this.vh - 8);
      int wx = (this.vw - ww) / 2;
      int wy = (this.vh - wh) / 2;
      int bodyAlpha = this.a(Math.round(255.0F * glass));
      Glass.glow(g, wx, wy, wx + ww, wy + wh, 7, accent, this.open * 0.55F);
      for (int i = 4; i >= 1; i--) {
         Glass.roundRect(g, wx - i, wy - i + 3, wx + ww + i, wy + wh + i + 3, 6 + i, Glass.withAlpha(0xFF000000, this.a(16)));
      }

      Glass.roundRect(g, wx, wy, wx + ww, wy + wh, 6, Glass.withAlpha(this.surface, bodyAlpha));
      Glass.roundRect(g, wx, wy, wx + SIDEBAR, wy + wh, 6, Glass.withAlpha(this.surface2, bodyAlpha));
      g.fill(wx + SIDEBAR - 6, wy, wx + SIDEBAR, wy + wh, Glass.withAlpha(this.surface2, bodyAlpha));
      g.fill(wx + SIDEBAR, wy + 6, wx + SIDEBAR + 1, wy + wh - 6, Glass.withAlpha(0xFFFFFFFF, this.a(14)));
      Glass.roundOutline(g, wx, wy, wx + ww, wy + wh, 6, Glass.withAlpha(Glass.lerp(0xFFFFFFFF, accent, 0.6F), this.a(60)));
      Glass.gradientBar(g, wx + 6, wy, wx + ww - 6, wy + 2, Glass.withAlpha(accent, this.a(255)), Glass.withAlpha(accent2, this.a(255)));
      this.drawSidebar(g, wx, wy, wh, mouseX, mouseY, accent, accent2);
      int cx = wx + SIDEBAR + 1;
      int cw = ww - SIDEBAR - 1;
      int bodyBottom = wy + wh - FOOTER;
      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         pageView.render(g, cx, wy + 2, cw, bodyBottom - wy - 2, mouseX, mouseY);
      } else {
         this.drawModulesPage(g, cx, wy, cw, wh, bodyBottom, mouseX, mouseY, accent, accent2);
      }

      this.drawFooter(g, cx, bodyBottom, cw, accent2, now);
      g.pose().popMatrix();
      super.extractRenderState(g, rawMouseX, rawMouseY, partialTick);
   }

   private void drawModulesPage(GuiGraphicsExtractor g, int cx, int wy, int cw, int wh, int bodyBottom, int mouseX, int mouseY, int accent, int accent2) {
      this.drawHeader(g, cx, wy, cw, mouseX, mouseY, accent, accent2, Util.getMillis());
      int bodyTop = wy + 32;
      this.listX = cx + 8;
      this.listW = Math.max(150, Math.round((cw - 24) * 0.47F));
      this.settingsX = this.listX + this.listW + 8;
      this.settingsW = cx + cw - 8 - this.settingsX;
      this.shown = this.visibleModules();
      if (this.selected != null && !this.shown.contains(this.selected) && this.search.isBlank()) {
         this.selected = null;
      }

      if (this.selected == null && !this.shown.isEmpty()) {
         this.selected = this.shown.get(0);
      }

      if (!this.revealed) {
         this.revealed = true;
         int index = this.selected == null ? -1 : this.shown.indexOf(this.selected);
         if (index > 0) {
            float target = index * (CARD_H + CARD_GAP) - (wh - 60) / 3.0F;
            this.listScrollTarget = Math.max(0.0F, target);
            this.listScroll = this.listScrollTarget;
         }
      }

      this.drawModuleList(g, bodyTop, bodyBottom, mouseX, mouseY, accent, accent2);
      this.drawSettings(g, bodyTop, bodyBottom, mouseX, mouseY, accent, accent2);
   }

   private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int h, int mouseX, int mouseY, int accent, int accent2) {
      float pulse = Glass.animations() ? 0.75F + 0.25F * (float)Math.sin(Util.getMillis() / 500.0) : 1.0F;
      GeminiMark.draw(g, x + 10, y + 10, 22, accent, pulse * this.open);
      g.text(this.font, "Gemini", x + 37, y + 11, Glass.withAlpha(Glass.TEXT, this.a(255)), true);
      String version = "dev".equals(Gemini.VERSION) ? "dev build" : "v" + Gemini.VERSION;
      g.text(this.font, version, x + 37, y + 22, Glass.withAlpha(accent2, this.a(255)), false);
      int tabsTop = y + 38;
      if (!SingleplayerGate.allowed()) {
         // Modules can be configured anywhere, but only run in a private single-player world.
         String label = "configure only";
         int lw = this.font.width(label) + 8;
         Glass.roundRect(g, x + 10, tabsTop, x + 10 + lw, tabsTop + 11, 3, Glass.withAlpha(Glass.WARN, this.a(70)));
         g.text(this.font, label, x + 14, tabsTop + 2, Glass.withAlpha(Glass.WARN, this.a(255)), false);
         if (inside(mouseX, mouseY, x + 10, tabsTop, lw, 11)) {
            this.tip = plain(SingleplayerGate.denialMessage());
         }

         tabsTop += 14;
      }

      int buttonsTop = y + h - 64;
      int count = Category.values().length + PAGE_TABS.length;
      int step = Mth.clamp((buttonsTop - 6 - tabsTop - 9) / count, 13, 20);
      int th = step - 2;
      float targetY = Float.NaN;
      int iy = tabsTop;
      List<Object> tabs = new ArrayList<>(List.of(Category.values()));
      tabs.addAll(List.of(PAGE_TABS));
      // pass 1: work out where the selection indicator should be, so it is drawn under the labels
      for (Object tab : tabs) {
         if (tab == PAGE_TABS[0]) {
            iy += 9;
         }

         if (this.tabActive(tab)) {
            targetY = iy;
         }

         iy += step;
      }

      boolean showIndicator = !Float.isNaN(targetY);
      this.indicatorAlpha = Glass.approach(this.indicatorAlpha, showIndicator ? 1.0F : 0.0F, 14.0F, this.dt);
      if (showIndicator) {
         this.indicatorY = Float.isNaN(this.indicatorY) ? targetY : Glass.approach(this.indicatorY, targetY, 16.0F, this.dt);
      }

      if (!Float.isNaN(this.indicatorY) && this.indicatorAlpha > 0.01F) {
         int top = Math.round(this.indicatorY);
         int alpha = Math.round(this.indicatorAlpha * 255.0F);
         Glass.roundRect(g, x + 6, top, x + SIDEBAR - 6, top + th, 4, Glass.withAlpha(accent, this.a(Math.round(alpha * 0.2F))));
         g.fill(x + 6, top + 3, x + 8, top + th - 3, Glass.withAlpha(accent2, this.a(alpha)));
      }

      iy = tabsTop;
      for (Object tab : tabs) {
         if (tab == PAGE_TABS[0]) {
            g.fill(x + 12, iy + 3, x + SIDEBAR - 12, iy + 4, Glass.withAlpha(0xFFFFFFFF, this.a(18)));
            iy += 9;
         }

         this.drawTab(g, tab, x, iy, th, mouseX, mouseY, accent, accent2);
         iy += step;
      }

      int half = (SIDEBAR - 12 - 3) / 2;
      this.sidebarButton(g, x + 6, buttonsTop, half, "Search", WindowGuiScreen.Kind.PALETTE, mouseX, mouseY, accent, false);
      this.sidebarButton(g, x + 6 + half + 3, buttonsTop, half, "Guide", WindowGuiScreen.Kind.HELP, mouseX, mouseY, accent, false);
      this.sidebarButton(g, x + 6, buttonsTop + 20, half, "Edit HUD", WindowGuiScreen.Kind.HUD_EDIT, mouseX, mouseY, accent, false);
      this.sidebarButton(g, x + 6 + half + 3, buttonsTop + 20, half, "Panels", WindowGuiScreen.Kind.LAYOUT, mouseX, mouseY, accent, false);
      String menuKey = this.bindingMenu ? "Press a key..." : "Menu key: " + GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString();
      this.sidebarButton(g, x + 6, buttonsTop + 40, SIDEBAR - 12, menuKey, WindowGuiScreen.Kind.MENU_KEY, mouseX, mouseY, accent, this.bindingMenu);
   }

   private boolean tabActive(Object tab) {
      if (tab instanceof Category cat) {
         return this.page == Page.MODULES && this.search.isBlank() && !FAVORITES.equals(this.filter) && cat == this.category;
      }

      return tab == this.page;
   }

   private void drawTab(GuiGraphicsExtractor g, Object tab, int x, int iy, int th, int mouseX, int mouseY, int accent, int accent2) {
      boolean active = this.tabActive(tab);
      boolean over = inside(mouseX, mouseY, x + 6, iy, SIDEBAR - 12, th);
      if (over && !active) {
         Glass.roundRect(g, x + 6, iy, x + SIDEBAR - 6, iy + th, 4, Glass.withAlpha(0xFFFFFFFF, this.a(10)));
      }

      int ty = iy + (th - 8) / 2 + 1;
      int textColor = Glass.withAlpha(active ? Glass.TEXT : (over ? Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, 0.6F) : Glass.TEXT_DIM), this.a(255));
      int right = x + SIDEBAR - 10;
      String name;
      if (tab instanceof Category cat) {
         int iconColor = active ? accent2 : Glass.lerp(cat.accent(), 0xFFFFFFFF, 0.35F);
         icon(g, cat.name(), x + 13, iy + (th - 7) / 2, Glass.withAlpha(iconColor, this.a(active || over ? 255 : 190)));
         List<Module> list = modules(cat);
         int on = 0;
         for (Module module : list) {
            if (module.isEnabled() && module.isListed()) {
               on++;
            }
         }

         String total = Integer.toString(list.size());
         int tw = this.font.width(total);
         g.text(this.font, total, right - tw, ty, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
         right -= tw + 4;
         if (on > 0) {
            String count = Integer.toString(on);
            int bw = this.font.width(count) + 6;
            int bx = right - bw;
            Glass.roundRect(g, bx, ty - 2, right, ty + 8, 3, Glass.withAlpha(active ? accent : this.surface3, this.a(active ? 170 : 255)));
            g.text(this.font, count, bx + 3, ty - 1, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
            right = bx - 3;
         }

         name = cat.displayName();
         this.hit(WindowGuiScreen.Kind.CATEGORY, x + 6, iy, SIDEBAR - 12, th, cat);
         if (over) {
            this.tip = list.size() + " modules, " + on + " on  ·  Tab / Shift+Tab switches tab";
         }
      } else {
         Page p = (Page)tab;
         icon(g, p.name(), x + 13, iy + (th - 7) / 2, Glass.withAlpha(active ? accent2 : Glass.TEXT_DIM, this.a(active || over ? 255 : 190)));
         name = p.label;
         if (p == Page.KEYBINDS && Gemini.getInstance() != null) {
            int conflicts = conflictCount();
            if (conflicts > 0) {
               String count = Integer.toString(conflicts);
               int bw = this.font.width(count) + 6;
               int bx = right - bw;
               Glass.roundRect(g, bx, ty - 2, right, ty + 8, 3, Glass.withAlpha(Glass.ERROR, this.a(170)));
               g.text(this.font, count, bx + 3, ty - 1, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
               right = bx - 3;
            }
         } else if (p == Page.BARITONE && gemini.util.BaritoneBridge.isActive()) {
            Glass.roundRect(g, right - 5, ty + 1, right, ty + 6, 2, Glass.withAlpha(Glass.OK, this.a(255)));
            right -= 8;
         }

         this.hit(WindowGuiScreen.Kind.PAGE_TAB, x + 6, iy, SIDEBAR - 12, th, p);
         if (over) {
            this.tip = switch (p) {
               case PROFILES -> "Saved profiles, export and import, built-in presets";
               case KEYBINDS -> "Every key bind grouped by key, with conflicts to fix";
               default -> "Live pathing status and bot quick starts";
            };
         }
      }

      g.text(this.font, fit(this.font, name, right - (x + 25) - 2), x + 25, ty, textColor, false);
   }

   /** Conflicting keys for the sidebar badge: the same cached count the title screen and the pause dock show. */
   private static int conflictCount() {
      return gemini.input.BindWarnings.conflictCount(Gemini.getInstance().modules());
   }

   private void sidebarButton(GuiGraphicsExtractor g, int x, int y, int w, String label, WindowGuiScreen.Kind kind, int mouseX, int mouseY, int accent, boolean active) {
      boolean over = inside(mouseX, mouseY, x, y, w, 17);
      Glass.roundRect(g, x, y, x + w, y + 17, 4, Glass.withAlpha(active ? accent : (over ? this.surface3 : this.surface), this.a(active ? 150 : 255)));
      if (over) {
         Glass.roundOutline(g, x, y, x + w, y + 17, 4, Glass.withAlpha(accent, this.a(120)));
      }

      String text = fit(this.font, label, w - 8);
      g.text(this.font, text, x + (w - this.font.width(text)) / 2, y + 5, Glass.withAlpha(over || active ? Glass.TEXT : Glass.TEXT_DIM, this.a(255)), false);
      this.hit(kind, x, y, w, 17, null);
      if (over) {
         this.tip = switch (kind) {
            case MENU_KEY -> "Click, then press the key that opens this menu";
            case PALETTE -> "Quick search over modules, settings, commands and pages (" + GeminiKeys.paletteHint() + ")";
            case HELP -> "What's new, controls, every command and about";
            case LAYOUT -> "Switch to the classic draggable panels (change back in ClickGUI > Layout)";
            default -> "Drag HUD widgets around the screen";
         };
      }
   }

   private void drawHeader(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, int accent, int accent2, long now) {
      int filtersW = 0;
      for (String f : FILTERS) {
         filtersW += this.font.width(f) + 12 + 3;
      }

      int sx = x + 8;
      int sw = w - 16 - filtersW - 6;
      int sy = y + 9;
      boolean overSearch = inside(mouseX, mouseY, sx, sy, sw, 16);
      Glass.roundRect(g, sx, sy, sx + sw, sy + 16, 4, Glass.withAlpha(this.searchFocused ? this.surface3 : this.surface2, this.a(255)));
      Glass.roundOutline(g, sx, sy, sx + sw, sy + 16, 4, Glass.withAlpha(this.searchFocused ? accent : 0xFFFFFFFF, this.a(this.searchFocused ? 160 : 18)));
      magnifier(g, sx + 6, sy + 4, Glass.withAlpha(this.searchFocused ? accent2 : Glass.TEXT_FAINT, this.a(255)));
      boolean blink = this.searchFocused && now / 450L % 2L == 0L;
      String text = this.search.isEmpty() && !this.searchFocused ? "Search modules and settings..." : this.search + (blink ? "_" : "");
      while (this.font.width(text) > sw - 34 && text.length() > 1) {
         text = text.substring(1);
      }

      g.text(this.font, text, sx + 18, sy + 4, Glass.withAlpha(this.search.isEmpty() ? Glass.TEXT_FAINT : Glass.TEXT, this.a(255)), false);
      this.hit(WindowGuiScreen.Kind.SEARCH, sx, sy, sw, 16, null);
      if (!this.search.isEmpty()) {
         boolean overClear = inside(mouseX, mouseY, sx + sw - 14, sy, 14, 16);
         g.text(this.font, "x", sx + sw - 10, sy + 4, Glass.withAlpha(overClear ? Glass.TEXT : Glass.TEXT_FAINT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.CLEAR, sx + sw - 14, sy, 14, 16, null);
      }

      if (overSearch) {
         this.tip = "Type anywhere to search  ·  setting names count too  ·  Ctrl+F focuses  ·  Esc clears";
      }

      int fx = sx + sw + 6;
      for (String f : FILTERS) {
         int fw = this.font.width(f) + 12;
         boolean active = f.equals(this.filter);
         boolean over = inside(mouseX, mouseY, fx, sy, fw, 16);
         Glass.roundRect(g, fx, sy, fx + fw, sy + 16, 4, Glass.withAlpha(active ? accent : (over ? this.surface3 : this.surface2), this.a(active ? 170 : 255)));
         g.text(this.font, f, fx + 6, sy + 4, Glass.withAlpha(active || over ? Glass.TEXT : Glass.TEXT_DIM, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.FILTER, fx, sy, fw, 16, f);
         if (over) {
            this.tip = switch (f) {
               case "On" -> "Only show modules that are switched on";
               case "Bound" -> "Only show modules with a key bind";
               case FAVORITES -> "Your starred modules from every category";
               default -> "Show every module";
            };
         }

         fx += fw + 3;
      }
   }

   private void drawModuleList(GuiGraphicsExtractor g, int top, int bottom, int mouseX, int mouseY, int accent, int accent2) {
      int x = this.listX;
      int w = this.listW;
      String heading = !this.search.isBlank()
         ? "Results for \"" + this.search.trim() + "\""
         : (FAVORITES.equals(this.filter) ? "Favorites" : this.category.displayName());
      g.text(this.font, fit(this.font, heading, w - 60), x + 2, top, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
      String count = this.shown.size() + (this.shown.size() == 1 ? " module" : " modules");
      g.text(this.font, count, x + w - this.font.width(count), top, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
      this.listViewTop = top + 17;
      this.listViewBottom = bottom - 4;
      int viewH = this.listViewBottom - this.listViewTop;
      int contentHeight = this.shown.size() * (CARD_H + CARD_GAP);
      float maxScroll = Math.max(0, contentHeight - viewH);
      this.listScrollTarget = Mth.clamp(this.listScrollTarget, 0.0F, maxScroll);
      this.listScroll = Mth.clamp(Glass.approach(this.listScroll, this.listScrollTarget, 18.0F, this.dt), 0.0F, maxScroll);
      if (this.shown.isEmpty()) {
         String empty = this.search.isBlank() ? "Nothing matches this filter." : "No module or setting matches your search.";
         g.text(this.font, empty, x + (w - this.font.width(empty)) / 2, this.listViewTop + 20, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
         return;
      }

      g.enableScissor(x, this.listViewTop, x + w, this.listViewBottom);
      int y = this.listViewTop - Math.round(this.listScroll);
      boolean searchMode = !this.search.isBlank();
      for (Module module : this.shown) {
         if (y + CARD_H >= this.listViewTop && y <= this.listViewBottom) {
            boolean over = inside(mouseX, mouseY, x, y, w, CARD_H) && mouseY >= this.listViewTop && mouseY < this.listViewBottom;
            this.drawCard(g, module, x, y, w, over, module == this.selected, searchMode, accent, accent2);
            if (over) {
               this.tip = blocked(module) ? plain(module.blockedReason()) + "  ·  you can still change its settings" : module.getDescription();
            }
         } else {
            this.hoverAnim.remove(module);
         }

         y += CARD_H + CARD_GAP;
      }

      g.disableScissor();
      this.scrollbar(g, x + w + 2, this.listViewTop, viewH, contentHeight, this.listScroll, accent);
   }

   /** A module that cannot be switched on right now (no world, sandbox off...). The ClickGUI module is never toggled. */
   private static boolean blocked(Module module) {
      return !(module instanceof ClickGuiModule) && !module.canActivate();
   }

   private void drawCard(GuiGraphicsExtractor g, Module module, int x, int y, int w, boolean over, boolean selected, boolean searchMode, int accent, int accent2) {
      boolean blocked = blocked(module);
      boolean switchedOn = blocked ? module.isSavedEnabled() : module.isEnabled();
      float hover = this.anim(this.hoverAnim, module, over ? 1.0F : 0.0F, 16.0F);
      float on = this.anim(this.enableAnim, module, switchedOn ? 1.0F : 0.0F, 12.0F);
      Glass.roundRect(g, x, y, x + w, y + CARD_H, 4, Glass.withAlpha(selected ? this.surface3 : this.surface2, this.a(255)));
      if (on > 0.01F && !blocked) {
         Glass.roundRect(g, x, y, x + w, y + CARD_H, 4, Glass.withAlpha(accent, this.a(Math.round(34.0F * on))));
         g.fill(x, y + 5, x + 2, y + CARD_H - 5, Glass.withAlpha(accent2, this.a(Math.round(255.0F * on))));
      }

      if (hover > 0.01F) {
         Glass.roundRect(g, x, y, x + w, y + CARD_H, 4, Glass.withAlpha(0xFFFFFFFF, this.a(Math.round(9.0F * hover))));
      }

      if (selected) {
         Glass.roundOutline(g, x, y, x + w, y + CARD_H, 4, Glass.withAlpha(accent, this.a(130)));
      }

      // The card is registered before its switch so a click on the switch toggles instead of selecting.
      this.hit(WindowGuiScreen.Kind.CARD, x, y, w, CARD_H, module, this.listViewTop, this.listViewBottom);
      int right = x + w - 8;
      if (!(module instanceof ClickGuiModule)) {
         int sx = right - 18;
         Glass.toggle(g, sx, y + 10, on, blocked ? 0xFF59606E : Glass.lerp(accent, accent2, 0.35F), this.open * (blocked ? 0.6F : 1.0F));
         this.hit(WindowGuiScreen.Kind.SWITCH, sx - 4, y, 26, CARD_H, module, this.listViewTop, this.listViewBottom);
         right = sx - 6;
      }

      ClickGuiModule style = style();
      boolean starred = style != null && style.isFavorite(module);
      if (starred || hover > 0.3F) {
         int starW = this.font.width(FAVORITES);
         int starAlpha = starred ? 255 : Math.round(140.0F * hover);
         g.text(this.font, FAVORITES, right - starW, y + 6, Glass.withAlpha(starred ? accent2 : Glass.TEXT_FAINT, this.a(starAlpha)), false);
         this.hit(WindowGuiScreen.Kind.STAR, right - starW - 3, y, starW + 6, CARD_H, module, this.listViewTop, this.listViewBottom);
         right -= starW + 6;
      }

      String key = this.binding == module ? "..." : (module.getKeybind() == -1 ? null : GeminiKeys.displayName(module.getKeybind()));
      if (key != null && !"NONE".equals(key)) {
         String label = module.isHoldBind() ? key + " hold" : key;
         int kw = this.font.width(label) + 6;
         Glass.roundRect(g, right - kw, y + 6, right, y + 17, 3, Glass.withAlpha(this.surface, this.a(255)));
         g.text(this.font, label, right - kw + 3, y + 8, Glass.withAlpha(this.binding == module ? accent2 : Glass.TEXT_DIM, this.a(255)), false);
         right -= kw + 6;
      }

      int nameColor = blocked ? Glass.TEXT_FAINT : (module.isEnabled() ? Glass.TEXT : (over ? Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, 0.7F) : Glass.lerp(Glass.TEXT_DIM, Glass.TEXT, 0.4F)));
      int textX = x + 9 + Math.round(hover * 2.0F);
      g.text(this.font, fit(this.font, module.getName(), right - textX - 6), textX, y + 6, Glass.withAlpha(nameColor, this.a(255)), false);
      Fuzzy.Match match = searchMode ? this.matches.get(module) : null;
      if (match != null && match.setting() != null) {
         // the search found this module through one of its settings: say which
         String hint = "setting: " + match.setting();
         g.text(this.font, fit(this.font, hint, x + w - 38 - textX), textX, y + 18, Glass.withAlpha(accent2, this.a(255)), false);
      } else {
         String description = searchMode ? module.getCategory().displayName() + "  ·  " + module.getDescription() : module.getDescription();
         g.text(this.font, fit(this.font, description, x + w - 38 - textX), textX, y + 18, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
      }
   }

   private void drawSettings(GuiGraphicsExtractor g, int top, int bottom, int mouseX, int mouseY, int accent, int accent2) {
      int x = this.settingsX;
      int w = this.settingsW;
      Glass.roundRect(g, x, top - 2, x + w, bottom - 4, 5, Glass.withAlpha(this.surface2, this.a(255)));
      Glass.roundOutline(g, x, top - 2, x + w, bottom - 4, 5, Glass.withAlpha(0xFFFFFFFF, this.a(14)));
      Module module = this.selected;
      if (module == null) {
         String text = "Select a module to see its settings";
         g.text(this.font, text, x + (w - this.font.width(text)) / 2, top + 30, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
         return;
      }

      boolean blocked = blocked(module);
      g.text(this.font, fit(this.font, module.getName(), w - 50), x + 9, top + 6, Glass.withAlpha(Glass.TEXT, this.a(255)), true);
      if (!(module instanceof ClickGuiModule)) {
         float on = this.anim(this.enableAnim, module, (blocked ? module.isSavedEnabled() : module.isEnabled()) ? 1.0F : 0.0F, 12.0F);
         int sx = x + w - 27;
         Glass.toggle(g, sx, top + 6, on, blocked ? 0xFF59606E : Glass.lerp(accent, accent2, 0.35F), this.open * (blocked ? 0.6F : 1.0F));
         this.hit(WindowGuiScreen.Kind.SWITCH, sx - 3, top + 2, 24, 16, module);
      }

      int ty = top + 21;
      if (blocked) {
         String reason = plain(module.blockedReason());
         g.text(this.font, fit(this.font, "Can't switch on now: " + reason, w - 18), x + 9, ty, Glass.withAlpha(Glass.WARN, this.a(255)), false);
         if (inside(mouseX, mouseY, x + 9, ty, w - 18, LINE)) {
            this.tip = reason + "  ·  settings changed here apply when it can run";
         }

         ty += LINE;
      }

      List<String> lines = wrap(this.font, module.getDescription(), w - 18);
      for (int i = 0; i < Math.min(blocked ? 2 : 3, lines.size()); i++) {
         g.text(this.font, lines.get(i), x + 9, ty, Glass.withAlpha(Glass.TEXT_DIM, this.a(255)), false);
         ty += LINE;
      }

      this.settingsViewTop = ty + 6;
      this.settingsViewBottom = bottom - 8;
      g.fill(x + 8, this.settingsViewTop - 2, x + w - 8, this.settingsViewTop - 1, Glass.withAlpha(0xFFFFFFFF, this.a(16)));
      List<Setting<?>> settings = new ArrayList<>();
      for (Setting<?> setting : module.settings()) {
         if (setting.visible()) {
            settings.add(setting);
         }
      }

      boolean bindable = module.isBindable();
      int contentHeight = (bindable ? 22 : 0) + 4;
      for (Setting<?> setting : settings) {
         contentHeight += this.rowHeight(setting);
      }

      int viewH = this.settingsViewBottom - this.settingsViewTop;
      if (this.revealSetting != null) {
         // scroll the setting the search matched into view, once
         int offset = (bindable ? 22 : 0) + 4;
         for (Setting<?> setting : settings) {
            if (setting.name().equals(this.revealSetting)) {
               this.settingsScrollTarget = Math.max(0, offset - viewH / 3);
               break;
            }

            offset += this.rowHeight(setting);
         }

         this.revealSetting = null;
      }

      float maxScroll = Math.max(0, contentHeight - viewH);
      this.settingsScrollTarget = Mth.clamp(this.settingsScrollTarget, 0.0F, maxScroll);
      this.settingsScroll = Mth.clamp(Glass.approach(this.settingsScroll, this.settingsScrollTarget, 18.0F, this.dt), 0.0F, maxScroll);
      String query = this.search.trim();
      g.enableScissor(x, this.settingsViewTop, x + w, this.settingsViewBottom);
      int ry = this.settingsViewTop + 4 - Math.round(this.settingsScroll);
      if (bindable) {
         this.drawBindRow(g, module, x + 4, ry, w - 8, mouseX, mouseY, accent2);
         ry += 22;
      }

      for (Setting<?> setting : settings) {
         int rh = this.rowHeight(setting);
         if (ry + rh >= this.settingsViewTop && ry <= this.settingsViewBottom) {
            boolean over = inside(mouseX, mouseY, x + 4, ry, w - 8, rh) && mouseY >= this.settingsViewTop && mouseY < this.settingsViewBottom;
            if (!query.isEmpty() && Fuzzy.substringScore(query, setting.name()) > 0) {
               // a setting the search matched: tinted with an accent bar so it stands out in the list
               Glass.roundRect(g, x + 4, ry, x + w - 4, ry + rh - 1, 3, Glass.withAlpha(accent, this.a(38)));
               g.fill(x + 4, ry + 3, x + 6, ry + rh - 4, Glass.withAlpha(accent2, this.a(255)));
            }

            this.drawSetting(g, setting, x + 4, ry, w - 8, rh, over, mouseX, mouseY, accent, accent2);
            if (over) {
               this.tip = setting.description();
            }
         }

         ry += rh;
      }

      g.disableScissor();
      if (settings.isEmpty() && !bindable) {
         String text = "This module has no settings";
         g.text(this.font, text, x + (w - this.font.width(text)) / 2, this.settingsViewTop + 12, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
      }

      this.scrollbar(g, x + w - 4, this.settingsViewTop, viewH, contentHeight, this.settingsScroll, accent);
   }

   private void drawBindRow(GuiGraphicsExtractor g, Module module, int x, int y, int w, int mouseX, int mouseY, int accent2) {
      boolean over = inside(mouseX, mouseY, x, y, w, 20) && mouseY >= this.settingsViewTop && mouseY < this.settingsViewBottom;
      if (over) {
         Glass.roundRect(g, x, y, x + w, y + 20, 3, Glass.withAlpha(0xFFFFFFFF, this.a(10)));
         this.tip = "Click and press a key to bind  ·  right-click clears  ·  Bind Mode picks Toggle or Hold";
      }

      g.text(this.font, "Keybind", x + 8, y + 6, Glass.withAlpha(Glass.TEXT_DIM, this.a(255)), false);
      String key;
      if (this.binding == module) {
         key = "Press a key...";
      } else {
         key = module.getKeybind() == -1 ? "None" : GeminiKeys.displayName(module.getKeybind());
         if (module.getKeybind() != -1 && module.isHoldBind()) {
            key = key + "  (hold)";
         }
      }

      int kw = this.font.width(key) + 10;
      Glass.roundRect(g, x + w - 8 - kw, y + 4, x + w - 8, y + 16, 3, Glass.withAlpha(this.binding == module ? accent2 : this.surface3, this.a(this.binding == module ? 120 : 255)));
      g.text(this.font, key, x + w - 8 - kw + 5, y + 6, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
      this.hit(WindowGuiScreen.Kind.BIND, x, y, w, 20, module, this.settingsViewTop, this.settingsViewBottom);
   }

   private void drawSetting(GuiGraphicsExtractor g, Setting<?> setting, int x, int y, int w, int h, boolean over, int mouseX, int mouseY, int accent, int accent2) {
      SettingControl control = this.control(setting);
      if (control != null) {
         // numbers and colours: the shared controls draw and edit themselves
         boolean live = mouseY >= this.settingsViewTop && mouseY < this.settingsViewBottom;
         control.setOpacity(this.open);
         control.setAccent(accent, accent2);
         control.render(g, x, y, w, live ? mouseX : SettingControl.NO_MOUSE, live ? mouseY : SettingControl.NO_MOUSE, this.dt);
         this.hit(WindowGuiScreen.Kind.CONTROL, x, y, w, control.height(), control, this.settingsViewTop, this.settingsViewBottom);
         return;
      }

      if (over) {
         Glass.roundRect(g, x, y, x + w, y + h, 3, Glass.withAlpha(0xFFFFFFFF, this.a(8)));
      }

      int left = x + 8;
      int right = x + w - 8;
      int label = Glass.withAlpha(!setting.isDefault() && !(setting instanceof ActionSetting) ? Glass.TEXT : Glass.TEXT_DIM, this.a(255));
      if (setting instanceof BooleanSetting bool) {
         float on = this.settingAnim(setting, bool.get() ? 1.0F : 0.0F);
         g.text(this.font, fit(this.font, setting.name(), right - left - 34), left, y + 6, label, false);
         Glass.toggle(g, right - 18, y + 5, on, Glass.lerp(accent, accent2, 0.35F), this.open);
         this.hit(WindowGuiScreen.Kind.BOOL, x, y, w, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ModeSetting mode) {
         String value = mode.get();
         int pillW = Math.min(right - left - 56, this.font.width(value) + 28);
         int pillL = right - pillW;
         g.text(this.font, fit(this.font, setting.name(), pillL - left - 10), left, y + 6, label, false);
         Glass.roundRect(g, pillL, y + 3, right, y + 16, 3, Glass.withAlpha(accent, this.a(over ? 70 : 40)));
         g.text(this.font, "<", pillL + 5, y + 6, Glass.withAlpha(over ? Glass.TEXT : Glass.TEXT_FAINT, this.a(255)), false);
         g.text(this.font, ">", right - 10, y + 6, Glass.withAlpha(over ? Glass.TEXT : Glass.TEXT_FAINT, this.a(255)), false);
         String shownValue = fit(this.font, value, pillW - 22);
         g.text(this.font, shownValue, pillL + (pillW - this.font.width(shownValue)) / 2, y + 6, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.MODE, pillL, y, pillW, h, setting, this.settingsViewTop, this.settingsViewBottom);
         this.hit(WindowGuiScreen.Kind.MODE, x, y, pillL - x, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ListSetting list) {
         String value = list.size() + (list.size() == 1 ? " entry  >" : " entries  >");
         int valueW = this.font.width(value);
         g.text(this.font, fit(this.font, setting.name(), right - left - valueW - 12), left, y + 6, label, false);
         g.text(this.font, value, right - valueW, y + 6, Glass.withAlpha(over ? Glass.TEXT : accent2, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.LIST, x, y, w, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ActionSetting) {
         Glass.roundRect(g, left, y + 2, right, y + h - 2, 4, Glass.withAlpha(accent, this.a(over ? 150 : 85)));
         String text = fit(this.font, setting.name(), right - left - 8);
         g.text(this.font, text, left + (right - left - this.font.width(text)) / 2, y + (h - 8) / 2, Glass.withAlpha(Glass.TEXT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.ACTION, left, y + 2, right - left, h - 4, setting, this.settingsViewTop, this.settingsViewBottom);
      }
   }

   private int rowHeight(Setting<?> setting) {
      SettingControl control = this.control(setting);
      if (control != null) {
         return control.height() + 2;
      }

      return setting instanceof ActionSetting ? 24 : 20;
   }

   private SettingControl control(Setting<?> setting) {
      if (!(setting instanceof NumberSetting) && !(setting instanceof ColorSetting)) {
         return null;
      }

      SettingControl control = this.controls.get(setting);
      if (control == null) {
         control = SettingControl.of(setting);
         control.setOnChange(WindowGuiScreen::save);
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

   private void scrollbar(GuiGraphicsExtractor g, int x, int top, int viewH, int contentHeight, float scroll, int accent) {
      if (contentHeight > viewH && viewH > 8) {
         int thumbH = Math.max(14, Math.round(viewH * ((float)viewH / contentHeight)));
         float max = Math.max(1, contentHeight - viewH);
         int thumbY = top + Math.round((viewH - thumbH) * (scroll / max));
         g.fill(x, top, x + 2, top + viewH, Glass.withAlpha(0xFFFFFFFF, this.a(10)));
         g.fill(x, thumbY, x + 2, thumbY + thumbH, Glass.withAlpha(accent, this.a(170)));
      }
   }

   private void drawFooter(GuiGraphicsExtractor g, int x, int y, int w, int accent2, long now) {
      boolean flashing = this.flash != null && now < this.flashUntil;
      DashboardPage pageView = this.pageFor(this.page);
      String hint = pageView == null ? "Esc close  ·  RMB toggle  ·  MMB bind  ·  ★ star" : "Esc close  ·  Tab next tab";
      int hintW = this.font.width(hint);
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
         text = pageView == null ? "Click a module to edit it  ·  ↑↓ select  ·  Enter toggles" : pageView.hint();
      }

      text = plain(text);
      g.text(
         this.font, fit(this.font, text, w - hintW - 36), x + 8, y + 6,
         Glass.withAlpha(flashing || this.binding != null || this.bindingMenu ? accent2 : Glass.TEXT_DIM, this.a(255)), false
      );
      g.text(this.font, hint, x + w - 8 - hintW, y + 6, Glass.withAlpha(Glass.TEXT_FAINT, this.a(255)), false);
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

      WindowGuiScreen.Hit hit = null;
      for (int i = this.hits.size() - 1; i >= 0; i--) {
         if (this.hits.get(i).contains(mx, my)) {
            hit = this.hits.get(i);
            break;
         }
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
            this.listScrollTarget = 0.0F;
         }
         case CATEGORY -> this.selectCategory((Category)hit.target);
         case CARD -> {
            Module module = (Module)hit.target;
            if (button == 1) {
               this.toggle(module);
            } else if (button == 2) {
               this.beginBind(module);
               this.select(module);
            } else {
               this.select(module);
            }
         }
         case SWITCH -> this.toggle((Module)hit.target);
         case STAR -> {
            ClickGuiModule style = style();
            if (style != null) {
               Module module = (Module)hit.target;
               boolean starred = style.toggleFavorite(module);
               this.showNotice(starred ? module.getName() + " starred" : module.getName() + " unstarred");
            }
         }
         case BIND -> {
            Module module = (Module)hit.target;
            if (button == 1) {
               module.setKeybind(-1);
               this.showNotice(module.getName() + " unbound");
            } else {
               this.beginBind(module);
            }
         }
         case BOOL -> {
            BooleanSetting bool = (BooleanSetting)hit.target;
            if (button == 1) {
               bool.reset();
            } else {
               bool.toggle();
            }

            save();
         }
         case MODE -> {
            ModeSetting mode = (ModeSetting)hit.target;
            if (button == 1 || button == 0 && mx < hit.x + 14) {
               mode.cycleBack();
            } else if (button == 2) {
               mode.reset();
            } else {
               mode.cycle();
            }

            save();
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
         case LIST -> {
            if (this.selected != null) {
               this.minecraft.gui.setScreen(new ListEditorScreen(this, this.selected, (ListSetting)hit.target));
            }
         }
         case ACTION -> {
            ((ActionSetting)hit.target).run();
            save();
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
      float speed = (style == null ? 2.0F : style.scrollSpeed()) * 12.0F;
      double mx = this.vx(x);
      double my = this.vy(y);
      DashboardPage pageView = this.pageFor(this.page);
      if (pageView != null) {
         return pageView.mouseScrolled(mx, my, scrollY * speed) || super.mouseScrolled(x, y, scrollX, scrollY);
      }

      if (mx >= this.listX && mx < this.listX + this.listW + 6 && my >= this.listViewTop && my < this.listViewBottom) {
         this.listScrollTarget -= (float)(scrollY * speed);
         return true;
      } else if (mx >= this.settingsX && mx < this.settingsX + this.settingsW && my >= this.settingsViewTop && my < this.settingsViewBottom) {
         this.settingsScrollTarget -= (float)(scrollY * speed);
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
         if (pageView == null && !this.search.isEmpty()) {
            this.leaveSearch();
         } else {
            this.onClose();
         }

         return true;
      } else if (!this.searchFocused && GeminiKeys.OPEN_GUI.matches(event)) {
         this.onClose();
         return true;
      } else if (key == Keys.F && Controls.ctrlDown()) {
         this.showPage(Page.MODULES);
         this.searchFocused = true;
         return true;
      } else if (key == Keys.TAB) {
         this.cycleTab(Controls.shiftDown() ? -1 : 1);
         return true;
      } else if (pageView != null) {
         return super.keyPressed(event);
      } else if (key == Keys.BACKSPACE) {
         if (!this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            this.listScrollTarget = 0.0F;
         }

         return true;
      } else if (key == Keys.DOWN || key == Keys.UP) {
         this.moveSelection(key == Keys.DOWN ? 1 : -1);
         return true;
      } else if (Keys.isEnter(key)) {
         if (this.selected != null) {
            this.toggle(this.selected);
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
         this.listScrollTarget = 0.0F;
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
         DashboardPage pageView = this.pageFor(target);
         if (pageView != null) {
            pageView.opened();
         }
      }
   }

   /** Tab / Shift+Tab: through the categories, then the pages, and around. */
   private void cycleTab(int step) {
      List<Object> tabs = new ArrayList<>(List.of(Category.values()));
      tabs.addAll(List.of(PAGE_TABS));
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
      this.listScrollTarget = 0.0F;
      if (this.selected != null) {
         this.category = this.selected.getCategory();
         this.revealed = false;
      }
   }

   private void selectCategory(Category cat) {
      this.showPage(Page.MODULES);
      this.category = cat;
      this.search = "";
      this.searchFocused = false;
      if (FAVORITES.equals(this.filter)) {
         this.filter = "All";
      }

      this.listScrollTarget = 0.0F;
      this.listScroll = 0.0F;
      List<Module> list = this.visibleModules();
      this.select(list.isEmpty() ? null : list.get(0));
   }

   private void select(Module module) {
      if (module != this.selected) {
         this.selected = module;
         this.settingsScroll = 0.0F;
         this.settingsScrollTarget = 0.0F;
         Fuzzy.Match match = this.search.isBlank() || module == null ? null : this.matches.get(module);
         this.revealSetting = match == null ? null : match.setting();
      }
   }

   private void moveSelection(int step) {
      List<Module> list = this.visibleModules();
      if (!list.isEmpty()) {
         int index = this.selected == null ? -1 : list.indexOf(this.selected);
         int next = Mth.clamp(index + step, 0, list.size() - 1);
         this.select(list.get(next));
         int top = next * (CARD_H + CARD_GAP);
         int view = this.listViewBottom - this.listViewTop;
         if (top < this.listScrollTarget) {
            this.listScrollTarget = top;
         } else if (top + CARD_H > this.listScrollTarget + view) {
            this.listScrollTarget = top + CARD_H - view;
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

   private int a(int alpha) {
      return Math.round(alpha * this.open);
   }

   private float anim(Map<Module, Float> map, Module key, float target, float speed) {
      float value = Glass.approach(map.getOrDefault(key, target), target, speed, this.dt);
      map.put(key, value);
      return value;
   }

   private float settingAnim(Setting<?> setting, float target) {
      float value = Glass.approach(this.settingAnim.getOrDefault(setting, target), target, 16.0F, this.dt);
      this.settingAnim.put(setting, value);
      return value;
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

   static String fit(Font font, String text, int maxWidth) {
      return maxWidth <= 4 ? "" : Controls.fit(font, text, maxWidth);
   }

   static List<String> wrap(Font font, String text, int maxWidth) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();
      for (String word : text.split("\\s+")) {
         if (line.length() == 0) {
            line.append(word);
         } else if (font.width(line + " " + word) <= maxWidth) {
            line.append(' ').append(word);
         } else {
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
         }
      }

      if (line.length() > 0) {
         lines.add(line.toString());
      }

      return lines;
   }

   static void roundRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
      Glass.roundRect(g, x1, y1, x2, y2, radius, color);
   }

   private static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
      g.fill(x + 1, y, x + 4, y + 1, color);
      g.fill(x, y + 1, x + 1, y + 4, color);
      g.fill(x + 4, y + 1, x + 5, y + 4, color);
      g.fill(x + 1, y + 4, x + 4, y + 5, color);
      g.fill(x + 4, y + 5, x + 5, y + 6, color);
      g.fill(x + 5, y + 6, x + 6, y + 7, color);
   }

   /** 7x7 pixel icons per category and page (by enum name), drawn with fills so they take the theme colour. */
   private static void icon(GuiGraphicsExtractor g, String name, int x, int y, int color) {
      String[] rows = switch (name) {
         case "COMBAT" -> new String[]{".....##", "....###", "...###.", "#.###..", ".###...", ".##....", "#.#...."};
         case "MOVEMENT" -> new String[]{"#..#...", ".#..#..", "..#..#.", "...#..#", "..#..#.", ".#..#..", "#..#..."};
         case "PLAYER" -> new String[]{"..###..", "..###..", "...#...", ".#####.", "...#...", "..#.#..", ".#...#."};
         case "WORLD" -> new String[]{"..###..", ".#.#.#.", "#..#..#", "#######", "#..#..#", ".#.#.#.", "..###.."};
         case "RENDER" -> new String[]{".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."};
         case "HUD" -> new String[]{"#######", "#.....#", "#######", "#..#..#", "#..#..#", "#..#..#", "#######"};
         case "PROFILES" -> new String[]{"######.", "#.##.##", "#.##..#", "#.....#", "#.###.#", "#.###.#", "#######"};
         case "KEYBINDS" -> new String[]{"..###..", ".#...#.", ".#...#.", "..###..", "...#...", "...##..", "...##.."};
         case "BARITONE" -> new String[]{"#......", "##.....", "#.#....", "#..#...", "#...###", "#......", "#......"};
         default -> new String[]{"...#...", ".#####.", ".#...#.", "##.#.##", ".#...#.", ".#####.", "...#..."};
      };
      for (int row = 0; row < rows.length; row++) {
         String line = rows[row];
         int start = -1;
         for (int col = 0; col <= line.length(); col++) {
            boolean set = col < line.length() && line.charAt(col) == '#';
            if (set && start < 0) {
               start = col;
            } else if (!set && start >= 0) {
               g.fill(x + start, y + row, x + col, y + row + 1, color);
               start = -1;
            }
         }
      }
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
      BOOL,
      CONTROL,
      MODE,
      LIST,
      ACTION,
      LAYOUT,
      HUD_EDIT,
      MENU_KEY,
      PALETTE,
      HELP;
   }

   private record PageHit(Object owner, DashboardPage.Click click) {
   }

   private record Hit(WindowGuiScreen.Kind kind, int x, int y, int w, int h, Object target) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
