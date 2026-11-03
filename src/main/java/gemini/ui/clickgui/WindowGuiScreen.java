package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.input.KeybindReport;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.ClickGuiModule;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.GeminiMark;
import gemini.ui.HudOverlay;
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
 * The default Gemini menu: one centred window with a category sidebar, a searchable module list and a settings
 * pane for the selected module. Drawing and hit-testing share one pass: every clickable element registers a hit
 * rectangle while it is drawn, so input always matches what is on screen.
 */
public final class WindowGuiScreen extends Screen {
   private static final int SIDEBAR = 116;
   private static final int CARD_H = 30;
   private static final int CARD_GAP = 4;
   private static final int FOOTER = 20;
   private static final int LINE = 12;
   private static final int TEXT = -1;
   private static final int TEXT_DIM = -5592406;
   private static final int TEXT_FAINT = -8355185;
   private static final int SURFACE = -15723481;
   private static final int SURFACE_2 = -15196629;
   private static final int SURFACE_3 = -14604492;
   private static final String[] FILTERS = new String[]{"All", "On", "Bound"};
   private static Category lastCategory = Category.COMBAT;
   private static String lastModule;
   private static String rememberedSearch = "";
   private final long openedAt = Util.getMillis();
   private final List<WindowGuiScreen.Hit> hits = new ArrayList<>();
   private final Map<Module, Float> hoverAnim = new HashMap<>();
   private final Map<Module, Float> enableAnim = new HashMap<>();
   private final Map<Setting<?>, Float> settingAnim = new IdentityHashMap<>();
   private long lastFrame = this.openedAt;
   private float open;
   private float uiScale = 1.0F;
   private int vw;
   private int vh;
   private int slide;
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
   private Setting<?> dragging;
   private int dragLeft;
   private int dragRight;
   private String tip;
   private String flash;
   private long flashUntil;
   private List<Module> shown = List.of();
   private boolean revealed;

   public WindowGuiScreen() {
      super(Component.literal("Gemini Client"));
      ClickGuiModule style = style();
      if (style != null && style.keepSearch()) {
         this.search = rememberedSearch;
      }
   }

   @Override
   protected void init() {
      ClickGuiModule style = style();
      float base = style == null ? 1.0F : style.scale();
      float scale = base;
      if (style == null || style.autoFit()) {
         scale = Math.min(base, Math.min(this.width / 470.0F, this.height / 290.0F));
      }

      this.uiScale = Mth.clamp(scale, 0.45F, 2.0F);
      this.vw = Math.round(this.width / this.uiScale);
      this.vh = Math.round(this.height / this.uiScale);
      if (this.selected == null && lastModule != null && Gemini.getInstance() != null) {
         this.selected = Gemini.getInstance().modules().getByName(lastModule).orElse(null);
      }
   }

   // ------------------------------------------------------------------ rendering

   @Override
   public void extractRenderState(GuiGraphicsExtractor g, int rawMouseX, int rawMouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = Math.min(0.05F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      ClickGuiModule style = style();
      boolean animate = style == null || style.animations();
      this.open = animate ? ease(Math.min(1.0F, (now - this.openedAt) / 220.0F)) : 1.0F;
      this.slide = Math.round((1.0F - this.open) * 12.0F);
      int accent = style == null ? -16748289 : style.accentColor();
      int accent2 = style == null ? -16722689 : style.secondaryColor();
      float glass = style == null ? 0.9F : style.opacity();
      this.hits.clear();
      this.tip = null;
      if (style == null || style.dim()) {
         g.fill(0, 0, this.width, this.height, GeminiMark.withAlpha(-16777216, this.a(150)));
      }

      int mouseX = Math.round(rawMouseX / this.uiScale);
      int mouseY = Math.round(rawMouseY / this.uiScale) - this.slide;
      g.pose().pushMatrix();
      g.pose().scale(this.uiScale, this.uiScale);
      g.pose().translate(0.0F, this.slide);
      int ww = Mth.clamp(this.vw - 32, 440, 680);
      int wh = Mth.clamp(this.vh - 32, 270, 400);
      ww = Math.min(ww, this.vw - 8);
      wh = Math.min(wh, this.vh - 8);
      int wx = (this.vw - ww) / 2;
      int wy = (this.vh - wh) / 2;
      this.drawShadow(g, wx, wy, ww, wh, accent);
      roundRect(g, wx, wy, wx + ww, wy + wh, 6, GeminiMark.withAlpha(SURFACE, this.a(Math.round(255.0F * glass))));
      roundRect(g, wx, wy, wx + SIDEBAR, wy + wh, 6, GeminiMark.withAlpha(SURFACE_2, this.a(Math.round(255.0F * glass))));
      g.fill(wx + SIDEBAR - 6, wy, wx + SIDEBAR, wy + wh, GeminiMark.withAlpha(SURFACE_2, this.a(Math.round(255.0F * glass))));
      g.fill(wx + SIDEBAR, wy + 6, wx + SIDEBAR + 1, wy + wh - 6, GeminiMark.withAlpha(-1, this.a(14)));
      gradient(g, wx + 6, wy, wx + ww - 6, wy + 2, accent, accent2, this.a(255));
      this.drawSidebar(g, wx, wy, wh, mouseX, mouseY, accent, accent2, delta);
      int cx = wx + SIDEBAR + 1;
      int cw = ww - SIDEBAR - 1;
      this.drawHeader(g, cx, wy, cw, mouseX, mouseY, accent, accent2, now);
      int bodyTop = wy + 32;
      int bodyBottom = wy + wh - FOOTER;
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

      this.drawModuleList(g, bodyTop, bodyBottom, mouseX, mouseY, accent, accent2, delta);
      this.drawSettings(g, bodyTop, bodyBottom, mouseX, mouseY, accent, accent2, delta);
      this.drawFooter(g, cx, bodyBottom, cw, accent2, now);
      g.pose().popMatrix();
      super.extractRenderState(g, rawMouseX, rawMouseY, partialTick);
   }

   private void drawShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
      for (int i = 6; i >= 1; i--) {
         roundRect(g, x - i, y - i + 2, x + w + i, y + h + i + 2, 6 + i, GeminiMark.withAlpha(-16777216, this.a(14)));
      }

      roundRect(g, x - 1, y - 1, x + w + 1, y + h + 1, 7, GeminiMark.withAlpha(accent, this.a(40)));
   }

   private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int h, int mouseX, int mouseY, int accent, int accent2, float delta) {
      float pulse = 0.75F + 0.25F * (float)Math.sin(Util.getMillis() / 500.0);
      GeminiMark.draw(g, x + 10, y + 10, 22, accent, pulse);
      g.text(this.font, "Gemini", x + 37, y + 11, GeminiMark.withAlpha(TEXT, this.a(255)), true);
      String version = "dev".equals(Gemini.VERSION) ? "dev build" : "v" + Gemini.VERSION;
      g.text(this.font, version, x + 37, y + 22, GeminiMark.withAlpha(accent2, this.a(255)), false);
      int iy = y + 42;
      for (Category cat : Category.values()) {
         boolean active = this.search.isBlank() && cat == this.category;
         boolean over = inside(mouseX, mouseY, x + 6, iy, SIDEBAR - 12, 20);
         if (active) {
            roundRect(g, x + 6, iy, x + SIDEBAR - 6, iy + 20, 4, GeminiMark.withAlpha(accent, this.a(46)));
            g.fill(x + 6, iy + 4, x + 8, iy + 16, GeminiMark.withAlpha(accent, this.a(255)));
         } else if (over) {
            roundRect(g, x + 6, iy, x + SIDEBAR - 6, iy + 20, 4, GeminiMark.withAlpha(-1, this.a(10)));
         }

         int iconColor = active ? accent2 : HudOverlay.lerp(cat.accent(), -1, 0.35F);
         icon(g, cat, x + 14, iy + 7, GeminiMark.withAlpha(iconColor, this.a(active || over ? 255 : 190)));
         int on = 0;
         for (Module module : modules(cat)) {
            if (module.isEnabled() && module.isListed()) {
               on++;
            }
         }

         int badge = on > 0 ? this.font.width(Integer.toString(on)) + 6 + 8 : 0;
         String catName = fit(this.font, cat.displayName(), SIDEBAR - 27 - 10 - badge);
         g.text(this.font, catName, x + 27, iy + 6, GeminiMark.withAlpha(active ? TEXT : (over ? -2302756 : TEXT_DIM), this.a(255)), false);

         if (on > 0) {
            String count = Integer.toString(on);
            int bw = this.font.width(count) + 6;
            int bx = x + SIDEBAR - 12 - bw;
            roundRect(g, bx, iy + 5, bx + bw, iy + 15, 3, GeminiMark.withAlpha(active ? accent : SURFACE_3, this.a(active ? 150 : 255)));
            g.text(this.font, count, bx + 3, iy + 6, GeminiMark.withAlpha(TEXT, this.a(255)), false);
         }

         this.hit(WindowGuiScreen.Kind.CATEGORY, x + 6, iy, SIDEBAR - 12, 20, cat);
         if (over) {
            this.tip = modules(cat).size() + " modules  ·  Tab / Shift+Tab switches category";
         }

         iy += 22;
      }

      int by = y + h - 24;
      this.sidebarButton(g, x, by, this.bindingMenu ? "Press a key..." : "Menu: " + GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString(), WindowGuiScreen.Kind.MENU_KEY, mouseX, mouseY, accent, this.bindingMenu);
      by -= 20;
      this.sidebarButton(g, x, by, "Classic panels", WindowGuiScreen.Kind.LAYOUT, mouseX, mouseY, accent, false);
      by -= 20;
      this.sidebarButton(g, x, by, "Edit HUD", WindowGuiScreen.Kind.HUD_EDIT, mouseX, mouseY, accent, false);
   }

   private void sidebarButton(GuiGraphicsExtractor g, int x, int y, String label, WindowGuiScreen.Kind kind, int mouseX, int mouseY, int accent, boolean active) {
      boolean over = inside(mouseX, mouseY, x + 6, y, SIDEBAR - 12, 17);
      roundRect(
         g, x + 6, y, x + SIDEBAR - 6, y + 17, 4, GeminiMark.withAlpha(active ? accent : (over ? SURFACE_3 : SURFACE), this.a(active ? 140 : 255))
      );
      String text = fit(this.font, label, SIDEBAR - 22);
      g.text(this.font, text, x + (SIDEBAR - this.font.width(text)) / 2, y + 5, GeminiMark.withAlpha(over || active ? TEXT : TEXT_DIM, this.a(255)), false);
      this.hit(kind, x + 6, y, SIDEBAR - 12, 17, null);
      if (over) {
         this.tip = switch (kind) {
            case MENU_KEY -> "Click, then press the key that opens this menu";
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
      roundRect(g, sx, sy, sx + sw, sy + 16, 4, GeminiMark.withAlpha(this.searchFocused ? SURFACE_3 : SURFACE_2, this.a(255)));
      if (this.searchFocused) {
         gradient(g, sx + 4, sy + 15, sx + sw - 4, sy + 16, accent, accent2, this.a(255));
      }

      magnifier(g, sx + 6, sy + 4, GeminiMark.withAlpha(this.searchFocused ? accent2 : TEXT_FAINT, this.a(255)));
      boolean blink = this.searchFocused && now / 450L % 2L == 0L;
      String text = this.search.isEmpty() && !this.searchFocused ? "Search all modules..." : this.search + (blink ? "_" : "");
      while (this.font.width(text) > sw - 34 && text.length() > 1) {
         text = text.substring(1);
      }

      g.text(this.font, text, sx + 18, sy + 4, GeminiMark.withAlpha(this.search.isEmpty() ? TEXT_FAINT : TEXT, this.a(255)), false);
      this.hit(WindowGuiScreen.Kind.SEARCH, sx, sy, sw, 16, null);
      if (!this.search.isEmpty()) {
         boolean overClear = inside(mouseX, mouseY, sx + sw - 14, sy, 14, 16);
         g.text(this.font, "x", sx + sw - 10, sy + 4, GeminiMark.withAlpha(overClear ? TEXT : TEXT_FAINT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.CLEAR, sx + sw - 14, sy, 14, 16, null);
      }

      if (overSearch) {
         this.tip = "Type anywhere to search  ·  Ctrl+F focuses  ·  Esc clears";
      }

      int fx = sx + sw + 6;
      for (String f : FILTERS) {
         int fw = this.font.width(f) + 12;
         boolean active = f.equals(this.filter);
         boolean over = inside(mouseX, mouseY, fx, sy, fw, 16);
         roundRect(g, fx, sy, fx + fw, sy + 16, 4, GeminiMark.withAlpha(active ? accent : (over ? SURFACE_3 : SURFACE_2), this.a(active ? 170 : 255)));
         g.text(this.font, f, fx + 6, sy + 4, GeminiMark.withAlpha(active || over ? TEXT : TEXT_DIM, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.FILTER, fx, sy, fw, 16, f);
         if (over) {
            this.tip = switch (f) {
               case "On" -> "Only show modules that are switched on";
               case "Bound" -> "Only show modules with a key bind";
               default -> "Show every module";
            };
         }

         fx += fw + 3;
      }
   }

   private void drawModuleList(GuiGraphicsExtractor g, int top, int bottom, int mouseX, int mouseY, int accent, int accent2, float delta) {
      int x = this.listX;
      int w = this.listW;
      String heading = this.search.isBlank() ? this.category.displayName() : "Results for \"" + this.search.trim() + "\"";
      g.text(this.font, fit(this.font, heading, w - 40), x + 2, top, GeminiMark.withAlpha(TEXT, this.a(255)), false);
      String count = this.shown.size() + (this.shown.size() == 1 ? " module" : " modules");
      g.text(this.font, count, x + w - this.font.width(count), top, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
      this.listViewTop = top + 17;
      this.listViewBottom = bottom - 4;
      int viewH = this.listViewBottom - this.listViewTop;
      int contentH = this.shown.size() * (CARD_H + CARD_GAP);
      float maxScroll = Math.max(0, contentH - viewH);
      this.listScrollTarget = Mth.clamp(this.listScrollTarget, 0.0F, maxScroll);
      this.listScroll = approach(this.listScroll, this.listScrollTarget, delta, 18.0F);
      if (this.shown.isEmpty()) {
         String empty = this.search.isBlank() ? "Nothing matches this filter." : "No module matches your search.";
         g.text(this.font, empty, x + (w - this.font.width(empty)) / 2, this.listViewTop + 20, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
         return;
      }

      g.enableScissor(x, this.listViewTop, x + w, this.listViewBottom);
      int y = this.listViewTop - Math.round(this.listScroll);
      boolean searchMode = !this.search.isBlank();
      for (Module module : this.shown) {
         if (y + CARD_H >= this.listViewTop && y <= this.listViewBottom) {
            boolean over = inside(mouseX, mouseY, x, y, w, CARD_H) && mouseY >= this.listViewTop && mouseY < this.listViewBottom;
            this.drawCard(g, module, x, y, w, over, module == this.selected, searchMode, accent, accent2, delta);
            if (over) {
               this.tip = module.canActivate() || module instanceof ClickGuiModule
                  ? module.getDescription()
                  : module.blockedReason().replaceAll("§.", "");
            }
         } else {
            this.hoverAnim.remove(module);
         }

         y += CARD_H + CARD_GAP;
      }

      g.disableScissor();
      this.scrollbar(g, x + w + 2, this.listViewTop, viewH, contentH, this.listScroll, accent);
   }

   private void drawCard(
      GuiGraphicsExtractor g, Module module, int x, int y, int w, boolean over, boolean selected, boolean searchMode, int accent, int accent2, float delta
   ) {
      float hover = this.anim(this.hoverAnim, module, over ? 1.0F : 0.0F, delta, 16.0F);
      float on = this.anim(this.enableAnim, module, module.isEnabled() ? 1.0F : 0.0F, delta, 12.0F);
      boolean blocked = !module.canActivate() && !(module instanceof ClickGuiModule);
      roundRect(g, x, y, x + w, y + CARD_H, 4, GeminiMark.withAlpha(selected ? SURFACE_3 : SURFACE_2, this.a(255)));
      if (on > 0.01F) {
         roundRect(g, x, y, x + w, y + CARD_H, 4, GeminiMark.withAlpha(accent, this.a(Math.round(34.0F * on))));
         g.fill(x, y + 5, x + 2, y + CARD_H - 5, GeminiMark.withAlpha(accent2, this.a(Math.round(255.0F * on))));
      }

      if (hover > 0.01F) {
         roundRect(g, x, y, x + w, y + CARD_H, 4, GeminiMark.withAlpha(-1, this.a(Math.round(9.0F * hover))));
      }

      if (selected) {
         outline(g, x, y, x + w, y + CARD_H, GeminiMark.withAlpha(accent, this.a(110)));
      }

      // The card is registered before its switch so a click on the switch toggles instead of selecting.
      this.hit(WindowGuiScreen.Kind.CARD, x, y, w, CARD_H, module, this.listViewTop, this.listViewBottom);
      boolean toggleable = !(module instanceof ClickGuiModule);
      int right = x + w - 8;
      if (toggleable) {
         int sx = right - 18;
         this.drawSwitch(g, sx, y + 10, on, accent, accent2, blocked);
         this.hit(WindowGuiScreen.Kind.SWITCH, sx - 4, y, 26, CARD_H, module, this.listViewTop, this.listViewBottom);
         right = sx - 6;
      }

      String key = this.binding == module ? "..." : (module.getKeybind() == -1 ? null : GeminiKeys.displayName(module.getKeybind()));
      if (key != null && !"NONE".equals(key)) {
         String label = module.isHoldBind() ? key + " hold" : key;
         int kw = this.font.width(label) + 6;
         roundRect(g, right - kw, y + 6, right, y + 17, 3, GeminiMark.withAlpha(SURFACE, this.a(255)));
         g.text(this.font, label, right - kw + 3, y + 8, GeminiMark.withAlpha(this.binding == module ? accent2 : TEXT_DIM, this.a(255)), false);
         right -= kw + 6;
      }

      int nameColor = blocked ? TEXT_FAINT : (module.isEnabled() ? TEXT : (over ? -2302756 : -3355444));
      int textX = x + 9 + Math.round(hover * 2.0F);
      g.text(this.font, fit(this.font, module.getName(), right - textX - 6), textX, y + 6, GeminiMark.withAlpha(nameColor, this.a(255)), false);
      String description = searchMode ? module.getCategory().displayName() + "  ·  " + module.getDescription() : module.getDescription();
      g.text(this.font, fit(this.font, description, x + w - 38 - textX), textX, y + 18, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
   }

   private void drawSettings(GuiGraphicsExtractor g, int top, int bottom, int mouseX, int mouseY, int accent, int accent2, float delta) {
      int x = this.settingsX;
      int w = this.settingsW;
      roundRect(g, x, top - 2, x + w, bottom - 4, 5, GeminiMark.withAlpha(SURFACE_2, this.a(255)));
      Module module = this.selected;
      if (module == null) {
         String text = "Select a module to see its settings";
         g.text(this.font, text, x + (w - this.font.width(text)) / 2, top + 30, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
         return;
      }

      g.text(this.font, fit(this.font, module.getName(), w - 50), x + 9, top + 6, GeminiMark.withAlpha(TEXT, this.a(255)), true);
      if (!(module instanceof ClickGuiModule)) {
         float on = this.anim(this.enableAnim, module, module.isEnabled() ? 1.0F : 0.0F, delta, 12.0F);
         int sx = x + w - 27;
         this.drawSwitch(g, sx, top + 6, on, accent, accent2, !module.canActivate());
         this.hit(WindowGuiScreen.Kind.SWITCH, sx - 3, top + 2, 24, 16, module);
      }

      List<String> lines = wrap(this.font, module.getDescription(), w - 18);
      int ty = top + 21;
      for (int i = 0; i < Math.min(3, lines.size()); i++) {
         g.text(this.font, lines.get(i), x + 9, ty, GeminiMark.withAlpha(TEXT_DIM, this.a(255)), false);
         ty += LINE;
      }

      this.settingsViewTop = ty + 6;
      this.settingsViewBottom = bottom - 8;
      g.fill(x + 8, this.settingsViewTop - 2, x + w - 8, this.settingsViewTop - 1, GeminiMark.withAlpha(-1, this.a(16)));
      List<Setting<?>> settings = new ArrayList<>();
      for (Setting<?> setting : module.settings()) {
         if (setting.visible()) {
            settings.add(setting);
         }
      }

      boolean bindable = module.isBindable();
      int contentH = (bindable ? 22 : 0) + 4;
      for (Setting<?> setting : settings) {
         contentH += rowHeight(setting);
      }

      int viewH = this.settingsViewBottom - this.settingsViewTop;
      float maxScroll = Math.max(0, contentH - viewH);
      this.settingsScrollTarget = Mth.clamp(this.settingsScrollTarget, 0.0F, maxScroll);
      this.settingsScroll = approach(this.settingsScroll, this.settingsScrollTarget, delta, 18.0F);
      g.enableScissor(x, this.settingsViewTop, x + w, this.settingsViewBottom);
      int ry = this.settingsViewTop + 4 - Math.round(this.settingsScroll);
      if (bindable) {
         this.drawBindRow(g, module, x + 4, ry, w - 8, mouseX, mouseY, accent2);
         ry += 22;
      }

      for (Setting<?> setting : settings) {
         int rh = rowHeight(setting);
         if (ry + rh >= this.settingsViewTop && ry <= this.settingsViewBottom) {
            boolean over = inside(mouseX, mouseY, x + 4, ry, w - 8, rh) && mouseY >= this.settingsViewTop && mouseY < this.settingsViewBottom;
            this.drawSetting(g, module, setting, x + 4, ry, w - 8, rh, over, accent, accent2, delta);
            if (over) {
               this.tip = setting.description();
            }
         }

         ry += rh;
      }

      g.disableScissor();
      if (settings.isEmpty() && !bindable) {
         String text = "This module has no settings";
         g.text(this.font, text, x + (w - this.font.width(text)) / 2, this.settingsViewTop + 12, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
      }

      this.scrollbar(g, x + w - 4, this.settingsViewTop, viewH, contentH, this.settingsScroll, accent);
   }

   private void drawBindRow(GuiGraphicsExtractor g, Module module, int x, int y, int w, int mouseX, int mouseY, int accent2) {
      boolean over = inside(mouseX, mouseY, x, y, w, 20) && mouseY >= this.settingsViewTop && mouseY < this.settingsViewBottom;
      if (over) {
         roundRect(g, x, y, x + w, y + 20, 3, GeminiMark.withAlpha(-1, this.a(10)));
         this.tip = "Click and press a key to bind  ·  right-click clears  ·  Bind Mode picks Toggle or Hold";
      }

      g.text(this.font, "Keybind", x + 8, y + 6, GeminiMark.withAlpha(TEXT_DIM, this.a(255)), false);
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
      roundRect(g, x + w - 8 - kw, y + 4, x + w - 8, y + 16, 3, GeminiMark.withAlpha(this.binding == module ? accent2 : SURFACE_3, this.a(this.binding == module ? 120 : 255)));
      g.text(this.font, key, x + w - 8 - kw + 5, y + 6, GeminiMark.withAlpha(TEXT, this.a(255)), false);
      this.hit(WindowGuiScreen.Kind.BIND, x, y, w, 20, module, this.settingsViewTop, this.settingsViewBottom);
   }

   private void drawSetting(
      GuiGraphicsExtractor g, Module module, Setting<?> setting, int x, int y, int w, int h, boolean over, int accent, int accent2, float delta
   ) {
      if (over) {
         roundRect(g, x, y, x + w, y + h, 3, GeminiMark.withAlpha(-1, this.a(8)));
      }

      int left = x + 8;
      int right = x + w - 8;
      int label = GeminiMark.withAlpha(!setting.isDefault() && !(setting instanceof ActionSetting) ? -1250068 : TEXT_DIM, this.a(255));
      if (setting instanceof BooleanSetting bool) {
         float on = this.settingAnim(setting, bool.get() ? 1.0F : 0.0F, delta);
         g.text(this.font, fit(this.font, setting.name(), right - left - 34), left, y + 6, label, false);
         this.drawSwitch(g, right - 18, y + 5, on, accent, accent2, false);
         this.hit(WindowGuiScreen.Kind.BOOL, x, y, w, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof NumberSetting number) {
         String value = number.display();
         int vw = this.font.width(value);
         g.text(this.font, fit(this.font, setting.name(), right - left - vw - 12), left, y + 5, label, false);
         g.text(this.font, value, right - vw, y + 5, GeminiMark.withAlpha(accent2, this.a(255)), false);
         float pct = this.settingAnim(setting, (float)number.percent(), delta);
         this.drawTrack(g, left, right, y + 20, pct, accent, accent2, false, this.dragging == setting || over);
         this.hit(WindowGuiScreen.Kind.NUMBER, left - 3, y + 14, right - left + 6, 14, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ColorSetting color) {
         g.text(this.font, fit(this.font, setting.name(), right - left - 28), left, y + 5, label, false);
         roundRect(g, right - 16, y + 4, right, y + 13, 2, GeminiMark.withAlpha(-16777216, this.a(255)));
         g.fill(right - 15, y + 5, right - 1, y + 12, GeminiMark.withAlpha(color.argb(), this.a(255)));
         float pct = this.settingAnim(setting, (float)color.percent(), delta);
         this.drawTrack(g, left, right, y + 20, pct, accent, accent2, true, this.dragging == setting || over);
         this.hit(WindowGuiScreen.Kind.COLOR, left - 3, y + 14, right - left + 6, 14, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ModeSetting mode) {
         String value = mode.get();
         int pillW = Math.min(right - left - 56, this.font.width(value) + 28);
         int pillL = right - pillW;
         g.text(this.font, fit(this.font, setting.name(), pillL - left - 10), left, y + 6, label, false);
         roundRect(g, pillL, y + 3, right, y + 16, 3, GeminiMark.withAlpha(accent, this.a(over ? 70 : 40)));
         g.text(this.font, "<", pillL + 5, y + 6, GeminiMark.withAlpha(over ? TEXT : TEXT_FAINT, this.a(255)), false);
         g.text(this.font, ">", right - 10, y + 6, GeminiMark.withAlpha(over ? TEXT : TEXT_FAINT, this.a(255)), false);
         String shownValue = fit(this.font, value, pillW - 22);
         g.text(this.font, shownValue, pillL + (pillW - this.font.width(shownValue)) / 2, y + 6, GeminiMark.withAlpha(TEXT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.MODE, pillL, y, pillW, h, setting, this.settingsViewTop, this.settingsViewBottom);
         this.hit(WindowGuiScreen.Kind.MODE, x, y, pillL - x, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ListSetting list) {
         String value = list.size() + (list.size() == 1 ? " entry  >" : " entries  >");
         int vw = this.font.width(value);
         g.text(this.font, fit(this.font, setting.name(), right - left - vw - 12), left, y + 6, label, false);
         g.text(this.font, value, right - vw, y + 6, GeminiMark.withAlpha(over ? TEXT : accent2, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.LIST, x, y, w, h, setting, this.settingsViewTop, this.settingsViewBottom);
      } else if (setting instanceof ActionSetting) {
         roundRect(g, left, y + 2, right, y + h - 2, 4, GeminiMark.withAlpha(accent, this.a(over ? 150 : 85)));
         String text = fit(this.font, setting.name(), right - left - 8);
         g.text(this.font, text, left + (right - left - this.font.width(text)) / 2, y + (h - 8) / 2, GeminiMark.withAlpha(TEXT, this.a(255)), false);
         this.hit(WindowGuiScreen.Kind.ACTION, left, y + 2, right - left, h - 4, setting, this.settingsViewTop, this.settingsViewBottom);
      }
   }

   private static int rowHeight(Setting<?> setting) {
      if (setting instanceof NumberSetting || setting instanceof ColorSetting) {
         return 30;
      } else {
         return setting instanceof ActionSetting ? 24 : 20;
      }
   }

   private void drawSwitch(GuiGraphicsExtractor g, int x, int y, float on, int accent, int accent2, boolean blocked) {
      int track = blocked ? -13421773 : HudOverlay.lerp(-13289414, HudOverlay.lerp(accent, accent2, 0.35F), on);
      roundRect(g, x, y, x + 18, y + 9, 4, GeminiMark.withAlpha(track, this.a(255)));
      int kx = x + 1 + Math.round(on * 9.0F);
      roundRect(g, kx, y + 1, kx + 7, y + 8, 3, GeminiMark.withAlpha(HudOverlay.lerp(-6908266, -1, on), this.a(255)));
   }

   private void drawTrack(GuiGraphicsExtractor g, int left, int right, int y, float pct, int accent, int accent2, boolean hue, boolean active) {
      int width = right - left;
      float clamped = Mth.clamp(pct, 0.0F, 1.0F);
      if (hue) {
         int segments = Math.max(6, width / 3);
         for (int i = 0; i < segments; i++) {
            int sx = left + width * i / segments;
            int ex = left + width * (i + 1) / segments;
            g.fill(sx, y, ex, y + 4, GeminiMark.withAlpha(ColorSetting.hueColor((float)i / segments, 0.72F, 1.0F), this.a(255)));
         }
      } else {
         roundRect(g, left, y, right, y + 4, 2, GeminiMark.withAlpha(-13289414, this.a(255)));
         int fill = Math.round(width * clamped);
         if (fill > 1) {
            gradient(g, left, y, left + fill, y + 4, accent, accent2, this.a(255));
         }
      }

      int knob = left + Math.round(width * clamped);
      if (active) {
         roundRect(g, knob - 5, y - 3, knob + 5, y + 7, 4, GeminiMark.withAlpha(accent2, this.a(70)));
      }

      roundRect(g, knob - 3, y - 2, knob + 3, y + 6, 2, GeminiMark.withAlpha(-1, this.a(255)));
   }

   private void scrollbar(GuiGraphicsExtractor g, int x, int top, int viewH, int contentH, float scroll, int accent) {
      if (contentH > viewH && viewH > 8) {
         int thumbH = Math.max(14, Math.round(viewH * ((float)viewH / contentH)));
         float max = Math.max(1, contentH - viewH);
         int thumbY = top + Math.round((viewH - thumbH) * (scroll / max));
         g.fill(x, top, x + 2, top + viewH, GeminiMark.withAlpha(-1, this.a(10)));
         g.fill(x, thumbY, x + 2, thumbY + thumbH, GeminiMark.withAlpha(accent, this.a(170)));
      }
   }

   private void drawFooter(GuiGraphicsExtractor g, int x, int y, int w, int accent2, long now) {
      boolean flashing = this.flash != null && now < this.flashUntil;
      String hint = "Esc close  ·  RMB toggle  ·  MMB bind";
      int hintW = this.font.width(hint);
      String text;
      if (this.bindingMenu) {
         text = "Press the key that should open this menu  ·  Esc cancels";
      } else if (this.binding != null) {
         text = "Binding " + this.binding.getName() + "  ·  press a key  ·  Esc cancels  ·  Delete clears";
      } else if (flashing) {
         text = this.flash;
      } else {
         text = this.tip == null ? "Click a module to edit it  ·  ↑↓ select  ·  Enter toggles" : this.tip;
      }

      text = text.replaceAll("§.", "");
      g.text(
         this.font, fit(this.font, text, w - hintW - 36), x + 8, y + 6,
         GeminiMark.withAlpha(flashing || this.binding != null || this.bindingMenu ? accent2 : TEXT_DIM, this.a(255)), false
      );
      g.text(this.font, hint, x + w - 8 - hintW, y + 6, GeminiMark.withAlpha(TEXT_FAINT, this.a(255)), false);
   }

   // ------------------------------------------------------------------ input

   @Override
   public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubled) {
      double mx = rawEvent.x() / this.uiScale;
      double my = rawEvent.y() / this.uiScale - this.slide;
      int button = rawEvent.button();
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

      if (hit == null) {
         this.searchFocused = false;
         return super.mouseClicked(rawEvent, doubled);
      }

      if (hit.kind != WindowGuiScreen.Kind.SEARCH && hit.kind != WindowGuiScreen.Kind.CLEAR) {
         this.searchFocused = false;
      }

      switch (hit.kind) {
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
            } else {
               this.select(module);
            }
         }
         case SWITCH -> this.toggle((Module)hit.target);
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
         case NUMBER, COLOR -> {
            if (button == 1) {
               ((Setting<?>)hit.target).reset();
               save();
            } else if (button == 0) {
               this.dragging = (Setting<?>)hit.target;
               this.dragLeft = hit.x + 3;
               this.dragRight = hit.x + hit.w - 3;
               this.updateDrag(mx);
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
         case MENU_KEY -> {
            this.bindingMenu = true;
            this.binding = null;
         }
      }

      return true;
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent rawEvent, double deltaX, double deltaY) {
      if (this.dragging != null) {
         this.updateDrag(rawEvent.x() / this.uiScale);
         return true;
      } else {
         return super.mouseDragged(rawEvent, deltaX, deltaY);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent rawEvent) {
      if (this.dragging != null) {
         this.dragging = null;
         save();
      }

      return super.mouseReleased(rawEvent);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      ClickGuiModule style = style();
      float speed = (style == null ? 2.0F : style.scrollSpeed()) * 12.0F;
      double mx = x / this.uiScale;
      double my = y / this.uiScale - this.slide;
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
      int key = event.key();
      if (this.bindingMenu) {
         if (key != 256) {
            GeminiKeys.OPEN_GUI.setKey(InputConstants.getKey(event));
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            save();
         }

         this.bindingMenu = false;
         return true;
      } else if (this.binding != null) {
         if (key != 256) {
            if (key == 261 || key == 259) {
               this.binding.setKeybind(-1);
               this.showNotice(this.binding.getName() + " unbound");
            } else {
               this.binding.setKeybind(key);
               String clash = KeybindReport.warning(Gemini.getInstance().modules(), this.binding, key);
               this.showNotice(clash != null ? clash : this.binding.getName() + " bound to " + GeminiKeys.displayName(key));
            }
         }

         this.binding = null;
         return true;
      } else if (key == 256) {
         if (!this.search.isEmpty()) {
            this.leaveSearch();
         } else {
            this.onClose();
         }

         return true;
      } else if (!this.searchFocused && GeminiKeys.OPEN_GUI.matches(event)) {
         this.onClose();
         return true;
      } else if (key == 70 && ctrlDown()) {
         this.searchFocused = true;
         return true;
      } else if (key == 259) {
         if (!this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            this.listScrollTarget = 0.0F;
         }

         return true;
      } else if (key == 264 || key == 265) {
         this.moveSelection(key == 264 ? 1 : -1);
         return true;
      } else if (key == 257 || key == 335) {
         if (this.selected != null) {
            this.toggle(this.selected);
         }

         return true;
      } else if (key == 258) {
         Category[] all = Category.values();
         int index = this.category.ordinal() + (shiftDown() ? all.length - 1 : 1);
         this.selectCategory(all[index % all.length]);
         return true;
      } else {
         return super.keyPressed(event);
      }
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      if (this.binding != null || this.bindingMenu) {
         return true;
      } else if (event.isAllowedChatCharacter() && this.search.length() < 32) {
         this.search = this.search + event.codepointAsString();
         this.searchFocused = true;
         this.listScrollTarget = 0.0F;
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   @Override
   public void removed() {
      lastCategory = this.category;
      lastModule = this.selected == null ? null : this.selected.getName();
      rememberedSearch = this.search;
      save();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ------------------------------------------------------------------ actions

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
      this.category = cat;
      this.search = "";
      this.searchFocused = false;
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
         this.showNotice(module.blockedReason());
      } else {
         module.toggle();
      }
   }

   private void beginBind(Module module) {
      if (module.isBindable()) {
         this.binding = module;
         this.bindingMenu = false;
         this.select(module);
      } else {
         this.showNotice(module.getName() + " can't have a key bind");
      }
   }

   private void updateDrag(double mouseX) {
      double pct = this.dragRight <= this.dragLeft ? 0.0 : (mouseX - this.dragLeft) / (this.dragRight - this.dragLeft);
      if (this.dragging instanceof NumberSetting number) {
         number.setFromPercent(pct);
      } else if (this.dragging instanceof ColorSetting color) {
         color.setFromPercent(pct);
      }
   }

   private void showNotice(String message) {
      if (message != null) {
         this.flash = message;
         this.flashUntil = Util.getMillis() + 2600L;
      }
   }

   private List<Module> visibleModules() {
      if (Gemini.getInstance() == null) {
         return List.of();
      }

      String query = this.search.trim().toLowerCase(Locale.ROOT);
      List<Module> list = new ArrayList<>();
      for (Module module : query.isEmpty() ? modules(this.category) : Gemini.getInstance().modules().getModules()) {
         if (!query.isEmpty()
            && !module.getName().toLowerCase(Locale.ROOT).contains(query)
            && !module.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
            continue;
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
         // Names that start with the query first, then names containing it, then description matches.
         list.sort(Comparator.<Module>comparingInt(m -> {
            String name = m.getName().toLowerCase(Locale.ROOT);
            return name.startsWith(query) ? 0 : (name.contains(query) ? 1 : 2);
         }).thenComparing(byName));
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

   private float anim(Map<Module, Float> map, Module key, float target, float delta, float speed) {
      float value = approach(map.getOrDefault(key, target), target, delta, speed);
      map.put(key, value);
      return value;
   }

   private float settingAnim(Setting<?> setting, float target, float delta) {
      float value = this.dragging == setting ? target : approach(this.settingAnim.getOrDefault(setting, target), target, delta, 16.0F);
      this.settingAnim.put(setting, value);
      return value;
   }

   private static float approach(float current, float target, float delta, float speed) {
      float next = current + (target - current) * Math.min(1.0F, delta * speed);
      return Math.abs(next - target) < 0.002F ? target : next;
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

   private static float ease(float t) {
      return 1.0F - (float)Math.pow(1.0F - t, 3.0);
   }

   private static boolean ctrlDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(window, 341) || InputConstants.isKeyDown(window, 345);
   }

   private static boolean shiftDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(window, 340) || InputConstants.isKeyDown(window, 344);
   }

   static String fit(Font font, String text, int maxWidth) {
      if (maxWidth <= 4) {
         return "";
      } else if (font.width(text) <= maxWidth) {
         return text;
      } else {
         String cut = text;
         while (cut.length() > 1 && font.width(cut + "..") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
         }

         return cut + "..";
      }
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

   /** Filled rectangle with rounded corners, built from non-overlapping fills so translucent colours stay even. */
   static void roundRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
      int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
      if (r <= 0) {
         g.fill(x1, y1, x2, y2, color);
         return;
      }

      g.fill(x1 + r, y1, x2 - r, y2, color);
      g.fill(x1, y1 + r, x1 + r, y2 - r, color);
      g.fill(x2 - r, y1 + r, x2, y2 - r, color);
      for (int i = 0; i < r; i++) {
         double dy = r - i - 0.5;
         int inset = (int)Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
         g.fill(x1 + inset, y1 + i, x1 + r, y1 + i + 1, color);
         g.fill(x2 - r, y1 + i, x2 - inset, y1 + i + 1, color);
         g.fill(x1 + inset, y2 - i - 1, x1 + r, y2 - i, color);
         g.fill(x2 - r, y2 - i - 1, x2 - inset, y2 - i, color);
      }
   }

   private static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
      g.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
      g.fill(x1 + 2, y2 - 1, x2 - 2, y2, color);
      g.fill(x1, y1 + 2, x1 + 1, y2 - 2, color);
      g.fill(x2 - 1, y1 + 2, x2, y2 - 2, color);
      g.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
      g.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
      g.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
      g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
   }

   private static void gradient(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int c1, int c2, int alpha) {
      int width = Math.max(1, right - left);
      int steps = Math.min(32, width);
      for (int i = 0; i < steps; i++) {
         int sx = left + width * i / steps;
         int ex = left + width * (i + 1) / steps;
         float t = steps <= 1 ? 0.0F : (float)i / (steps - 1);
         g.fill(sx, top, ex, bottom, GeminiMark.withAlpha(HudOverlay.lerp(c1, c2, t), alpha));
      }
   }

   private static void magnifier(GuiGraphicsExtractor g, int x, int y, int color) {
      g.fill(x + 1, y, x + 4, y + 1, color);
      g.fill(x, y + 1, x + 1, y + 4, color);
      g.fill(x + 4, y + 1, x + 5, y + 4, color);
      g.fill(x + 1, y + 4, x + 4, y + 5, color);
      g.fill(x + 4, y + 5, x + 5, y + 6, color);
      g.fill(x + 5, y + 6, x + 6, y + 7, color);
   }

   /** 7x7 pixel icons per category, drawn with fills so they take the theme colour. */
   private static void icon(GuiGraphicsExtractor g, Category category, int x, int y, int color) {
      String[] rows = switch (category) {
         case COMBAT -> new String[]{".....##", "....###", "...###.", "#.###..", ".###...", ".##....", "#.#...."};
         case MOVEMENT -> new String[]{"#..#...", ".#..#..", "..#..#.", "...#..#", "..#..#.", ".#..#..", "#..#..."};
         case PLAYER -> new String[]{"..###..", "..###..", "...#...", ".#####.", "...#...", "..#.#..", ".#...#."};
         case WORLD -> new String[]{"..###..", ".#.#.#.", "#..#..#", "#######", "#..#..#", ".#.#.#.", "..###.."};
         case RENDER -> new String[]{".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."};
         case HUD -> new String[]{"#######", "#.....#", "#######", "#..#..#", "#..#..#", "#..#..#", "#######"};
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
      CARD,
      SWITCH,
      FILTER,
      SEARCH,
      CLEAR,
      BIND,
      BOOL,
      NUMBER,
      COLOR,
      MODE,
      LIST,
      ACTION,
      LAYOUT,
      HUD_EDIT,
      MENU_KEY;
   }

   private record Hit(WindowGuiScreen.Kind kind, int x, int y, int w, int h, Object target) {
      boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }
}
