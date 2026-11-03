package gemini.ui.clickgui;

import gemini.input.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import gemini.Gemini;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
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
import gemini.ui.GeminiMark;
import gemini.ui.HudOverlay;
import gemini.ui.Fuzzy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

public final class CategoryPanel {
   public static final int DEFAULT_WIDTH = 128;
   public static final int HEADER = 22;
   static final int ROW = 18;
   private static final int MIN_WIDTH = 104;
   private static final int MAX_WIDTH = 280;
   private static final int MIN_VIEW = 54;
   private static final int SNAP_GRID = 8;
   private static final int KIND_MODULE = 0;
   private static final int KIND_BIND = 1;
   private static final int KIND_SETTING = 2;
   public float animationProgress = 0.0F;
   private final Category category;
   private final ConfigManager.PanelState state;
   private final List<CategoryPanel.Row> rows = new ArrayList<>();
   private final List<CategoryPanel.Item> items = new ArrayList<>();
   private final java.util.IdentityHashMap<Setting<?>, Float> settingAnims = new java.util.IdentityHashMap<>();
   private float scroll;
   private float scrollTarget;
   private float collapseAnim;
   private int contentHeight;
   private int viewHeight;
   private int slide;
   private int screenWidth = 400;
   private int screenHeight = 300;
   private boolean snapEnabled = true;
   private CategoryPanel.Drag drag = CategoryPanel.Drag.NONE;
   private double grabX;
   private double grabY;
   private double pressX;
   private double pressY;
   private boolean moved;
   private boolean resizeWidth;
   private boolean resizeHeight;
   private int resizeStartWidth;
   private int resizeStartView;
   private Setting<?> activeSlider;
   private int sliderX;
   private int sliderWidth;
   private long lastGripClick;
   private String hoveredTitle;
   private String hoveredKeyBadge;
   private int hoveredAnchorX;
   private int hoveredAnchorY;
   private int hoveredAnchorW;
   private String filterMode = "All";

   public CategoryPanel(Category category, List<Module> modules, ConfigManager.PanelState state) {
      this.category = category;
      this.state = state;
      this.collapseAnim = state.collapsed ? 0.0F : 1.0F;
      this.animationProgress = state.collapsed ? 0.0F : 1.0F;

      for (Module module : modules) {
         this.rows.add(new CategoryPanel.Row(module));
      }
   }

   public void updateAnimations() {
      boolean open = !this.state.collapsed;
      this.animationProgress = Mth.lerp(0.2F, this.animationProgress, open ? 1.0F : 0.0F);
   }

   public Category category() {
      return this.category;
   }

   public int x() {
      return this.state.x;
   }

   public int y() {
      return this.state.y;
   }

   public int width() {
      return this.state.width > 0 ? Math.max(104, Math.min(280, this.state.width)) : 128;
   }

   public boolean isCollapsed() {
      return this.state.collapsed;
   }

   public boolean isPlaced() {
      return this.state.placed;
   }

   public String hoveredTitle() {
      return this.hoveredTitle;
   }

   public String hoveredKeyBadge() {
      return this.hoveredKeyBadge;
   }

   public int hoveredAnchorX() {
      return this.hoveredAnchorX;
   }

   public int hoveredAnchorY() {
      return this.hoveredAnchorY;
   }

   public int hoveredAnchorW() {
      return this.hoveredAnchorW;
   }

   public void setDefaultPosition(int x, int y) {
      if (!this.state.placed) {
         this.state.x = x;
         this.state.y = y;
      }
   }

   public void setCollapsed(boolean collapsed) {
      this.state.collapsed = collapsed;
   }

   public void setFilterMode(String filterMode) {
      this.filterMode = filterMode == null ? "All" : filterMode;
   }

   public void resetLayout() {
      this.state.placed = false;
      this.state.width = 0;
      this.state.height = 0;
      this.state.collapsed = false;
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
   }

   public boolean contains(double mx, double my) {
      int top = this.state.y + this.slide;
      return mx >= this.state.x - 4 && mx <= this.state.x + this.width() + 4 && my >= top && my <= top + this.totalHeight() + 4;
   }

   public void expandAll(boolean expanded) {
      for (CategoryPanel.Row row : this.rows) {
         row.expanded = expanded;
      }
   }

   private int totalHeight() {
      return 22 + this.shownView();
   }

   private int shownView() {
      return Math.round(this.viewHeight * this.collapseAnim);
   }

   public void tick(float delta, boolean animate) {
      this.updateAnimations();
      float follow = 1.0F - (float)Math.exp(-delta * 16.0F);
      float fastFollow = 1.0F - (float)Math.exp(-delta * 20.0F);
      float collapseTarget = this.state.collapsed ? 0.0F : 1.0F;
      this.collapseAnim = animate ? Mth.lerp(follow, this.collapseAnim, collapseTarget) : collapseTarget;
      if (Math.abs(this.collapseAnim - collapseTarget) < 0.003F) {
         this.collapseAnim = collapseTarget;
      }

      for (CategoryPanel.Row row : this.rows) {
         float enabled = row.module.isEnabled() ? 1.0F : 0.0F;
         float expand = row.expanded ? 1.0F : 0.0F;
         float hoverTarget = row.hoveredFrame ? 1.0F : 0.0F;
         row.hoveredFrame = false;
         if (animate) {
            row.enableAnim = Mth.lerp(follow, row.enableAnim, enabled);
            row.hoverAnim = Mth.lerp(fastFollow, row.hoverAnim, hoverTarget);
            row.expand = Mth.lerp(follow, row.expand, expand);
            if (Math.abs(row.expand - expand) < 0.003F) {
               row.expand = expand;
            }
         } else {
            row.enableAnim = enabled;
            row.hoverAnim = hoverTarget;
            row.expand = expand;
         }
         if (row.expand > 0.01F) {
            for (Setting<?> setting : row.module.settings()) {
               if (setting instanceof BooleanSetting bool) {
                  float target = bool.get() ? 1.0F : 0.0F;
                  float cur = this.settingAnims.getOrDefault(setting, target);
                  this.settingAnims.put(setting, animate ? Mth.lerp(fastFollow, cur, target) : target);
               } else if (setting instanceof NumberSetting num) {
                  float target = (float)num.percent();
                  float cur = this.settingAnims.getOrDefault(setting, target);
                  this.settingAnims.put(setting, animate ? Mth.lerp(fastFollow, cur, target) : target);
               } else if (setting instanceof ColorSetting col) {
                  float target = (float)col.percent();
                  float cur = this.settingAnims.getOrDefault(setting, target);
                  this.settingAnims.put(setting, animate ? Mth.lerp(fastFollow, cur, target) : target);
               }
            }
         }
      }

      float maxScroll = Math.max(0, this.contentHeight - this.viewHeight);
      this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, this.scrollTarget));
      this.scroll = animate ? this.scroll + (this.scrollTarget - this.scroll) * Math.min(1.0F, delta * 18.0F) : this.scrollTarget;
      if (Math.abs(this.scroll - this.scrollTarget) < 0.3F) {
         this.scroll = this.scrollTarget;
      }

      this.scroll = Math.max(0.0F, Math.min(maxScroll, this.scroll));
   }

   private List<CategoryPanel.Row> visibleRows(String search) {
      String needle = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
      boolean activeOnly = "Active Only".equals(this.filterMode);
      boolean keyboundOnly = "Keybound".equals(this.filterMode);
      if (needle.isEmpty() && !activeOnly && !keyboundOnly) {
         return this.rows;
      } else {
         List<CategoryPanel.Row> list = new ArrayList<>();

         for (CategoryPanel.Row row : this.rows) {
            if ((!activeOnly || row.module.isEnabled())
               && (!keyboundOnly || row.module.getKeybind() != -1)
               && (needle.isEmpty() || Fuzzy.moduleScore(needle, row.module) > 0)) {
               list.add(row);
            }
         }

         return list;
      }
   }

   private static int settingHeight(Setting<?> setting) {
      if (!(setting instanceof NumberSetting) && !(setting instanceof ColorSetting)) {
         return setting instanceof ActionSetting ? 20 : 17;
      } else {
         return 25;
      }
   }

   private void layout(String search, int screenH) {
      this.items.clear();
      int cy = 2;

      for (CategoryPanel.Row row : this.visibleRows(search)) {
         this.items.add(new CategoryPanel.Item(0, row, null, cy, 18));
         cy += 18;
         if (!(row.expand <= 0.001F)) {
            int blockTop = cy;
            int full = 0;
            List<CategoryPanel.Item> block = new ArrayList<>();
            if (row.module.isBindable()) {
               block.add(new CategoryPanel.Item(1, row, null, cy + full, 17));
               full += 17;
            }

            for (Setting<?> setting : row.module.settings()) {
               if (setting.visible()) {
                  int h = settingHeight(setting);
                  block.add(new CategoryPanel.Item(2, row, setting, blockTop + full, h));
                  full += h;
               }
            }

            int shown = Math.round(full * Math.max(0.0F, Math.min(1.0F, row.expand)));

            for (CategoryPanel.Item item : block) {
               item.clipTop = blockTop;
               item.clipBottom = blockTop + shown;
               this.items.add(item);
            }

            cy += shown + (full > 0 ? 2 : 0) * (shown > 0 ? 1 : 0);
         }
      }

      this.contentHeight = cy + 2;
      int maxAuto = Math.max(54, screenH - 40 - (this.state.y + 22));
      int auto = Math.max(22, Math.min(this.contentHeight, maxAuto));
      if (this.state.height > 22) {
         this.viewHeight = Math.max(54, Math.min(this.state.height - 22, Math.max(54, screenH - 8)));
      } else {
         this.viewHeight = auto;
      }

      float maxScroll = Math.max(0, this.contentHeight - this.viewHeight);
      if (this.scrollTarget > maxScroll) {
         this.scrollTarget = maxScroll;
      }

      if (this.scroll > maxScroll) {
         this.scroll = maxScroll;
      }
   }

   private void clampPosition() {
      int w = this.width();
      this.state.x = Math.max(-w + 48, Math.min(this.screenWidth - 48, this.state.x));
      this.state.y = Math.max(0, Math.min(this.screenHeight - 22 - 4, this.state.y));
   }

   public String render(
      GuiGraphicsExtractor graphics,
      Font font,
      int mouseX,
      int mouseY,
      boolean hoverable,
      CategoryPanel.Look look,
      int slide,
      String search,
      int screenW,
      int screenH,
      Module bindingModule
   ) {
      this.slide = slide;
      this.screenWidth = screenW;
      this.screenHeight = screenH;
      this.snapEnabled = look.snapGrid();
      this.hoveredTitle = null;
      this.hoveredKeyBadge = null;
      if (this.drag != CategoryPanel.Drag.MOVE) {
         this.clampPosition();
      }

      this.layout(search, screenH);
      int x = this.state.x;
      int w = this.width();
      int top = this.state.y + slide;
      int shown = this.shownView();
      int bottom = top + 22 + shown;
      int vpTop = top + 22;
      int accent = look.accent();
      int accent2 = look.accent2();
      int panelAccent = HudOverlay.lerp(this.category.accent(), accent, 0.45F);
      int bodyAlpha = Math.round(255.0F * look.opacity() * look.open());
      if (look.glow()) {
         graphics.fill(x - 3, top - 3, x + w + 3, bottom + 3, GeminiMark.withAlpha(panelAccent, (int)(16.0F * look.open())));
         graphics.fill(x - 2, top - 2, x + w + 2, bottom + 2, GeminiMark.withAlpha(accent2, (int)(28.0F * look.open())));
      }

      graphics.fill(x - 1, top - 1, x + w + 1, bottom + 1, GeminiMark.withAlpha(panelAccent, (int)(75.0F * look.open())));
      graphics.fill(x, top, x + w, bottom, GeminiMark.withAlpha(-15987178, bodyAlpha));
      graphics.fill(x, top, x + w, top + 22, GeminiMark.withAlpha(-15329245, Math.min(255, bodyAlpha + 12)));
      drawGradientStrip(graphics, x, top, x + w, top + 2, panelAccent, accent2);
      graphics.fill(x, top + 21, x + w, top + 22, GeminiMark.withAlpha(panelAccent, (int)(65.0F * look.open())));
      graphics.text(font, this.category.displayName().toUpperCase(Locale.ROOT), x + 8, top + 8, -526345, false);
      List<CategoryPanel.Row> visible = this.visibleRows(search);
      int activeInCat = 0;

      for (CategoryPanel.Row r : visible) {
         if (r.module.isEnabled()) {
            activeInCat++;
         }
      }

      String count = activeInCat > 0 ? activeInCat + "/" + visible.size() : Integer.toString(visible.size());
      int chevronX = x + w - 14;
      graphics.text(font, count, chevronX - 6 - font.width(count), top + 8, activeInCat > 0 ? accent2 : -7628888, false);
      if (this.state.collapsed) {
         triRight(graphics, chevronX, top + 11, 4, accent2);
      } else {
         triDown(graphics, chevronX - 2, top + 9, 4, panelAccent);
      }

      String hovered = null;
      boolean overHeader = hoverable && inside(mouseX, mouseY, x, top, w, 22);
      if (overHeader) {
         graphics.fill(x, top + 2, x + w, top + 21, GeminiMark.withAlpha(panelAccent, 32));
         hovered = "Drag to move (snaps to 8px grid)  ·  RMB collapse  ·  MMB reset size";
      }

      if (shown > 0) {
         int scrollPx = Math.round(this.scroll);
         graphics.enableScissor(x, vpTop, x + w, vpTop + shown);

         for (CategoryPanel.Item item : this.items) {
            int sy = vpTop + item.y - scrollPx;
            if (sy + item.h >= vpTop && sy <= vpTop + shown) {
               if (item.kind == 0) {
                  boolean over = hoverable
                     && inside(mouseX, mouseY, x, sy, w, item.h)
                     && mouseY >= vpTop
                     && mouseY < vpTop + shown
                     && !this.onScrollbar(mouseX);
                  if (over) {
                     item.row.hoveredFrame = true;
                  }

                  String tip = this.drawModule(graphics, font, item, x, sy, w, over, accent, accent2, panelAccent, bindingModule);
                  if (over) {
                     hovered = tip;
                     this.hoveredTitle = item.row.module.getName();
                     int key = item.row.module.getKeybind();
                     this.hoveredKeyBadge = key == -1 ? null : GeminiKeys.displayName(key);
                     this.hoveredAnchorX = x;
                     this.hoveredAnchorY = sy;
                     this.hoveredAnchorW = w;
                  }
               } else {
                  int clipTop = vpTop + item.clipTop - scrollPx;
                  int clipBottom = vpTop + item.clipBottom - scrollPx;
                  if (clipBottom > clipTop) {
                     graphics.enableScissor(x, clipTop, x + w, clipBottom);
                     boolean overx = hoverable
                        && inside(mouseX, mouseY, x, sy, w, item.h)
                        && mouseY >= Math.max(clipTop, vpTop)
                        && mouseY < Math.min(clipBottom, vpTop + shown)
                        && !this.onScrollbar(mouseX);
                     if (item.kind == 1) {
                        this.drawBind(graphics, font, item, x, sy, w, overx, accent, bindingModule);
                        if (overx) {
                           hovered = "Click to bind  ·  right-click clears  ·  Bind Mode below picks Toggle or Hold";
                           this.hoveredTitle = item.row.module.getName() + " · Keybind";
                           this.hoveredAnchorX = x;
                           this.hoveredAnchorY = sy;
                           this.hoveredAnchorW = w;
                        }
                     } else {
                        this.drawSetting(graphics, font, item.setting, x, sy, w, item.h, overx, accent, accent2);
                        if (overx) {
                           hovered = item.setting.description();
                           this.hoveredTitle = item.row.module.getName() + " · " + item.setting.name();
                           this.hoveredAnchorX = x;
                           this.hoveredAnchorY = sy;
                           this.hoveredAnchorW = w;
                        }
                     }

                     graphics.disableScissor();
                  }
               }
            }
         }

         graphics.disableScissor();
         if (this.contentHeight > this.viewHeight && shown >= this.viewHeight - 1) {
            this.drawScrollbar(graphics, x, vpTop, w, accent, mouseX, mouseY, hoverable);
         }
      }

      drawGrip(graphics, x + w, bottom, accent, hoverable && this.gripHit(mouseX, mouseY) != 0);
      if (hoverable) {
         this.applyCursor(graphics, mouseX, mouseY);
      }

      return hovered;
   }

   private static void drawGradientStrip(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int colorA, int colorB) {
      int width = Math.max(1, right - left);
      int steps = Math.min(20, width);

      for (int i = 0; i < steps; i++) {
         int sx = left + width * i / steps;
         int ex = left + width * (i + 1) / steps;
         float t = steps <= 1 ? 0.0F : (float)i / (steps - 1);
         graphics.fill(sx, top, ex, bottom, HudOverlay.lerp(colorA, colorB, t));
      }
   }

   private void applyCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (this.drag != CategoryPanel.Drag.RESIZE) {
         int grip = this.gripHit(mouseX, mouseY);
         if (grip == 3) {
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
         } else if (grip == 1) {
            graphics.requestCursor(CursorTypes.RESIZE_EW);
         } else if (grip == 2) {
            graphics.requestCursor(CursorTypes.RESIZE_NS);
         }
      } else {
         graphics.requestCursor(
            this.resizeWidth && this.resizeHeight ? CursorTypes.RESIZE_ALL : (this.resizeWidth ? CursorTypes.RESIZE_EW : CursorTypes.RESIZE_NS)
         );
      }
   }

   private boolean onScrollbar(double mx) {
      return this.contentHeight > this.viewHeight && mx >= this.state.x + this.width() - 6 && mx <= this.state.x + this.width() - 2;
   }

   private String drawModule(
      GuiGraphicsExtractor graphics,
      Font font,
      CategoryPanel.Item item,
      int x,
      int y,
      int w,
      boolean over,
      int accent,
      int accent2,
      int panelAccent,
      Module bindingModule
   ) {
      CategoryPanel.Row row = item.row;
      Module module = row.module;
      boolean blocked = !module.canActivate() && !(module instanceof ClickGuiModule);
      if (row.hoverAnim > 0.01F) {
         int hoverW = Math.round(w * row.hoverAnim);
         graphics.fill(x, y, x + hoverW, y + 18, GeminiMark.withAlpha(accent2, (int)(30.0F * row.hoverAnim)));
      }

      if (row.enableAnim > 0.01F) {
         int activeColor = HudOverlay.lerp(panelAccent, accent2, 0.3F);
         graphics.fill(x, y, x + w, y + 18, GeminiMark.withAlpha(activeColor, (int)(22.0F + 68.0F * row.enableAnim)));
         graphics.fill(x, y, x + 2, y + 18, GeminiMark.withAlpha(accent2, (int)(255.0F * row.enableAnim)));
      } else {
         graphics.fill(x, y, x + w, y + 18, GeminiMark.withAlpha(panelAccent, 12));
      }

      int nameColor = module.isEnabled() ? -1 : (blocked ? -10063488 : (over ? -1511689 : -4010792));
      int right = x + w - 8;
      int chevronX = x + w - 14;
      if (row.expanded) {
         triDown(graphics, chevronX - 2, y + 7, 3, accent2);
      } else {
         triRight(graphics, chevronX, y + 9, 3, -9470834);
      }

      right = chevronX - 8;
      String key = bindingModule == module ? "..." : (module.getKeybind() == -1 ? null : GeminiKeys.displayName(module.getKeybind()));
      if (key != null && !"NONE".equals(key)) {
         String label = module.isHoldBind() ? key + "*" : key;
         graphics.text(font, label, right - font.width(label), y + 5, bindingModule == module ? accent : -7628888, false);
         right -= font.width(label) + 6;
      }

      int textOffset = Math.round(row.hoverAnim * 2.0F);
      graphics.text(font, fit(font, module.getName(), Math.max(20, right - (x + 8 + textOffset))), x + 8 + textOffset, y + 5, nameColor, false);
      if (blocked && over) {
         String reason = module.blockedReason();
         return reason == null ? module.getDescription() : reason.replaceAll("§.", "");
      } else {
         return module.getDescription();
      }
   }

   private void drawBind(GuiGraphicsExtractor graphics, Font font, CategoryPanel.Item item, int x, int y, int w, boolean over, int accent, Module bindingModule) {
      Module module = item.row.module;
      if (over) {
         graphics.fill(x + 4, y, x + w - 4, y + item.h, 486539263);
      }

      graphics.text(font, "Keybind", x + 10, y + 5, -4734770, false);
      String key = bindingModule == module ? "press a key..." : GeminiKeys.displayName(module.getKeybind());
      if (module.isHoldBind() && bindingModule != module && !"NONE".equals(key)) {
         key = key + " (hold)";
      }

      graphics.text(font, key, x + w - 10 - font.width(key), y + 5, bindingModule == module ? accent : -7364427, false);
   }

   private void drawSetting(GuiGraphicsExtractor graphics, Font font, Setting<?> setting, int x, int y, int w, int h, boolean over, int accent, int accent2) {
      if (over) {
         graphics.fill(x + 4, y, x + w - 4, y + h, 486539263);
      }

      int left = x + 10;
      int right = x + w - 10;
      int labelColor = !setting.isDefault() && !(setting instanceof ActionSetting) ? -1840396 : -4734770;
      if (setting instanceof BooleanSetting bool) {
         graphics.text(font, fit(font, setting.name(), right - left - 24), left, y + 5, labelColor, false);
         int tx = right - 18;
         float anim = this.settingAnims.getOrDefault(setting, bool.get() ? 1.0F : 0.0F);
         int trackColor = HudOverlay.lerp(-14142908, accent, anim);
         graphics.fill(tx - 1, y + 3, tx + 19, y + 13, GeminiMark.withAlpha(accent, Math.round(45.0F * anim)));
         graphics.fill(tx, y + 4, tx + 18, y + 12, GeminiMark.withAlpha(trackColor, 210));
         int kx = tx + 1 + Math.round(anim * 10.0F);
         graphics.fill(kx, y + 5, kx + 6, y + 11, HudOverlay.lerp(-8483940, -1, anim));
      } else if (setting instanceof NumberSetting number) {
         String value = formatNumber(number);
         int valueW = font.width(value);
         graphics.text(font, fit(font, setting.name(), right - left - valueW - 6), left, y + 3, labelColor, false);
         graphics.text(font, value, right - valueW, y + 3, accent2, false);
         double pct = this.settingAnims.getOrDefault(setting, (float)number.percent());
         drawTrack(graphics, left, right, y + 16, pct, accent, accent2, null, over || this.activeSlider == setting);
      } else if (setting instanceof ColorSetting color) {
         graphics.text(font, fit(font, setting.name(), right - left - 22), left, y + 3, labelColor, false);
         graphics.fill(right - 14, y + 3, right, y + 11, -16052458);
         graphics.fill(right - 13, y + 4, right - 1, y + 10, color.argb());
         double pct = this.settingAnims.getOrDefault(setting, (float)color.percent());
         drawTrack(graphics, left, right, y + 16, pct, accent, accent2, color, over || this.activeSlider == setting);
      } else if (setting instanceof ModeSetting mode) {
         String value = mode.get();
         int valueW = font.width(value);
         graphics.text(font, fit(font, setting.name(), right - left - valueW - 14), left, y + 5, labelColor, false);
         int pillL = right - valueW - 6;
         graphics.fill(pillL, y + 3, right, y + 14, GeminiMark.withAlpha(accent, over ? 55 : 28));
         graphics.text(font, value, right - valueW - 3, y + 5, over ? -1 : accent2, false);
      } else if (setting instanceof ListSetting list) {
         String value = list.size() + (list.size() == 1 ? " entry  >" : " entries  >");
         int valueW = font.width(value);
         graphics.text(font, fit(font, setting.name(), right - left - valueW - 8), left, y + 5, labelColor, false);
         graphics.text(font, value, right - valueW, y + 5, over ? -1 : accent2, false);
      } else if (setting instanceof ActionSetting) {
         graphics.fill(left - 2, y + 2, right + 2, y + h - 2, over ? GeminiMark.withAlpha(accent, 90) : -14932426);
         graphics.fill(left - 2, y + 2, left, y + h - 2, accent);
         String label = fit(font, setting.name(), right - left - 8);
         graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, -1511689, false);
      }
   }

   private static void drawTrack(GuiGraphicsExtractor graphics, int left, int right, int y, double percent, int accent, int accent2, ColorSetting hue, boolean active) {
      int width = right - left;
      double clampedPct = Mth.clamp(percent, 0.0, 1.0);
      if (hue != null) {
         int segments = Math.max(6, width / 3);

         for (int i = 0; i < segments; i++) {
            int sx = left + width * i / segments;
            int ex = left + width * (i + 1) / segments;
            graphics.fill(sx, y, ex, y + 3, ColorSetting.hueColor((float)i / segments, 0.72F, 1.0F));
         }
      } else {
         graphics.fill(left, y, right, y + 3, -14142908);
         int fillW = (int)Math.round(width * clampedPct);
         if (fillW > 0) {
            drawGradientStrip(graphics, left, y, left + fillW, y + 3, accent, accent2);
         }
      }

      int knob = left + (int)Math.round(width * clampedPct);
      if (active) {
         graphics.fill(knob - 3, y - 3, knob + 3, y + 6, GeminiMark.withAlpha(accent2, 95));
      }
      graphics.fill(knob - 2, y - 2, knob + 2, y + 5, -1);
   }

   private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int vpTop, int w, int accent, int mouseX, int mouseY, boolean hoverable) {
      int trackX = x + w - 6;
      graphics.fill(trackX, vpTop + 1, trackX + 3, vpTop + this.viewHeight - 1, 1073741824);
      int[] thumb = this.thumbBounds(vpTop);
      boolean active = this.drag == CategoryPanel.Drag.SCROLLBAR
         || hoverable && mouseX >= trackX - 1 && mouseX <= trackX + 4 && mouseY >= thumb[0] && mouseY <= thumb[1];
      graphics.fill(trackX, thumb[0], trackX + 3, thumb[1], GeminiMark.withAlpha(accent, active ? 235 : 150));
   }

   private int[] thumbBounds(int vpTop) {
      int trackH = this.viewHeight - 2;
      int thumbH = Math.max(14, Math.round(trackH * ((float)this.viewHeight / this.contentHeight)));
      float maxScroll = Math.max(1, this.contentHeight - this.viewHeight);
      int thumbY = vpTop + 1 + Math.round((trackH - thumbH) * (this.scroll / maxScroll));
      return new int[]{thumbY, thumbY + thumbH};
   }

   private static void drawGrip(GuiGraphicsExtractor graphics, int right, int bottom, int accent, boolean active) {
      int color = active ? accent : -10720642;

      for (int i = 0; i < 3; i++) {
         int off = 2 + i * 3;
         graphics.fill(right - off - 1, bottom - 2, right - off, bottom - 1, color);
         graphics.fill(right - 2, bottom - off - 1, right - 1, bottom - off, color);
      }

      graphics.fill(right - 5, bottom - 5, right - 4, bottom - 4, color);
   }

   private int gripHit(double mx, double my) {
      int x = this.state.x;
      int w = this.width();
      int top = this.state.y + this.slide;
      int bottom = top + this.totalHeight();
      boolean nearRight = mx >= x + w - 2 && mx <= x + w + 4 && my >= top && my <= bottom + 4;
      boolean nearBottom = !this.state.collapsed && my >= bottom - 2 && my <= bottom + 4 && mx >= x && mx <= x + w + 4;
      boolean corner = mx >= x + w - 9 && mx <= x + w + 4 && my >= bottom - 9 && my <= bottom + 4 && !this.state.collapsed;
      if (!corner && (!nearRight || !nearBottom)) {
         if (nearRight) {
            return 1;
         } else {
            return nearBottom ? 2 : 0;
         }
      } else {
         return 3;
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, String search, ClickGuiScreen screen) {
      double mx = event.x();
      double my = event.y();
      if (!this.contains(mx, my)) {
         return false;
      } else {
         int button = Keys.glfwButton(event.button());
         int w = this.width();
         int top = this.state.y + this.slide;
         int vpTop = top + 22;
         int shown = this.shownView();
         int grip = this.gripHit(mx, my);
         if (grip != 0 && button == 0) {
            long now = System.currentTimeMillis();
            if (now - this.lastGripClick < 350L) {
               this.state.width = 0;
               this.state.height = 0;
               save();
               return true;
            } else {
               this.lastGripClick = now;
               this.drag = CategoryPanel.Drag.RESIZE;
               this.resizeWidth = grip == 1 || grip == 3;
               this.resizeHeight = grip == 2 || grip == 3;
               this.pressX = mx;
               this.pressY = my;
               this.resizeStartWidth = w;
               this.resizeStartView = this.viewHeight;
               return true;
            }
         } else if (my < vpTop) {
            if (button == 0) {
               this.drag = CategoryPanel.Drag.MOVE;
               this.moved = false;
               this.grabX = mx - this.state.x;
               this.grabY = my - this.state.y;
               this.pressX = mx;
               this.pressY = my;
            } else if (button == 1) {
               this.toggleCollapse();
            } else if (button == 2) {
               this.state.width = 0;
               this.state.height = 0;
               this.scrollTarget = 0.0F;
               save();
            }

            return true;
         } else if (shown <= 0 || my >= vpTop + shown) {
            return true;
         } else if (this.onScrollbar(mx) && button == 0) {
            int[] thumb = this.thumbBounds(vpTop);
            this.drag = CategoryPanel.Drag.SCROLLBAR;
            if (my < thumb[0] || my > thumb[1]) {
               this.scrollToThumbCenter(my, vpTop);
            }

            this.grabY = my - this.thumbBounds(vpTop)[0];
            return true;
         } else {
            int scrollPx = Math.round(this.scroll);
            Iterator<CategoryPanel.Item> var16 = this.items.iterator();

            CategoryPanel.Item item;
            int sy;
            while (true) {
               if (!var16.hasNext()) {
                  return true;
               }

               item = var16.next();
               sy = vpTop + item.y - scrollPx;
               if (!(my < sy) && !(my >= sy + item.h)) {
                  if (item.kind == 0) {
                     break;
                  }

                  int clipTop = vpTop + item.clipTop - scrollPx;
                  int clipBottom = vpTop + item.clipBottom - scrollPx;
                  if (!(my < clipTop) && !(my >= clipBottom)) {
                     break;
                  }
               }
            }

            this.handleItemClick(item, event, sy, screen);
            return true;
         }
      }
   }

   private void handleItemClick(CategoryPanel.Item item, MouseButtonEvent event, int itemY, ClickGuiScreen screen) {
      int button = Keys.glfwButton(event.button());
      Module module = item.row.module;
      if (item.kind == 0) {
         if (button == 0) {
            if (!module.isSavedEnabled() && !module.canActivate()) {
               screen.flash(module.blockedReason());
               return;
            }

            module.toggle();
         } else if (button == 1) {
            item.row.expanded = !item.row.expanded;
         } else if (button == 2 && module.isBindable()) {
            screen.beginModuleBind(module);
         }
      } else if (item.kind == 1) {
         if (button == 0) {
            screen.beginModuleBind(module);
         } else {
            module.setKeybind(-1);
         }
      } else {
         Setting<?> setting = item.setting;
         boolean reset = button == 2 || button == 1 && !(setting instanceof ModeSetting) && !(setting instanceof ActionSetting);
         if (reset) {
            setting.reset();
            save();
         } else if (setting instanceof ListSetting list) {
            if (button == 0) {
               screen.openListEditor(module, list);
            }
         } else {
            if (setting instanceof BooleanSetting bool && button == 0) {
               bool.toggle();
            } else if (setting instanceof ModeSetting mode) {
               if (button == 0) {
                  mode.cycle();
               } else if (button == 1) {
                  mode.cycleBack();
               }
            } else if (!(setting instanceof NumberSetting) && !(setting instanceof ColorSetting)) {
               if (setting instanceof ActionSetting action && button == 0) {
                  action.run();
               }
            } else if (button == 0) {
               this.drag = CategoryPanel.Drag.SLIDER;
               this.activeSlider = setting;
               this.sliderX = this.state.x + 10;
               this.sliderWidth = this.width() - 20;
               this.updateSlider(event.x());
               return;
            }

            save();
         }
      }
   }

   private void toggleCollapse() {
      this.state.collapsed = !this.state.collapsed;
      save();
   }

   private void scrollToThumbCenter(double my, int vpTop) {
      int[] thumb = this.thumbBounds(vpTop);
      int thumbH = thumb[1] - thumb[0];
      int trackH = this.viewHeight - 2;
      double ratio = (my - vpTop - 1.0 - thumbH / 2.0) / Math.max(1, trackH - thumbH);
      this.scrollTarget = (float)(Math.max(0.0, Math.min(1.0, ratio)) * Math.max(0, this.contentHeight - this.viewHeight));
      this.scroll = this.scrollTarget;
   }

   public boolean mouseDragged(MouseButtonEvent event) {
      if (this.drag != CategoryPanel.Drag.NONE && Keys.glfwButton(event.button()) == 0) {
         double mx = event.x();
         double my = event.y();
         switch (this.drag) {
            case MOVE:
               if (Math.abs(mx - this.pressX) + Math.abs(my - this.pressY) > 3.0) {
                  this.moved = true;
               }

               if (this.moved) {
                  int rawX = (int)Math.round(mx - this.grabX);
                  int rawY = (int)Math.round(my - this.grabY);
                  if (this.snapEnabled && !isAltDown()) {
                     rawX = Math.round(rawX / 8.0F) * 8;
                     rawY = Math.round(rawY / 8.0F) * 8;
                  }

                  this.state.x = rawX;
                  this.state.y = rawY;
                  this.state.placed = true;
                  this.clampPosition();
               }
               break;
            case RESIZE:
               if (this.resizeWidth) {
                  int targetW = (int)Math.max(104.0, Math.min(280.0, this.resizeStartWidth + (mx - this.pressX)));
                  if (this.snapEnabled && !isAltDown()) {
                     targetW = Math.round(targetW / 8.0F) * 8;
                  }

                  this.state.width = targetW;
               }

               if (this.resizeHeight) {
                  int maxView = Math.max(54, this.screenHeight - this.state.y - 22 - 4);
                  int view = (int)Math.max(54.0, Math.min((double)maxView, this.resizeStartView + (my - this.pressY)));
                  this.state.height = 22 + view;
               }
               break;
            case SCROLLBAR:
               int vpTop = this.state.y + this.slide + 22;
               int thumbH = this.thumbBounds(vpTop)[1] - this.thumbBounds(vpTop)[0];
               int trackH = this.viewHeight - 2;
               double ratio = (my - this.grabY - vpTop - 1.0) / Math.max(1, trackH - thumbH);
               this.scrollTarget = (float)(Math.max(0.0, Math.min(1.0, ratio)) * Math.max(0, this.contentHeight - this.viewHeight));
               this.scroll = this.scrollTarget;
               break;
            case SLIDER:
               this.updateSlider(mx);
         }

         return true;
      } else {
         return false;
      }
   }

   public void mouseReleased(MouseButtonEvent event) {
      CategoryPanel.Drag finished = this.drag;
      this.drag = CategoryPanel.Drag.NONE;
      this.activeSlider = null;
      if (finished == CategoryPanel.Drag.MOVE) {
         if (!this.moved) {
            this.toggleCollapse();
         } else {
            save();
         }
      } else if (finished == CategoryPanel.Drag.RESIZE || finished == CategoryPanel.Drag.SLIDER) {
         save();
      }
   }

   public boolean isDragging() {
      return this.drag != CategoryPanel.Drag.NONE;
   }

   private void updateSlider(double mouseX) {
      double percent = (mouseX - this.sliderX) / Math.max(1, this.sliderWidth);
      if (this.activeSlider instanceof NumberSetting number) {
         number.setFromPercent(percent);
      } else if (this.activeSlider instanceof ColorSetting color) {
         color.setFromPercent(percent);
      }
   }

   public boolean mouseScrolled(double mx, double my, double amount, float speed) {
      if (!this.contains(mx, my)) {
         return false;
      } else {
         label79: {
            int vpTop = this.state.y + this.slide + 22;
            boolean shift = isShiftDown();
            if (shift && my >= vpTop && my < vpTop + this.shownView()) {
               int scrollPx = Math.round(this.scroll);

               for (CategoryPanel.Item item : this.items) {
                  if (item.kind == 2) {
                     int sy = vpTop + item.y - scrollPx;
                     if (my >= sy && my < sy + item.h && my >= vpTop + item.clipTop - scrollPx && my < vpTop + item.clipBottom - scrollPx) {
                        int steps = amount > 0.0 ? 1 : -1;
                        Setting<?> setting = item.setting;
                        if (setting instanceof NumberSetting number) {
                           number.nudge(steps);
                           break label79;
                        }

                        if (setting instanceof ColorSetting color) {
                           color.set(color.get() + steps * 6);
                           break label79;
                        }

                        if (setting instanceof ModeSetting mode) {
                           if (steps > 0) {
                              mode.cycleBack();
                           } else {
                              mode.cycle();
                           }
                           break label79;
                        }

                        if (setting instanceof BooleanSetting bool) {
                           bool.set(steps > 0);
                           break label79;
                        }
                     }
                  }
               }
            }

            this.scrollTarget -= (float)(amount * speed * 18.0);
            float maxScroll = Math.max(0, this.contentHeight - this.viewHeight);
            this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, this.scrollTarget));
            return true;
         }

         save();
         return true;
      }
   }

   private static boolean isShiftDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(Keys.LEFT_SHIFT) || InputConstants.isKeyDown(Keys.RIGHT_SHIFT);
   }

   private static boolean isAltDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(Keys.LEFT_ALT) || InputConstants.isKeyDown(Keys.RIGHT_ALT);
   }

   private static void save() {
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().config().save();
      }
   }

   private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
      return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
   }

   private static String fit(Font font, String text, int maxWidth) {
      if (font.width(text) <= maxWidth) {
         return text;
      } else {
         String cut = text;

         while (cut.length() > 1 && font.width(cut + "..") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
         }

         return cut + "..";
      }
   }

   private static String formatNumber(NumberSetting setting) {
      double value = setting.get();
      double step = setting.step();
      if (!(step >= 1.0) && (!(Math.abs(value - Math.rint(value)) < 1.0E-6) || !(step <= 0.0))) {
         return step >= 0.1 ? String.format(Locale.ROOT, "%.1f", value) : String.format(Locale.ROOT, "%.2f", value);
      } else {
         return Long.toString(Math.round(value));
      }
   }

   private static void triRight(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
      for (int i = 0; i < size; i++) {
         graphics.fill(cx + i, cy - (size - i - 1), cx + i + 1, cy + (size - i), color);
      }
   }

   private static void triDown(GuiGraphicsExtractor graphics, int cx, int cy, int size, int color) {
      for (int i = 0; i < size; i++) {
         graphics.fill(cx - (size - i - 1), cy + i, cx + (size - i), cy + i + 1, color);
      }
   }

   private static enum Drag {
      NONE,
      MOVE,
      RESIZE,
      SCROLLBAR,
      SLIDER;
   }

   private static final class Item {
      private final int kind;
      private final CategoryPanel.Row row;
      private final Setting<?> setting;
      private final int y;
      private final int h;
      private int clipTop;
      private int clipBottom;

      private Item(int kind, CategoryPanel.Row row, Setting<?> setting, int y, int h) {
         this.kind = kind;
         this.row = row;
         this.setting = setting;
         this.y = y;
         this.h = h;
      }
   }

   public record Look(int accent, int accent2, float opacity, boolean animate, float open, float scrollSpeed, boolean glow, boolean snapGrid) {
   }

   private static final class Row {
      private final Module module;
      private boolean expanded;
      private float expand;
      private float enableAnim;
      private float hoverAnim;
      private boolean hoveredFrame;

      private Row(Module module) {
         this.module = module;
      }
   }
}
