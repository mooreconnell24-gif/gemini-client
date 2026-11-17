package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import gemini.Gemini;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
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
import gemini.ui.Glass;
import gemini.ui.UiMath;
import gemini.ui.Theme;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.ListControl;
import gemini.ui.clickgui.controls.SettingControl;
import gemini.ui.clickgui.controls.Widgets;
import gemini.ui.font.FontSize;
import gemini.ui.font.FontWeight;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

/**
 * One draggable column of the panel menu: a rounded card with a header (the category's icon and name) and the
 * category's modules underneath. A row lights up in the accent while its module is on; a left click toggles the
 * module and a right click unfolds its settings in place, drawn with the same controls as the window menu in
 * their compact form.
 */
public final class CategoryPanel {
   public static final int DEFAULT_WIDTH = 114;
   public static final int HEADER = 24;
   static final int ROW = 18;
   private static final int MIN_WIDTH = 96;
   private static final int MAX_WIDTH = 280;
   private static final int MIN_VIEW = 54;
   private static final int SNAP_GRID = 8;
   /** Distance in pixels inside which a dragged panel jumps to another panel's edge. */
   private static final float MAGNET = 6.0F;
   /** Decay rate of the scroll glide; a wheel notch has settled after roughly a third of a second. */
   private static final float SCROLL_FRICTION = 11.0F;
   private static final int KIND_MODULE = 0;
   private static final int KIND_BIND = 1;
   private static final int KIND_SETTING = 2;
   private static final int BIND_ROW = 18;
   private static final int CARD_RADIUS = 6;
   public float animationProgress = 0.0F;
   private final Category category;
   private final ConfigManager.PanelState state;
   private final List<CategoryPanel.Row> rows = new ArrayList<>();
   private final List<CategoryPanel.Item> items = new ArrayList<>();
   private final IdentityHashMap<Setting<?>, SettingControl> controls = new IdentityHashMap<>();
   private List<CategoryPanel.Row> visible = List.of();
   private String matchedNeedle = "";
   private float scroll;
   private float scrollVelocity;
   private float collapseAnim;
   private float emphasis;
   private float alpha = 1.0F;
   private float dt;
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
   private float guideX = Float.NaN;
   private float guideY = Float.NaN;
   private SettingControl activeControl;
   private SettingControl clickedControl;
   private long lastGripClick;
   private String hoveredTitle;
   private String hoveredKeyBadge;
   private int hoveredAnchorX;
   private int hoveredAnchorY;
   private int hoveredAnchorW;
   private String filterMode = "All";
   private Module highlighted;
   private Module pendingReveal;

   public CategoryPanel(Category category, List<Module> modules, ConfigManager.PanelState state) {
      this.category = category;
      this.state = state;
      this.collapseAnim = state.collapsed ? 0.0F : 1.0F;
      this.animationProgress = this.collapseAnim;

      for (Module module : modules) {
         this.rows.add(new CategoryPanel.Row(module));
      }
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
      return this.state.width > 0 ? Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.state.width)) : DEFAULT_WIDTH;
   }

   /** Height of the whole card as drawn right now (header plus the visible part of the list). */
   public int outerHeight() {
      return HEADER + this.shownView();
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

   /** X of the vertical guide line the panel is snapped to while it is dragged, NaN when there is none. */
   public float guideX() {
      return this.drag == CategoryPanel.Drag.MOVE ? this.guideX : Float.NaN;
   }

   /** Y of the horizontal guide line the panel is snapped to while it is dragged, NaN when there is none. */
   public float guideY() {
      return this.drag == CategoryPanel.Drag.MOVE ? this.guideY : Float.NaN;
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
      this.scrollVelocity = 0.0F;
   }

   public boolean contains(double mx, double my) {
      int top = this.state.y + this.slide;
      return mx >= this.state.x - 4 && mx <= this.state.x + this.width() + 4 && my >= top && my <= top + this.outerHeight() + 4;
   }

   public void expandAll(boolean expanded) {
      for (CategoryPanel.Row row : this.rows) {
         row.expanded = expanded;
      }
   }

   private int shownView() {
      return Math.round(this.viewHeight * this.collapseAnim);
   }

   // ---- keyboard navigation --------------------------------------------------------------------------------

   /** The modules this panel lists for the search and the current filter, top to bottom (none when collapsed). */
   public List<Module> visibleModules(String search) {
      List<Module> modules = new ArrayList<>();
      if (!this.state.collapsed) {
         for (CategoryPanel.Row row : this.visibleRows(search)) {
            modules.add(row.module);
         }
      }

      return modules;
   }

   /** Fuzzy score of a listed module for the search, 0 when it is not listed. */
   public int score(String search, Module module) {
      for (CategoryPanel.Row row : this.visibleRows(search)) {
         if (row.module == module) {
            return row.match == null ? 1 : row.match.score();
         }
      }

      return 0;
   }

   /** Marks the row the keyboard is on (null for none) and scrolls it into view. */
   public void setHighlighted(Module module) {
      this.highlighted = module;
      this.pendingReveal = module;
   }

   /** Opens or closes a module's settings; returns false when the module is not in this panel. */
   public boolean setExpanded(Module module, boolean expanded) {
      for (CategoryPanel.Row row : this.rows) {
         if (row.module == module) {
            row.expanded = expanded;
            this.pendingReveal = module;
            return true;
         }
      }

      return false;
   }

   public boolean isExpanded(Module module) {
      for (CategoryPanel.Row row : this.rows) {
         if (row.module == module) {
            return row.expanded;
         }
      }

      return false;
   }

   // ---- setting controls -----------------------------------------------------------------------------------

   private SettingControl control(Module module, Setting<?> setting) {
      SettingControl control = this.controls.get(setting);
      if (control == null) {
         control = SettingControl.of(setting);
         if (control == null) {
            return null;
         }

         control.setCompact(true);
         control.setOnChange(CategoryPanel::save);
         if (control instanceof ListControl list) {
            list.setOpener(() -> {
               Minecraft client = Minecraft.getInstance();
               client.gui.setScreen(new ListEditorScreen(client.gui.screen(), module, (ListSetting)setting));
            });
         }

         this.controls.put(setting, control);
      }

      return control;
   }

   /** The control that is taking keyboard input (a number or hex being typed), or null. */
   public SettingControl focusedControl() {
      for (SettingControl control : this.controls.values()) {
         if (control.focused()) {
            return control;
         }
      }

      return null;
   }

   /** The control the last {@link #mouseClicked} landed on, or null when it hit something else. */
   public SettingControl clickedControl() {
      return this.clickedControl;
   }

   public void forgetClick() {
      this.clickedControl = null;
   }

   public boolean keyPressed(KeyEvent event) {
      SettingControl focused = this.focusedControl();
      return focused != null && focused.keyPressed(event);
   }

   public boolean charTyped(CharacterEvent event) {
      SettingControl focused = this.focusedControl();
      return focused != null && focused.charTyped(event);
   }

   // ---- per-frame state ------------------------------------------------------------------------------------

   public void tick(float delta, boolean animate) {
      this.dt = delta;
      this.collapseAnim = Glass.approach(this.collapseAnim, this.state.collapsed ? 0.0F : 1.0F, 16.0F, delta);
      this.animationProgress = this.collapseAnim;
      for (CategoryPanel.Row row : this.rows) {
         row.enableAnim = Glass.approach(row.enableAnim, row.module.isEnabled() ? 1.0F : 0.0F, 14.0F, delta);
         row.hoverAnim = Glass.approach(row.hoverAnim, row.hoveredFrame ? 1.0F : 0.0F, 20.0F, delta);
         row.expand = Glass.approach(row.expand, row.expanded ? 1.0F : 0.0F, 15.0F, delta);
         row.hoveredFrame = false;
      }

      float maxScroll = Math.max(0, this.contentHeight - this.viewHeight);
      if (this.scrollVelocity != 0.0F) {
         if (animate) {
            this.scroll = UiMath.glidePosition(this.scroll, this.scrollVelocity, SCROLL_FRICTION, delta);
            this.scrollVelocity = UiMath.glideVelocity(this.scrollVelocity, SCROLL_FRICTION, delta);
         } else {
            // without animations the whole glide distance is applied at once
            this.scroll += this.scrollVelocity / SCROLL_FRICTION;
            this.scrollVelocity = 0.0F;
         }
      }

      if (this.scroll <= 0.0F || this.scroll >= maxScroll) {
         this.scroll = Math.max(0.0F, Math.min(maxScroll, this.scroll));
         this.scrollVelocity = 0.0F;
      }
   }

   /** Starts (or adds to) a glide that carries the list {@code distance} pixels further down. */
   private void glide(float distance) {
      this.scrollVelocity += distance * SCROLL_FRICTION;
   }

   private void refreshMatches(String needle) {
      if (!needle.equals(this.matchedNeedle)) {
         this.matchedNeedle = needle;
         for (CategoryPanel.Row row : this.rows) {
            row.match = needle.isEmpty() ? null : Fuzzy.moduleMatch(needle, row.module);
         }
      }
   }

   private List<CategoryPanel.Row> visibleRows(String search) {
      String needle = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
      this.refreshMatches(needle);
      boolean activeOnly = "Active Only".equals(this.filterMode);
      boolean keyboundOnly = "Keybound".equals(this.filterMode);
      boolean favoritesOnly = "Favorites".equals(this.filterMode);
      if (needle.isEmpty() && !activeOnly && !keyboundOnly && !favoritesOnly) {
         return this.rows;
      }

      ClickGuiModule style = style();
      List<CategoryPanel.Row> list = new ArrayList<>();
      for (CategoryPanel.Row row : this.rows) {
         if ((!activeOnly || row.module.isEnabled())
            && (!keyboundOnly || row.module.getKeybind() != -1)
            && (!favoritesOnly || style != null && style.isFavorite(row.module))
            && (row.match == null || row.match.matched())) {
            list.add(row);
         }
      }

      return list;
   }

   private int settingHeight(Module module, Setting<?> setting) {
      SettingControl control = this.control(module, setting);
      return control == null ? 0 : control.height();
   }

   private void layout(String search, int screenH) {
      this.items.clear();
      this.visible = this.visibleRows(search);
      int cy = 2;

      for (CategoryPanel.Row row : this.visible) {
         this.items.add(new CategoryPanel.Item(KIND_MODULE, row, null, cy, ROW));
         cy += ROW;
         if (row.expand > 0.001F) {
            int blockTop = cy;
            int full = 0;
            List<CategoryPanel.Item> block = new ArrayList<>();
            if (row.module.isBindable()) {
               block.add(new CategoryPanel.Item(KIND_BIND, row, null, cy + full, BIND_ROW));
               full += BIND_ROW;
            }

            for (Setting<?> setting : row.module.settings()) {
               if (setting.visible()) {
                  int h = this.settingHeight(row.module, setting);
                  block.add(new CategoryPanel.Item(KIND_SETTING, row, setting, blockTop + full, h));
                  full += h;
               }
            }

            full += 3;
            int shown = Math.round(full * Glass.easeInOut(row.expand));
            row.blockTop = blockTop;
            row.blockShown = shown;
            for (CategoryPanel.Item item : block) {
               item.clipTop = blockTop;
               item.clipBottom = blockTop + shown;
               this.items.add(item);
            }

            cy += shown;
         }
      }

      this.contentHeight = cy + 2;
      int maxAuto = Math.max(MIN_VIEW, screenH - 40 - (this.state.y + HEADER));
      int auto = Math.max(HEADER, Math.min(this.contentHeight, maxAuto));
      if (this.state.height > HEADER) {
         this.viewHeight = Math.max(MIN_VIEW, Math.min(this.state.height - HEADER, Math.max(MIN_VIEW, screenH - 8)));
      } else {
         this.viewHeight = auto;
      }

      float maxScroll = Math.max(0, this.contentHeight - this.viewHeight);
      if (this.scroll > maxScroll) {
         this.scroll = maxScroll;
         this.scrollVelocity = 0.0F;
      }

      if (this.pendingReveal != null) {
         for (CategoryPanel.Item item : this.items) {
            if (item.kind == KIND_MODULE && item.row.module == this.pendingReveal) {
               float target = this.scroll;
               if (item.y - 2 < this.scroll) {
                  target = item.y - 2;
               } else if (item.y + ROW + 2 > this.scroll + this.viewHeight) {
                  target = item.y + ROW + 2 - this.viewHeight;
               }

               this.scrollVelocity = (Math.max(0.0F, Math.min(maxScroll, target)) - this.scroll) * SCROLL_FRICTION;
               break;
            }
         }

         this.pendingReveal = null;
      }
   }

   private void clampPosition() {
      int w = this.width();
      this.state.x = Math.max(-w + 48, Math.min(this.screenWidth - 48, this.state.x));
      this.state.y = Math.max(0, Math.min(this.screenHeight - HEADER - 4, this.state.y));
   }

   // ---- drawing --------------------------------------------------------------------------------------------

   private int faded(int color) {
      return Glass.fade(color, this.alpha);
   }

   private int faded(int color, int alpha) {
      return Glass.withAlpha(color, Math.round(alpha * this.alpha));
   }

   private void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
      Controls.text(graphics, font, text, x, y, color, this.alpha);
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
      this.alpha = Mth.clamp(look.open(), 0.0F, 1.0F);
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
      int bottom = top + HEADER + shown;
      int vpTop = top + HEADER;
      // "Category Tint" leans each column's accent towards its category's colour; otherwise every column matches
      ClickGuiModule tint = style();
      int accent = tint == null ? look.accent() : tint.accentFor(this.category);
      int accent2 = tint == null ? look.accent2() : tint.secondaryFor(this.category);
      int panelAccent = accent;
      boolean overHeader = hoverable && inside(mouseX, mouseY, x, top, w, HEADER);
      boolean overPanel = hoverable && inside(mouseX, mouseY, x, top, w, bottom - top);
      // A panel being dragged or resized lifts a little; with "Glass Glow" on, the hovered panel does too.
      float emphasisTarget = this.drag == CategoryPanel.Drag.MOVE || this.drag == CategoryPanel.Drag.RESIZE ? 1.0F : (look.glow() && overPanel ? (overHeader ? 0.6F : 0.3F) : 0.0F);
      this.emphasis = Glass.approach(this.emphasis, emphasisTarget, 12.0F, this.dt);
      float cardFade = this.alpha * look.opacity();
      Glass.shadow(graphics, x, top, x + w, bottom, CARD_RADIUS, 14, 4, (0.36F + 0.2F * this.emphasis) * this.alpha);
      Glass.roundRect(graphics, x, top, x + w, bottom, CARD_RADIUS, Glass.fade(Glass.surface(1), cardFade));
      Glass.roundOutline(graphics, x, top, x + w, bottom, CARD_RADIUS, this.faded(Glass.lerp(Glass.text(), panelAccent, this.emphasis), 18 + Math.round(60.0F * this.emphasis)));
      if (shown > 2) {
         graphics.fill(x + 8, top + HEADER - 1, x + w - 8, top + HEADER, this.faded(Widgets.divider()));
      }

      Icons.draw(graphics, Icons.named(this.category.name()), x + 8, top + (HEADER - 10) / 2, 10, this.faded(panelAccent));
      Controls.text(graphics, this.category.displayName(), x + 23, UiFont.centerY(top, HEADER), Glass.text(), this.alpha, FontSize.BODY, FontWeight.MEDIUM);
      int activeInCat = 0;
      for (CategoryPanel.Row r : this.visible) {
         if (r.module.isEnabled()) {
            activeInCat++;
         }
      }

      // Only the number switched on, and only when there is one: the total is not worth a label.
      int chevronX = x + w - 16;
      if (activeInCat > 0) {
         String count = Integer.toString(activeInCat);
         Controls.text(
            graphics, count, chevronX - 4 - UiFont.width(count, FontSize.SMALL, FontWeight.REGULAR), UiFont.centerY(top, HEADER, FontSize.SMALL), Glass.textFaint(), this.alpha,
            FontSize.SMALL, FontWeight.REGULAR
         );
      }

      Icons.draw(
         graphics, this.collapseAnim < 0.5F ? Icons.Icon.CHEVRON_RIGHT : Icons.Icon.CHEVRON_DOWN, chevronX, top + (HEADER - 8) / 2, 8,
         this.faded(overHeader ? Glass.textDim() : Glass.textFaint())
      );

      String hovered = null;
      if (overHeader) {
         hovered = "Drag to move (snaps to the grid and other panels, Alt = free)  ·  click or RMB collapse  ·  MMB reset size";
      }

      if (shown > 0) {
         int scrollPx = Math.round(this.scroll);
         graphics.enableScissor(x, vpTop, x + w, vpTop + shown);

         for (CategoryPanel.Item item : this.items) {
            int sy = vpTop + item.y - scrollPx;
            if (sy + item.h < vpTop || sy > vpTop + shown) {
               continue;
            }

            if (item.kind == KIND_MODULE) {
               boolean over = hoverable && inside(mouseX, mouseY, x, sy, w, item.h) && mouseY >= vpTop && mouseY < vpTop + shown && !this.onScrollbar(mouseX);
               if (over) {
                  item.row.hoveredFrame = true;
               }

               if (item.row.blockShown > 0 && item.row.expand > 0.001F) {
                  // the well the open settings sit in
                  int wellTop = vpTop + item.row.blockTop - scrollPx;
                  Glass.roundRect(graphics, x + 3, wellTop, x + w - 3, wellTop + item.row.blockShown - 1, 4, Glass.fade(Glass.withAlpha(Widgets.well(), 150), this.alpha));
               }

               String tip = this.drawModule(graphics, item.row, x, sy, w, over, accent, accent2, bindingModule);
               if (over) {
                  hovered = tip;
                  this.hoveredTitle = item.row.module.getName();
                  int key = item.row.module.getKeybind();
                  this.hoveredKeyBadge = key == -1 ? null : GeminiKeys.displayName(key);
                  this.anchor(x, sy, w);
               }
            } else {
               int clipTop = vpTop + item.clipTop - scrollPx;
               int clipBottom = vpTop + item.clipBottom - scrollPx;
               if (clipBottom <= clipTop) {
                  continue;
               }

               graphics.enableScissor(x, clipTop, x + w, clipBottom);
               boolean over = hoverable
                  && inside(mouseX, mouseY, x, sy, w, item.h)
                  && mouseY >= Math.max(clipTop, vpTop)
                  && mouseY < Math.min(clipBottom, vpTop + shown)
                  && !this.onScrollbar(mouseX);
               if (item.kind == KIND_BIND) {
                  this.drawBind(graphics, item, x + 3, sy, w - 6, over, accent, bindingModule);
                  if (over) {
                     hovered = "Click to bind  ·  right-click clears  ·  Bind Mode below picks Toggle or Hold";
                     this.hoveredTitle = item.row.module.getName() + " · Keybind";
                     this.anchor(x, sy, w);
                  }
               } else {
                  SettingControl control = this.control(item.row.module, item.setting);
                  if (control != null) {
                     control.setOpacity(this.alpha);
                     control.setAccent(accent, accent2);
                     control.render(graphics, x + 3, sy, w - 6, over ? mouseX : SettingControl.NO_MOUSE, over ? mouseY : SettingControl.NO_MOUSE, this.dt);
                  }

                  if (over) {
                     hovered = settingTip(item.setting, control);
                     this.hoveredTitle = item.row.module.getName() + " · " + item.setting.name();
                     this.anchor(x, sy, w);
                  }
               }

               graphics.disableScissor();
            }
         }

         graphics.disableScissor();
         if (this.contentHeight > this.viewHeight && shown >= this.viewHeight - 1) {
            this.drawScrollbar(graphics, x, vpTop, w, accent, mouseX, mouseY, hoverable);
         }
      }

      this.drawGrip(graphics, x + w, bottom, accent, hoverable && this.gripHit(mouseX, mouseY) != 0);
      if (hoverable) {
         this.applyCursor(graphics, mouseX, mouseY);
      }

      return hovered;
   }

   private void anchor(int x, int y, int w) {
      this.hoveredAnchorX = x;
      this.hoveredAnchorY = y;
      this.hoveredAnchorW = w;
   }

   private static String settingTip(Setting<?> setting, SettingControl control) {
      String hint = control == null ? null : control.hint();
      return (hint == null ? setting.description() : setting.description() + "  ·  " + hint) + "  ·  middle-click resets";
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

   private String drawModule(GuiGraphicsExtractor graphics, CategoryPanel.Row row, int x, int y, int w, boolean over, int accent, int accent2, Module bindingModule) {
      Module module = row.module;
      boolean blocked = !module.canActivate() && !(module instanceof ClickGuiModule);
      boolean binding = bindingModule == module;
      float on = module instanceof ClickGuiModule ? 0.0F : row.enableAnim;
      int rowL = x + 3;
      int rowR = x + w - 3;
      // A module that is on lights its row in the accent; hover adds a faint wash on top.
      if (on > 0.01F) {
         float strength = blocked ? 0.4F : 1.0F;
         Glass.roundGradient(
            graphics, rowL, y + 1, rowR, y + ROW - 1, 4, Glass.fade(Glass.withAlpha(accent, Math.round(96.0F * on * strength)), this.alpha),
            Glass.fade(Glass.withAlpha(accent2, Math.round(40.0F * on * strength)), this.alpha)
         );
         Glass.roundRect(graphics, rowL, y + 5, rowL + 2, y + ROW - 5, 1, Glass.fade(Glass.withAlpha(accent, Math.round(255.0F * on * strength)), this.alpha));
      }

      if (row.hoverAnim > 0.01F) {
         Glass.roundRect(graphics, rowL, y + 1, rowR, y + ROW - 1, 4, this.faded(Glass.text(), Math.round(14.0F * row.hoverAnim)));
      }

      if (module == this.highlighted || binding) {
         Glass.roundOutline(graphics, rowL, y + 1, rowR, y + ROW - 1, 4, this.faded(binding ? accent : accent2, 190));
      }

      // from the right: the open-settings arrow, then the key (or what the search matched), then the name
      int right = rowR - 6;
      if (row.expand > 0.01F || row.hoverAnim > 0.3F) {
         Icons.draw(
            graphics, row.expand > 0.5F ? Icons.Icon.CHEVRON_DOWN : Icons.Icon.CHEVRON_RIGHT, right - 7, y + (ROW - 8) / 2, 8,
            this.faded(row.expand > 0.5F ? Glass.text() : Glass.textFaint(), Math.round(255.0F * Math.max(row.expand, row.hoverAnim)))
         );
         right -= 10;
      }

      String hint = null;
      int hintColor = Glass.textFaint();
      if (binding) {
         hint = "press a key";
         hintColor = accent;
      } else if (row.match != null && row.match.setting() != null) {
         hint = row.match.setting();
         hintColor = Glass.textDim();
      } else if (module.getKeybind() != -1) {
         String key = GeminiKeys.displayName(module.getKeybind());
         if (!"NONE".equals(key)) {
            hint = module.isHoldBind() ? key + " hold" : key;
            hintColor = Glass.lerp(Glass.textFaint(), Glass.text(), on * 0.5F);
         }
      }

      int nameX = x + 11;
      ClickGuiModule style = style();
      if (style != null && style.isFavorite(module)) {
         Icons.draw(graphics, Icons.Icon.STAR, nameX - 1, y + (ROW - 8) / 2, 8, this.faded(Glass.lerp(accent2, Glass.text(), on * 0.6F)));
         nameX += 10;
      }

      FontWeight weight = on > 0.5F ? FontWeight.MEDIUM : FontWeight.REGULAR;
      int nameW = UiFont.width(module.getName(), FontSize.BODY, weight);
      if (hint != null) {
         int room = right - nameX - Math.min(nameW, 56) - 6;
         if (room >= 14) {
            String shownHint = UiFont.ellipsize(hint, room, FontSize.SMALL, FontWeight.REGULAR);
            int hintW = UiFont.width(shownHint, FontSize.SMALL, FontWeight.REGULAR);
            Controls.text(graphics, shownHint, right - hintW, UiFont.centerY(y, ROW, FontSize.SMALL), hintColor, this.alpha, FontSize.SMALL, FontWeight.REGULAR);
            right -= hintW + 5;
         }
      }

      int idle = blocked ? Glass.textFaint() : Glass.lerp(Glass.textDim(), Glass.text(), row.hoverAnim);
      int nameColor = Glass.lerp(idle, blocked ? Glass.textDim() : Glass.text(), on);
      Controls.text(
         graphics, UiFont.ellipsize(module.getName(), Math.max(20, right - nameX), FontSize.BODY, weight), nameX, UiFont.centerY(y, ROW), nameColor, this.alpha,
         FontSize.BODY, weight
      );
      String description = module.getDescription();
      if (row.match != null && row.match.setting() != null) {
         description = description + "  ·  matched setting: " + row.match.setting();
      }

      if (blocked && over) {
         String reason = module.blockedReason();
         return reason == null ? description : reason.replaceAll("§.", "");
      }

      return description;
   }

   private void drawBind(GuiGraphicsExtractor graphics, CategoryPanel.Item item, int x, int y, int w, boolean over, int accent, Module bindingModule) {
      Module module = item.row.module;
      boolean binding = bindingModule == module;
      Widgets.rowHover(graphics, x, y, w, item.h, over ? 1.0F : 0.0F, this.alpha);
      Controls.text(graphics, null, "Keybind", x + Widgets.PAD, UiFont.centerY(y, item.h), Widgets.label(module.getKeybind() == -1), this.alpha);
      String key = binding ? "Press a key" : (module.getKeybind() == -1 ? "None" : GeminiKeys.displayName(module.getKeybind()));
      if (module.isHoldBind() && !binding && module.getKeybind() != -1) {
         key = key + " (hold)";
      }

      Widgets.chip(
         graphics, key, x + w - Widgets.PAD, y + (item.h - 12) / 2, 12, binding ? accent : (over ? Widgets.raisedHover() : Widgets.raised()),
         binding ? Theme.onColor(accent) : (module.getKeybind() == -1 ? Glass.textDim() : Glass.text()), this.alpha
      );
   }

   private void drawScrollbar(GuiGraphicsExtractor graphics, int x, int vpTop, int w, int accent, int mouseX, int mouseY, boolean hoverable) {
      int trackX = x + w - 5;
      int[] thumb = this.thumbBounds(vpTop);
      boolean active = this.drag == CategoryPanel.Drag.SCROLLBAR
         || hoverable && mouseX >= trackX - 2 && mouseX <= trackX + 4 && mouseY >= thumb[0] && mouseY <= thumb[1];
      Glass.roundRect(graphics, trackX, thumb[0], trackX + 2, thumb[1], 1, this.faded(active ? accent : Glass.text(), active ? 220 : 56));
   }

   private int[] thumbBounds(int vpTop) {
      int trackH = this.viewHeight - 2;
      int thumbH = Math.max(14, Math.round(trackH * ((float)this.viewHeight / this.contentHeight)));
      float maxScroll = Math.max(1, this.contentHeight - this.viewHeight);
      int thumbY = vpTop + 1 + Math.round((trackH - thumbH) * (this.scroll / maxScroll));
      return new int[]{thumbY, thumbY + thumbH};
   }

   private void drawGrip(GuiGraphicsExtractor graphics, int right, int bottom, int accent, boolean active) {
      int color = this.faded(active ? accent : Glass.textFaint(), active ? 255 : 150);
      for (int i = 0; i < 3; i++) {
         int off = 3 + i * 3;
         graphics.fill(right - off - 1, bottom - 3, right - off, bottom - 2, color);
         graphics.fill(right - 3, bottom - off - 1, right - 2, bottom - off, color);
      }
   }

   private int gripHit(double mx, double my) {
      int x = this.state.x;
      int w = this.width();
      int top = this.state.y + this.slide;
      int bottom = top + this.outerHeight();
      boolean nearRight = mx >= x + w - 2 && mx <= x + w + 4 && my >= top && my <= bottom + 4;
      boolean nearBottom = !this.state.collapsed && my >= bottom - 2 && my <= bottom + 4 && mx >= x && mx <= x + w + 4;
      boolean corner = mx >= x + w - 9 && mx <= x + w + 4 && my >= bottom - 9 && my <= bottom + 4 && !this.state.collapsed;
      if (corner || nearRight && nearBottom) {
         return 3;
      } else if (nearRight) {
         return 1;
      } else {
         return nearBottom ? 2 : 0;
      }
   }

   // ---- input ----------------------------------------------------------------------------------------------

   public boolean mouseClicked(MouseButtonEvent event, boolean doubled, ClickGuiScreen screen) {
      this.clickedControl = null;
      double mx = event.x();
      double my = event.y();
      if (!this.contains(mx, my)) {
         return false;
      }

      int button = Keys.glfwButton(event.button());
      int w = this.width();
      int top = this.state.y + this.slide;
      int vpTop = top + HEADER;
      int shown = this.shownView();
      int grip = this.gripHit(mx, my);
      if (grip != 0 && button == 0) {
         long now = System.currentTimeMillis();
         if (now - this.lastGripClick < 350L) {
            this.state.width = 0;
            this.state.height = 0;
            save();
            return true;
         }

         this.lastGripClick = now;
         this.drag = CategoryPanel.Drag.RESIZE;
         this.resizeWidth = grip == 1 || grip == 3;
         this.resizeHeight = grip == 2 || grip == 3;
         this.pressX = mx;
         this.pressY = my;
         this.resizeStartWidth = w;
         this.resizeStartView = this.viewHeight;
         return true;
      } else if (my < vpTop) {
         if (button == 0) {
            this.drag = CategoryPanel.Drag.MOVE;
            this.moved = false;
            this.guideX = Float.NaN;
            this.guideY = Float.NaN;
            this.grabX = mx - this.state.x;
            this.grabY = my - this.state.y;
            this.pressX = mx;
            this.pressY = my;
         } else if (button == 1) {
            this.toggleCollapse();
         } else if (button == 2) {
            this.state.width = 0;
            this.state.height = 0;
            this.scroll = 0.0F;
            this.scrollVelocity = 0.0F;
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
      }

      int scrollPx = Math.round(this.scroll);
      for (CategoryPanel.Item item : this.items) {
         int sy = vpTop + item.y - scrollPx;
         if (my < sy || my >= sy + item.h) {
            continue;
         }

         if (item.kind != KIND_MODULE && (my < vpTop + item.clipTop - scrollPx || my >= vpTop + item.clipBottom - scrollPx)) {
            continue;
         }

         this.handleItemClick(item, mx, my, button, doubled, screen);
         break;
      }

      return true;
   }

   private void handleItemClick(CategoryPanel.Item item, double mx, double my, int button, boolean doubled, ClickGuiScreen screen) {
      Module module = item.row.module;
      if (item.kind == KIND_MODULE) {
         if (button == 0) {
            if (!module.isSavedEnabled() && !module.canActivate()) {
               screen.flash(module.blockedReason());
               return;
            }

            module.toggle();
         } else if (button == 1) {
            ClickGuiModule style = style();
            if (Controls.shiftDown() && style != null) {
               screen.flash(style.toggleFavorite(module) ? module.getName() + " added to favourites" : module.getName() + " removed from favourites");
            } else {
               item.row.expanded = !item.row.expanded;
            }
         } else if (button == 2 && module.isBindable()) {
            screen.beginModuleBind(module);
         }
      } else if (item.kind == KIND_BIND) {
         if (button == 0) {
            screen.beginModuleBind(module);
         } else {
            module.setKeybind(-1);
            save();
         }
      } else {
         Setting<?> setting = item.setting;
         SettingControl control = this.control(module, setting);
         if (control == null) {
            return;
         }

         if (button == 2) {
            // middle click puts any setting back to its default
            control.blur();
            setting.reset();
            save();
         } else {
            this.clickedControl = control;
            if (control.mouseClicked(mx, my, button, doubled)) {
               if (control.dragging()) {
                  this.drag = CategoryPanel.Drag.CONTROL;
                  this.activeControl = control;
               }
            } else if (button == 1) {
               setting.reset();
               save();
            }
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
      this.scroll = (float)(Math.max(0.0, Math.min(1.0, ratio)) * Math.max(0, this.contentHeight - this.viewHeight));
      this.scrollVelocity = 0.0F;
   }

   /**
    * @param panels every panel of the menu (this one included); a moved panel snaps to the others' edges
    */
   public boolean mouseDragged(MouseButtonEvent event, List<CategoryPanel> panels) {
      if (this.drag == CategoryPanel.Drag.NONE || Keys.glfwButton(event.button()) != 0) {
         return false;
      }

      double mx = event.x();
      double my = event.y();
      switch (this.drag) {
         case MOVE:
            if (Math.abs(mx - this.pressX) + Math.abs(my - this.pressY) > 3.0) {
               this.moved = true;
            }

            if (this.moved) {
               this.moveTo((float)(mx - this.grabX), (float)(my - this.grabY), panels);
            }
            break;
         case RESIZE:
            if (this.resizeWidth) {
               int targetW = (int)Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, this.resizeStartWidth + (mx - this.pressX)));
               if (this.snapEnabled && !Controls.altDown()) {
                  targetW = Math.round(targetW / (float)SNAP_GRID) * SNAP_GRID;
               }

               this.state.width = targetW;
            }

            if (this.resizeHeight) {
               int maxView = Math.max(MIN_VIEW, this.screenHeight - this.state.y - HEADER - 4);
               int view = (int)Math.max(MIN_VIEW, Math.min((double)maxView, this.resizeStartView + (my - this.pressY)));
               this.state.height = HEADER + view;
            }
            break;
         case SCROLLBAR:
            int vpTop = this.state.y + this.slide + HEADER;
            int[] thumb = this.thumbBounds(vpTop);
            int thumbH = thumb[1] - thumb[0];
            int trackH = this.viewHeight - 2;
            double ratio = (my - this.grabY - vpTop - 1.0) / Math.max(1, trackH - thumbH);
            this.scroll = (float)(Math.max(0.0, Math.min(1.0, ratio)) * Math.max(0, this.contentHeight - this.viewHeight));
            this.scrollVelocity = 0.0F;
            break;
         case CONTROL:
            if (this.activeControl != null) {
               this.activeControl.mouseDragged(mx, my, 0);
            }
            break;
         default:
      }

      return true;
   }

   /** Places the dragged panel, pulling it to the other panels' edges and tops, else to the 8px grid. */
   private void moveTo(float rawX, float rawY, List<CategoryPanel> panels) {
      this.guideX = Float.NaN;
      this.guideY = Float.NaN;
      if (this.snapEnabled && !Controls.altDown()) {
         int n = 0;
         float[] xs = new float[panels.size()];
         float[] ws = new float[panels.size()];
         float[] ys = new float[panels.size()];
         float[] hs = new float[panels.size()];
         for (CategoryPanel other : panels) {
            if (other != this) {
               xs[n] = other.state.x;
               ws[n] = other.width();
               ys[n] = other.state.y;
               hs[n] = other.outerHeight();
               n++;
            }
         }

         UiMath.AxisSnap snapX = UiMath.snapAxis(rawX, this.width(), this.screenWidth, 0.0F, false, xs, ws, n, MAGNET, SNAP_GRID, SNAP_GRID, false);
         UiMath.AxisSnap snapY = UiMath.snapAxis(rawY, this.outerHeight(), this.screenHeight, 0.0F, false, ys, hs, n, MAGNET, SNAP_GRID, SNAP_GRID, false);
         rawX = snapX.pos;
         rawY = snapY.pos;
         this.guideX = snapX.magnetic() ? snapX.line : Float.NaN;
         this.guideY = snapY.magnetic() ? snapY.line : Float.NaN;
      }

      this.state.x = Math.round(rawX);
      this.state.y = Math.round(rawY);
      this.state.placed = true;
      int beforeX = this.state.x;
      int beforeY = this.state.y;
      this.clampPosition();
      if (this.state.x != beforeX) {
         this.guideX = Float.NaN;
      }

      if (this.state.y != beforeY) {
         this.guideY = Float.NaN;
      }
   }

   public void mouseReleased(MouseButtonEvent event) {
      CategoryPanel.Drag finished = this.drag;
      this.drag = CategoryPanel.Drag.NONE;
      this.guideX = Float.NaN;
      this.guideY = Float.NaN;
      if (finished == CategoryPanel.Drag.MOVE) {
         if (!this.moved) {
            this.toggleCollapse();
         } else {
            save();
         }
      } else if (finished == CategoryPanel.Drag.RESIZE) {
         save();
      } else if (finished == CategoryPanel.Drag.CONTROL && this.activeControl != null) {
         this.activeControl.mouseReleased(event.x(), event.y(), Keys.glfwButton(event.button()));
      }

      this.activeControl = null;
   }

   public boolean isDragging() {
      return this.drag != CategoryPanel.Drag.NONE;
   }

   public boolean mouseScrolled(double mx, double my, double amount, float speed) {
      if (!this.contains(mx, my)) {
         return false;
      }

      int vpTop = this.state.y + this.slide + HEADER;
      if (Controls.shiftDown() && my >= vpTop && my < vpTop + this.shownView()) {
         int scrollPx = Math.round(this.scroll);
         for (CategoryPanel.Item item : this.items) {
            if (item.kind != KIND_SETTING) {
               continue;
            }

            int sy = vpTop + item.y - scrollPx;
            if (my >= sy && my < sy + item.h && my >= vpTop + item.clipTop - scrollPx && my < vpTop + item.clipBottom - scrollPx) {
               int steps = amount > 0.0 ? 1 : -1;
               Setting<?> setting = item.setting;
               boolean changed = true;
               if (setting instanceof NumberSetting number) {
                  number.nudge(steps);
               } else if (setting instanceof ColorSetting color) {
                  color.set(color.get() + steps * 6);
               } else if (setting instanceof ModeSetting mode) {
                  if (steps > 0) {
                     mode.cycleBack();
                  } else {
                     mode.cycle();
                  }
               } else if (setting instanceof BooleanSetting bool) {
                  bool.set(steps > 0);
               } else {
                  changed = false;
               }

               if (changed) {
                  save();
                  return true;
               }
            }
         }
      }

      this.glide((float)(-amount * speed * ROW));
      return true;
   }

   private static ClickGuiModule style() {
      return Gemini.getInstance() == null ? null : Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
   }

   private static void save() {
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().config().save();
      }
   }

   private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
      return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
   }

   private static enum Drag {
      NONE,
      MOVE,
      RESIZE,
      SCROLLBAR,
      CONTROL;
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

   /**
    * How the host screen wants the panels drawn this frame. {@code open} (0..1) fades the whole panel for the
    * menu's open and close animation; {@code animate} and {@code scrollSpeed} mirror the ClickGUI settings.
    */
   public record Look(
      int accent, int accent2, float opacity, boolean animate, float open, float scrollSpeed, boolean glow, boolean snapGrid, int body, int header
   ) {
      /** The default dark surface, for callers that do not theme the panel body. */
      public Look(int accent, int accent2, float opacity, boolean animate, float open, float scrollSpeed, boolean glow, boolean snapGrid) {
         this(accent, accent2, opacity, animate, open, scrollSpeed, glow, snapGrid, Glass.surface(1), Glass.surface(2));
      }
   }

   private static final class Row {
      private final Module module;
      private boolean expanded;
      private float expand;
      private float enableAnim;
      private float hoverAnim;
      private boolean hoveredFrame;
      private int blockTop;
      private int blockShown;
      private Fuzzy.Match match;

      private Row(Module module) {
         this.module = module;
      }
   }
}
