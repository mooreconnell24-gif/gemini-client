package gemini.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import gemini.Gemini;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
import gemini.input.KeybindReport;
import gemini.input.Keys;
import gemini.module.Category;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.render.HudModule;
import gemini.module.setting.ListSetting;
import gemini.ui.GeminiMark;
import gemini.ui.Glass;
import gemini.ui.UiMath;
import gemini.ui.clickgui.controls.Controls;
import gemini.ui.clickgui.controls.SettingControl;
import gemini.ui.font.UiFont;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The panel menu: one draggable glass column per category, a fuzzy search that filters every column at once and
 * can be driven from the keyboard, and the HUD editor (drag, snap and nudge the HUD widgets).
 */
public final class ClickGuiScreen extends Screen {
   private static final int GAP = 8;
   private static final int TOP_BAR = 54;
   private static final int FOOTER = 30;
   private static final String DEFAULT_TIP = "LMB toggle   RMB settings   MMB bind   Shift+RMB favourite   Shift+drag HUD widgets";
   /** The chips always shown: the filters and the two other screens. */
   private static final String[] CHIPS = {"All", "Active Only", "Keybound", "Favorites", "Edit HUD", "Window layout"};
   /** Occasional actions, behind the "More" chip so the bar stays short. */
   private static final String[] MORE_CHIPS = {"Collapse all", "Expand all", "Reset layout", "Save Preset", "Load Preset"};
   private static final String MORE = "More";
   private static final String LESS = "Less";
   private static boolean moreChips;
   private static String rememberedSearch = "";
   /** 0 while the menu is closed, 1 once it is fully open; it eases both ways. */
   public float animationProgress = 0.0F;
   private final List<CategoryPanel> panels = new ArrayList<>();
   private final List<ClickGuiScreen.Particle> particles = new ArrayList<>();
   private final List<ClickGuiScreen.Chip> chips = new ArrayList<>();
   private long lastFrame = Util.getMillis();
   private boolean closing;
   private String search = "";
   private boolean searchFocused;
   private String filterMode = "All";
   private Module highlighted;
   private Module bindingModule;
   private boolean bindingMenu;
   private boolean hudEditMode;
   private HudWidget draggingWidget;
   private HudWidget selectedWidget;
   private long nudgedAt;
   private final HudWidget.Guides guides = new HudWidget.Guides();
   private float widgetGrabX;
   private float widgetGrabY;
   private String hoveredDescription = DEFAULT_TIP;
   private String tooltipTitle;
   private String tooltipKeyBadge;
   private String tooltipBody;
   private int tooltipAnchorX;
   private int tooltipAnchorY;
   private int tooltipAnchorW;
   private float tooltipProgress;
   private String flashText;
   private long flashUntil;
   private float uiScale = 1.0F;
   private float fade = 1.0F;
   private int vw;
   private int vh;

   /** Opens the classic menu straight into HUD edit mode (used by the window layout's Edit HUD button). */
   public static ClickGuiScreen hudEditor() {
      ClickGuiScreen screen = new ClickGuiScreen();
      screen.hudEditMode = true;
      return screen;
   }

   public ClickGuiScreen() {
      super(Component.literal("Gemini Client"));
      ClickGuiModule style = this.style();
      if (style != null && style.keepSearch()) {
         this.search = rememberedSearch;
      }
   }

   protected void init() {
      // Minecraft 26.3 reads keys through SDL, which only sends typed characters to a screen that asks for them.
      this.minecraft.textInputManager().startTextInput(this);
      ClickGuiModule style = this.style();
      int count = Category.values().length;
      int column = CategoryPanel.DEFAULT_WIDTH + GAP;
      int needed = count * CategoryPanel.DEFAULT_WIDTH + (count + 1) * GAP;
      float base = style == null ? 1.0F : style.scale();
      float scale = base;
      if (style == null || style.autoFit()) {
         scale = Math.min(base, Math.min((float)this.width / needed, this.height / 300.0F));
      }

      this.uiScale = Mth.clamp(scale, 0.4F, 2.0F);
      this.vw = Math.round(this.width / this.uiScale);
      this.vh = Math.round(this.height / this.uiScale);
      this.particles.clear();
      ConfigManager config = Gemini.getInstance().config();
      int totalWidth = count * CategoryPanel.DEFAULT_WIDTH + (count - 1) * GAP;
      int startX = Math.max(GAP, (this.vw - totalWidth) / 2);
      if (this.panels.size() == count) {
         for (CategoryPanel panel : this.panels) {
            panel.setDefaultPosition(startX + panel.category().ordinal() * column, TOP_BAR + GAP);
         }
      } else {
         this.panels.clear();

         for (Category category : Category.values()) {
            CategoryPanel panel = new CategoryPanel(category, Gemini.getInstance().modules().of(category), config.panel(category.name()));
            panel.setDefaultPosition(startX + category.ordinal() * column, TOP_BAR + GAP);
            this.panels.add(panel);
         }
      }

      for (int i = 0; i < 42; i++) {
         this.particles.add(ClickGuiScreen.Particle.random(this.vw, this.vh));
      }

      this.buildChips();
      if (!this.search.isEmpty() && this.highlighted == null) {
         this.searchChanged();
      }
   }

   private void buildChips() {
      this.chips.clear();
      int x = 56;
      int y = 35;

      // The row has to end before the search box on the right.
      int limit = this.vw - 16 - this.searchWidth() - 8;
      for (String label : CHIPS) {
         x = this.addChip(label, x, y);
      }

      x = this.addChip(moreChips ? LESS : MORE, x + 6, y);
      if (moreChips) {
         for (String label : MORE_CHIPS) {
            if (x + UiFont.width(label) + 12 > limit) {
               break;
            }

            x = this.addChip(label, x, y);
         }
      }
   }

   private int addChip(String label, int x, int y) {
      int w = UiFont.width(label) + 12;
      this.chips.add(new ClickGuiScreen.Chip(label, x, y, w, 13));
      return x + w + 4;
   }

   private MouseButtonEvent scaled(MouseButtonEvent event) {
      return new MouseButtonEvent(event.x() / this.uiScale, event.y() / this.uiScale, event.buttonInfo());
   }

   /** Vanilla blurs the world behind every menu; the ClickGUI "Blur" switch turns that off for this one. */
   @Override
   protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
      ClickGuiModule style = this.style();
      if (style == null || style.blur()) {
         super.extractBlurredBackground(graphics);
      }
   }

   private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
      Controls.text(graphics, this.font, text, x, y, color, this.fade);
   }

   private int faded(int color, int alpha) {
      return Glass.withAlpha(color, Math.round(alpha * this.fade));
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int rawMouseX, int rawMouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      ClickGuiModule style = this.style();
      boolean animate = style == null || style.animations();
      // Glass.approach jumps straight to the target when animations are off
      this.animationProgress = Glass.approach(this.animationProgress, this.closing ? 0.0F : 1.0F, this.closing ? 22.0F : 13.0F, delta);
      float open = this.animationProgress;
      this.fade = open;
      if (this.closing && open <= 0.02F) {
         // fully faded out; tick() closes the screen
         super.extractRenderState(graphics, rawMouseX, rawMouseY, partialTick);
         return;
      }

      int accent = style == null ? -6467875 : style.accentColor();
      int accentB = style == null ? -16718337 : style.secondaryColor();
      boolean glow = style == null || style.glow();
      boolean snapGrid = style == null || style.snapGrid();
      float pulse = animate ? 0.55F + 0.45F * (float)Math.sin(now / 380.0) : 1.0F;
      int surface = style == null ? -15987178 : style.surface(0);
      int header = style == null ? -15329501 : Glass.lerp(style.surface(1), style.surface(2), 0.5F);
      boolean dim = style == null || style.dim();
      if (dim) {
         graphics.fill(0, 0, this.width, this.height, this.faded(0x050810, 175));
      }

      int mouseX = Math.round(rawMouseX / this.uiScale);
      int mouseY = Math.round(rawMouseY / this.uiScale);
      // the whole menu grows from 94% to full size around the screen centre while it fades in
      float zoom = 0.94F + 0.06F * open;
      graphics.pose().pushMatrix();
      graphics.pose().scale(this.uiScale, this.uiScale);
      graphics.pose().translate(this.vw / 2.0F * (1.0F - zoom), this.vh / 2.0F * (1.0F - zoom));
      graphics.pose().scale(zoom, zoom);
      if (dim) {
         graphics.fill(0, 0, this.vw, TOP_BAR, this.faded(surface, 205));
         graphics.fill(0, TOP_BAR - 1, this.vw, TOP_BAR, this.faded(0xFFFFFF, 18));
      }

      if (style == null || style.particles()) {
         for (ClickGuiScreen.Particle particle : this.particles) {
            particle.tick(delta, this.vw, this.vh);
            int px = (int)particle.x;
            int py = (int)particle.y;
            int color = (particle.size + px) % 2 == 0 ? accent : accentB;
            graphics.fill(px, py, px + particle.size, py + particle.size, this.faded(color, particle.alpha));
         }
      }

      if (open > 0.5F) {
         GeminiMark.draw(graphics, 14, 13, 20, accent, pulse);
      }

      this.text(graphics, "Gemini", 40, 19, Glass.text());
      if (this.hudEditMode) {
         // Only the HUD editor needs instructions up here; the module menu explains itself.
         this.text(graphics, "Drag to move  ·  arrow keys nudge 1px (Shift: 10px)", 40, 30, Glass.textDim());
      }
      this.drawSearch(graphics, mouseX, mouseY, accent, accentB, now, delta);
      this.drawChips(graphics, mouseX, mouseY, accent, accentB);

      this.hoveredDescription = DEFAULT_TIP;
      String frameTooltipTitle = null;
      String frameTooltipKey = null;
      String frameTooltipBody = null;
      int frameAnchorX = 0;
      int frameAnchorY = 0;
      int frameAnchorW = 0;
      if (this.hudEditMode || Controls.shiftDown() || this.selectedWidget != null) {
         this.renderHudEditOverlay(graphics, rawMouseX, rawMouseY, accent, accentB, now);
      }

      if (!this.hudEditMode) {
         int slide = Math.round((1.0F - open) * 14.0F);
         float opacity = style == null ? 0.88F : style.opacity();
         float scrollSpeed = style == null ? 2.0F : style.scrollSpeed();
         CategoryPanel.Look look = new CategoryPanel.Look(accent, accentB, opacity, animate, open, scrollSpeed, glow, snapGrid, surface, header);
         CategoryPanel top = null;

         for (CategoryPanel panel : this.panels) {
            if (panel.isDragging()) {
               top = panel;
               break;
            }
         }

         if (top == null && !this.closing) {
            for (int i = this.panels.size() - 1; i >= 0; i--) {
               if (this.panels.get(i).contains(mouseX, mouseY)) {
                  top = this.panels.get(i);
                  break;
               }
            }
         }

         for (CategoryPanel panel : this.panels) {
            panel.setFilterMode(this.filterMode);
            panel.tick(delta, animate);
            String description = panel.render(graphics, this.font, mouseX, mouseY, panel == top, look, slide, this.search, this.vw, this.vh, this.bindingModule);
            if (description != null && panel == top) {
               this.hoveredDescription = description;
               if (panel.hoveredTitle() != null) {
                  frameTooltipTitle = panel.hoveredTitle();
                  frameTooltipKey = panel.hoveredKeyBadge();
                  frameTooltipBody = description;
                  frameAnchorX = panel.hoveredAnchorX();
                  frameAnchorY = panel.hoveredAnchorY();
                  frameAnchorW = panel.hoveredAnchorW();
               }
            }
         }

         // the lines a dragged panel is snapped to
         for (CategoryPanel panel : this.panels) {
            if (!Float.isNaN(panel.guideX())) {
               int gx = Math.round(panel.guideX());
               graphics.fill(gx, TOP_BAR, gx + 1, this.vh - FOOTER, this.faded(accentB, 200));
            }

            if (!Float.isNaN(panel.guideY())) {
               int gy = Math.round(panel.guideY());
               graphics.fill(0, gy, this.vw, gy + 1, this.faded(accentB, 200));
            }
         }
      }

      boolean hasSlideTooltip = (style == null || style.floatingTooltips()) && frameTooltipTitle != null && frameTooltipBody != null;
      if (hasSlideTooltip) {
         this.tooltipTitle = frameTooltipTitle;
         this.tooltipKeyBadge = frameTooltipKey;
         this.tooltipBody = frameTooltipBody;
         this.tooltipAnchorX = frameAnchorX;
         this.tooltipAnchorY = frameAnchorY;
         this.tooltipAnchorW = frameAnchorW;
      }

      this.tooltipProgress = Glass.approach(this.tooltipProgress, hasSlideTooltip ? 1.0F : 0.0F, 18.0F, delta);
      if (this.tooltipProgress > 0.02F && this.tooltipTitle != null && this.tooltipBody != null) {
         this.drawSlideTooltip(graphics, accent, accentB);
      }

      if (this.bindingModule != null) {
         this.hoveredDescription = "Binding " + this.bindingModule.getName() + "  ·  press a key  ·  Esc cancel  ·  Del clear";
      } else if (this.highlighted != null && !this.hudEditMode) {
         if (DEFAULT_TIP.equals(this.hoveredDescription)) {
            this.hoveredDescription = "↑↓ move  ·  Enter toggle " + this.highlighted.getName() + "  ·  Shift+Enter or → settings  ·  Esc clear search";
         }
      }

      if (this.flashText != null && now < this.flashUntil) {
         this.hoveredDescription = this.flashText;
      }

      this.drawFooter(graphics, mouseX, mouseY, accent, accentB, style == null || style.tooltips(), now);
      graphics.pose().popMatrix();
      super.extractRenderState(graphics, rawMouseX, rawMouseY, partialTick);
   }

   private int searchWidth() {
      return Math.min(180, Math.max(110, this.vw / 4));
   }

   private void drawSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent, int accentB, long now, float delta) {
      int searchW = this.searchWidth();
      int searchX = this.vw - 16 - searchW;
      boolean over = inside(mouseX, mouseY, searchX, 28, searchW, 16);
      Glass.roundRect(graphics, searchX, 27, searchX + searchW, 45, Glass.RADIUS, this.faded(Glass.surface(this.searchFocused || over ? 2 : 1), 220));
      // The one field on the bar keeps a faint edge; focus tints it with the accent.
      Glass.roundOutline(graphics, searchX, 27, searchX + searchW, 45, Glass.RADIUS, this.searchFocused ? this.faded(accent, 150) : this.faded(0xFFFFFF, 24));
      int textRight = searchX + searchW - 8;
      if (!this.search.isEmpty()) {
         boolean overClear = inside(mouseX, mouseY, searchX + searchW - 16, 28, 16, 16);
         this.text(graphics, "x", searchX + searchW - 12, 32, overClear ? Glass.text() : Glass.textFaint());
         int results = 0;
         for (CategoryPanel panel : this.panels) {
            results += panel.visibleModules(this.search).size();
         }

         String count = Integer.toString(results);
         textRight = searchX + searchW - 20 - UiFont.width(count);
         this.text(graphics, count, textRight, 32, results == 0 ? Glass.ERROR : accentB);
         textRight -= 4;
      }

      boolean blink = this.searchFocused && now / 420L % 2L == 0L;
      String query = this.search.isEmpty() && !this.searchFocused ? "Search modules & settings..." : this.search + (blink ? "|" : "");
      while (UiFont.width(query) > textRight - searchX - 8 && query.length() > 1) {
         query = this.search.isEmpty() && !this.searchFocused ? query.substring(0, query.length() - 1) : query.substring(1);
      }

      this.text(graphics, query, searchX + 8, 32, this.search.isEmpty() ? Glass.textFaint() : Glass.text());
   }

   private void drawChips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent, int accentB) {
      for (ClickGuiScreen.Chip chip : this.chips) {
         boolean activeChip = "Edit HUD".equals(chip.label) && this.hudEditMode || chip.label.equals(this.filterMode);
         boolean over = chip.contains(mouseX, mouseY);
         // Plain text; the active one gets a soft fill, hover only brightens the label.
         if (activeChip) {
            Glass.roundRect(graphics, chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, 4, this.faded(accent, 110));
         }

         this.text(graphics, chip.label, chip.x + 6, chip.y + 3, over || activeChip ? Glass.text() : Glass.textDim());
      }
   }

   private List<String> wrapLines(String text, int maxWidth) {
      List<String> lines = new ArrayList<>();
      if (text == null || text.isEmpty()) {
         return lines;
      }

      String[] words = text.split("\\s+");
      StringBuilder current = new StringBuilder();
      for (String word : words) {
         if (current.length() == 0) {
            current.append(word);
         } else if (UiFont.width(current + " " + word) <= maxWidth) {
            current.append(' ').append(word);
         } else {
            lines.add(current.toString());
            current.setLength(0);
            current.append(word);
         }
      }

      if (current.length() > 0) {
         lines.add(current.toString());
      }

      return lines;
   }

   private void drawSlideTooltip(GuiGraphicsExtractor graphics, int accent, int accentB) {
      String cleanBody = this.tooltipBody.replaceAll("§.", "");
      boolean badge = this.tooltipKeyBadge != null && !"NONE".equals(this.tooltipKeyBadge);
      int titleW = UiFont.width(this.tooltipTitle) + (badge ? UiFont.width(" [" + this.tooltipKeyBadge + "]") + 8 : 0);
      int maxTextW = Math.max(175, Math.min(245, titleW + 16));
      List<String> wrapped = this.wrapLines(cleanBody, maxTextW);
      int widestLine = titleW;
      for (String line : wrapped) {
         widestLine = Math.max(widestLine, UiFont.width(line));
      }

      int cardW = widestLine + 16;
      int cardH = 18 + Math.max(1, wrapped.size()) * 10 + 4;
      int slideOffset = Math.round((1.0F - this.tooltipProgress) * 10.0F);
      int tx = this.tooltipAnchorX + this.tooltipAnchorW + 8 - slideOffset;
      if (tx + cardW > this.vw - 6) {
         tx = Math.max(6, this.tooltipAnchorX - cardW - 8 + slideOffset);
      }

      int ty = Mth.clamp(this.tooltipAnchorY - 2, TOP_BAR + 4, Math.max(TOP_BAR + 4, this.vh - FOOTER - cardH - 4));
      float show = this.tooltipProgress * this.fade;
      Glass.roundRect(graphics, tx, ty, tx + cardW, ty + cardH, Glass.RADIUS, Glass.fade(Glass.withAlpha(Glass.surface(1), 235), show));
      Glass.roundOutline(graphics, tx, ty, tx + cardW, ty + cardH, Glass.RADIUS, Glass.fade(Glass.withAlpha(0xFFFFFF, 20), show));
      Controls.text(graphics, this.font, this.tooltipTitle, tx + 8, ty + 5, Glass.text(), show);
      if (badge) {
         String label = "[" + this.tooltipKeyBadge + "]";
         Controls.text(graphics, this.font, label, tx + cardW - 6 - UiFont.width(label), ty + 5, Glass.textDim(), show);
      }

      int lineY = ty + 16;
      for (String line : wrapped) {
         Controls.text(graphics, this.font, line, tx + 8, lineY, Glass.textDim(), show);
         lineY += 10;
      }
   }

   // ---- HUD editor -----------------------------------------------------------------------------------------

   private float hudScale() {
      HudModule hud = Gemini.getInstance().modules().byClass(HudModule.class).orElse(null);
      return hud == null ? 1.0F : hud.scale();
   }

   /** HUD coordinate to this screen's (scaled) coordinate. */
   private int fromHud(float value) {
      return Math.round(value * this.hudScale() / this.uiScale);
   }

   private void renderHudEditOverlay(GuiGraphicsExtractor graphics, int rawMouseX, int rawMouseY, int accent, int accentB, long now) {
      float hudScale = this.hudScale();
      double hudMx = rawMouseX / hudScale;
      double hudMy = rawMouseY / hudScale;
      boolean editing = this.hudEditMode || Controls.shiftDown();
      boolean dragging = this.draggingWidget != null;
      if (editing) {
         // centre crosshair: faint while idle, clear while dragging, bright when the widget is centred on it
         boolean centredX = dragging && this.guides.kindX == UiMath.SNAP_CENTER;
         boolean centredY = dragging && this.guides.kindY == UiMath.SNAP_CENTER;
         int cx = this.vw / 2;
         int cy = this.vh / 2;
         graphics.fill(cx, TOP_BAR, cx + 1, this.vh - FOOTER, this.faded(centredX ? Glass.OK : accentB, centredX ? 255 : (dragging ? 110 : 40)));
         graphics.fill(0, cy, this.vw, cy + 1, this.faded(centredY ? Glass.OK : accentB, centredY ? 255 : (dragging ? 110 : 40)));
         if (centredX) {
            graphics.fill(cx - 1, TOP_BAR, cx + 2, this.vh - FOOTER, this.faded(Glass.OK, 70));
         }

         if (centredY) {
            graphics.fill(0, cy - 1, this.vw, cy + 2, this.faded(Glass.OK, 70));
         }

         if (dragging) {
            // the edge, alignment and spacing lines the dragged widget is snapped to right now
            if (!Float.isNaN(this.guides.lineX) && !centredX) {
               int gx = this.fromHud(this.guides.lineX);
               graphics.fill(gx, TOP_BAR, gx + 1, this.vh - FOOTER, this.faded(this.guides.kindX == UiMath.SNAP_EQUAL ? Glass.WARN : accentB, 210));
            }

            if (!Float.isNaN(this.guides.lineY) && !centredY) {
               int gy = this.fromHud(this.guides.lineY);
               graphics.fill(0, gy, this.vw, gy + 1, this.faded(this.guides.kindY == UiMath.SNAP_EQUAL ? Glass.WARN : accentB, 210));
            }
         }
      }

      for (Module module : Gemini.getInstance().modules().of(Category.HUD)) {
         if (module instanceof HudWidget widget && widget.isEnabled() && widget.lastScreenW() > 0.0F) {
            boolean selected = widget == this.selectedWidget;
            if (!editing && !selected) {
               continue;
            }

            int wx = this.fromHud(widget.lastScreenX());
            int wy = this.fromHud(widget.lastScreenY());
            int ww = this.fromHud(widget.lastScreenW());
            int wh = this.fromHud(widget.lastScreenH());
            boolean over = widget == this.draggingWidget || editing && widget.containsScreenPoint(hudMx, hudMy);
            if (selected) {
               Glass.glow(graphics, wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 2, accentB, 0.7F * this.fade);
               Glass.roundOutline(graphics, wx - 2, wy - 2, wx + ww + 2, wy + wh + 2, 2, this.faded(accentB, 255));
            } else {
               Glass.roundOutline(graphics, wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, 2, this.faded(over ? accentB : accent, over ? 220 : 110));
            }

            if (over && !selected) {
               graphics.fill(wx, wy, wx + ww, wy + wh, this.faded(accentB, 26));
            }

            String label = widget.getName();
            if (selected) {
               label = label + "  x " + Math.round(widget.lastScreenX()) + "  y " + Math.round(widget.lastScreenY());
            }

            int labelW = UiFont.width(label) + 6;
            int ly = wy - 13 < TOP_BAR + 2 ? wy + wh + 3 : wy - 13;
            int lx = Mth.clamp(wx, 2, Math.max(2, this.vw - labelW - 2));
            if (selected || over) {
               Glass.roundRect(graphics, lx, ly, lx + labelW, ly + 11, 3, this.faded(Glass.surface(0), 225));
               Glass.roundOutline(graphics, lx, ly, lx + labelW, ly + 11, 3, this.faded(accentB, selected ? 220 : 110));
            }

            this.text(graphics, label, lx + 3, ly + 2, selected || over ? Glass.text() : accentB);
         }
      }

      if (dragging) {
         this.hoveredDescription = "Dragging " + this.draggingWidget.getName() + "  ·  snaps to edges, centres and other widgets  ·  release to save";
      } else if (this.selectedWidget != null && (editing || now - this.nudgedAt < 4000L)) {
         this.hoveredDescription = this.selectedWidget.getName()
            + " selected  ·  arrows nudge 1px, Shift+arrows 10px  ·  x "
            + Math.round(this.selectedWidget.lastScreenX())
            + "  y "
            + Math.round(this.selectedWidget.lastScreenY());
      } else if (this.hudEditMode) {
         this.hoveredDescription = "HUD Edit Mode  ·  drag any enabled HUD widget, click one to select it  ·  click Edit HUD to return to modules";
      }
   }

   /** Moves the selected HUD widget by whole pixels. Returns false when there is nothing to move. */
   private boolean nudgeSelected(int dx, int dy) {
      HudWidget widget = this.selectedWidget;
      if (widget == null || !widget.isEnabled() || widget.lastScreenW() <= 0.0F) {
         return false;
      }

      float hudScale = this.hudScale();
      widget.nudge(Math.round(this.width / hudScale), Math.round(this.height / hudScale), dx, dy);
      this.nudgedAt = Util.getMillis();
      return true;
   }

   // ---- footer ---------------------------------------------------------------------------------------------

   private String menuKeyLabel() {
      return this.bindingMenu ? "Press any key..." : "Menu key  " + GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString();
   }

   private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int accent, int accentB, boolean tooltips, long now) {
      String bindLabel = this.menuKeyLabel();
      int bindWidth = UiFont.width(bindLabel) + 18;
      int bindX = 14;
      int bindY = this.vh - FOOTER + 4;
      boolean overBind = inside(mouseX, mouseY, bindX, bindY, bindWidth, 18);
      if (this.bindingMenu) {
         Glass.roundRect(graphics, bindX, bindY, bindX + bindWidth, bindY + 18, Glass.RADIUS, this.faded(accent, 110));
      }

      this.text(graphics, bindLabel, bindX + 9, bindY + 5, this.bindingMenu || overBind ? Glass.text() : Glass.textDim());
      boolean flashing = this.flashText != null && now < this.flashUntil;
      if (tooltips || flashing) {
         String tip = this.hoveredDescription.replaceAll("§.", "");
         int maxWidth = Math.max(120, this.vw - (bindX + bindWidth + 12) - 22);
         if (UiFont.width(tip) <= maxWidth) {
            this.text(graphics, tip, bindX + bindWidth + 12, bindY + 5, flashing ? accentB : Glass.textDim());
         } else {
            List<String> lines = this.wrapLines(tip, maxWidth);
            int topY = lines.size() > 1 ? bindY + 1 : bindY + 5;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
               this.text(graphics, lines.get(i), bindX + bindWidth + 12, topY + i * 9, flashing ? accentB : Glass.textDim());
            }
         }
      }

   }

   // ---- mouse ----------------------------------------------------------------------------------------------

   private SettingControl focusedControl() {
      for (CategoryPanel panel : this.panels) {
         SettingControl control = panel.focusedControl();
         if (control != null) {
            return control;
         }
      }

      return null;
   }

   public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubled) {
      if (this.closing) {
         return true;
      }

      // a number or hex being typed is finished by a press anywhere but on its own control
      SettingControl editing = this.focusedControl();
      for (CategoryPanel panel : this.panels) {
         // forget the last press's control so only the panel that takes this press reports one
         panel.forgetClick();
      }

      boolean handled = this.handleClick(rawEvent, doubled);
      if (editing != null) {
         boolean sameControl = false;
         for (CategoryPanel panel : this.panels) {
            sameControl |= panel.clickedControl() == editing;
         }

         if (!sameControl) {
            editing.blur();
         }
      }

      return handled || super.mouseClicked(rawEvent, doubled);
   }

   private boolean handleClick(MouseButtonEvent rawEvent, boolean doubled) {
      MouseButtonEvent event = this.scaled(rawEvent);
      double mx = event.x();
      double my = event.y();
      int button = Keys.glfwButton(event.button());
      int searchW = this.searchWidth();
      int searchX = this.vw - 16 - searchW;
      if (mx >= searchX && mx <= searchX + searchW && my >= 28.0 && my <= 44.0) {
         if (!this.search.isEmpty() && mx >= searchX + searchW - 16) {
            this.search = "";
            this.searchChanged();
         }

         this.searchFocused = true;
         return true;
      }

      this.searchFocused = false;
      for (ClickGuiScreen.Chip chip : this.chips) {
         if (button == 0 && chip.contains(mx, my)) {
            this.runChip(chip);
            return true;
         }
      }

      String bindLabel = this.menuKeyLabel();
      int bindWidth = UiFont.width(bindLabel) + 18;
      int bindY = this.vh - FOOTER + 4;
      if (button == 0 && mx >= 14.0 && mx <= 14 + bindWidth && my >= bindY && my <= bindY + 18) {
         this.bindingMenu = true;
         this.bindingModule = null;
         return true;
      }

      if (button == 0 && (this.hudEditMode || Controls.shiftDown())) {
         float hudScale = this.hudScale();
         double hudMx = rawEvent.x() / hudScale;
         double hudMy = rawEvent.y() / hudScale;
         List<Module> hudModules = Gemini.getInstance().modules().of(Category.HUD);

         for (int i = hudModules.size() - 1; i >= 0; i--) {
            if (hudModules.get(i) instanceof HudWidget widget && widget.containsScreenPoint(hudMx, hudMy)) {
               this.draggingWidget = widget;
               this.selectedWidget = widget;
               this.guides.clear();
               this.widgetGrabX = (float)(hudMx - widget.lastScreenX());
               this.widgetGrabY = (float)(hudMy - widget.lastScreenY());
               return true;
            }
         }

         if (this.hudEditMode) {
            this.selectedWidget = null;
            return true;
         }
      }

      if (this.hudEditMode) {
         return true;
      }

      for (int i = this.panels.size() - 1; i >= 0; i--) {
         CategoryPanel panel = this.panels.get(i);
         if (panel.mouseClicked(event, doubled, this)) {
            this.panels.remove(i);
            this.panels.add(panel);
            this.selectedWidget = null;
            return true;
         }
      }

      this.selectedWidget = null;
      return false;
   }

   private void runChip(ClickGuiScreen.Chip chip) {
      String label = chip.label;
      switch (label) {
         case MORE:
         case LESS:
            moreChips = !moreChips;
            this.buildChips();
            return;
         case "All":
         case "Active Only":
         case "Keybound":
         case "Favorites":
            this.filterMode = label;
            for (CategoryPanel panel : this.panels) {
               panel.setFilterMode(label);
            }

            this.searchChanged();
            this.flash("Filter: " + label);
            return;
         case "Collapse all":
            for (CategoryPanel panel : this.panels) {
               panel.setCollapsed(true);
            }
            break;
         case "Expand all":
            for (CategoryPanel panel : this.panels) {
               panel.setCollapsed(false);
            }
            break;
         case "Edit HUD":
            this.hudEditMode = !this.hudEditMode;
            this.draggingWidget = null;
            this.selectedWidget = null;
            this.flash(this.hudEditMode ? "HUD Edit Mode enabled · drag any HUD widget, arrows nudge the selected one" : "Returned to module panels");
            return;
         case "Window layout":
            ClickGuiModule layoutStyle = this.style();
            if (layoutStyle != null) {
               layoutStyle.setPanelLayout(false);
            }

            this.minecraft.gui.setScreen(new WindowGuiScreen());
            return;
         case "Save Preset":
            if (Gemini.getInstance().config().saveProfile("default")) {
               this.flash("Saved full preset 'default' (modules + UI layout)");
            }

            return;
         case "Load Preset":
            if (Gemini.getInstance().config().loadProfile("default")) {
               this.init();
               this.flash("Loaded preset 'default'");
            } else {
               this.flash("No 'default' preset yet · click Save Preset first");
            }

            return;
         default:
            for (CategoryPanel panel : this.panels) {
               panel.resetLayout();
            }

            this.init();
            this.flash("Layout reset");
      }

      Gemini.getInstance().config().save();
   }

   public boolean mouseReleased(MouseButtonEvent rawEvent) {
      this.draggingWidget = null;
      this.guides.clear();
      MouseButtonEvent event = this.scaled(rawEvent);

      for (CategoryPanel panel : this.panels) {
         panel.mouseReleased(event);
      }

      return super.mouseReleased(rawEvent);
   }

   public boolean mouseDragged(MouseButtonEvent rawEvent, double deltaX, double deltaY) {
      if (this.closing) {
         return true;
      }

      if (this.draggingWidget != null && Keys.glfwButton(rawEvent.button()) == 0) {
         ClickGuiModule style = this.style();
         float hudScale = this.hudScale();
         int hudW = Math.round(this.width / hudScale);
         int hudH = Math.round(this.height / hudScale);
         float newX = (float)(rawEvent.x() / hudScale) - this.widgetGrabX;
         float newY = (float)(rawEvent.y() / hudScale) - this.widgetGrabY;
         List<HudWidget> others = new ArrayList<>();
         for (Module module : Gemini.getInstance().modules().of(Category.HUD)) {
            if (module instanceof HudWidget widget && widget != this.draggingWidget) {
               others.add(widget);
            }
         }

         // Alt drags freely, as it does for the panels
         boolean free = Controls.altDown();
         this.draggingWidget.applyScreenPosition(hudW, hudH, newX, newY, !free && (style == null || style.snapGrid()), !free, others, this.guides);
         return true;
      }

      MouseButtonEvent event = this.scaled(rawEvent);
      for (CategoryPanel panel : this.panels) {
         if (panel.mouseDragged(event, this.panels)) {
            return true;
         }
      }

      return super.mouseDragged(rawEvent, deltaX, deltaY);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      if (this.hudEditMode || this.closing) {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      }

      ClickGuiModule style = this.style();
      float speed = style == null ? 2.0F : style.scrollSpeed();
      double mx = x / this.uiScale;
      double my = y / this.uiScale;

      for (int i = this.panels.size() - 1; i >= 0; i--) {
         if (this.panels.get(i).mouseScrolled(mx, my, scrollY, speed)) {
            return true;
         }
      }

      return super.mouseScrolled(x, y, scrollX, scrollY);
   }

   // ---- search and keyboard navigation ---------------------------------------------------------------------

   /** The panels left to right, then top to bottom: the order Up/Down walks through them. */
   private List<CategoryPanel> readingOrder() {
      List<CategoryPanel> ordered = new ArrayList<>(this.panels);
      ordered.sort(Comparator.comparingInt(CategoryPanel::x).thenComparingInt(CategoryPanel::y));
      return ordered;
   }

   private void highlight(Module module) {
      this.highlighted = module;
      for (CategoryPanel panel : this.panels) {
         panel.setHighlighted(module != null && panel.category() == module.getCategory() ? module : null);
      }
   }

   /** After the search text or filter changed: put the highlight on the best match, or drop it. */
   private void searchChanged() {
      if (this.search.isBlank()) {
         this.highlight(null);
         return;
      }

      Module best = null;
      int bestScore = 0;
      for (CategoryPanel panel : this.readingOrder()) {
         for (Module module : panel.visibleModules(this.search)) {
            int score = panel.score(this.search, module);
            if (score > bestScore) {
               bestScore = score;
               best = module;
            }
         }
      }

      this.highlight(best);
   }

   private void moveHighlight(int direction) {
      List<Module> results = new ArrayList<>();
      for (CategoryPanel panel : this.readingOrder()) {
         results.addAll(panel.visibleModules(this.search));
      }

      if (results.isEmpty()) {
         this.highlight(null);
         return;
      }

      int index = results.indexOf(this.highlighted);
      int next = index < 0 ? (direction > 0 ? 0 : results.size() - 1) : Math.floorMod(index + direction, results.size());
      this.highlight(results.get(next));
   }

   private CategoryPanel panelOf(Module module) {
      for (CategoryPanel panel : this.panels) {
         if (panel.category() == module.getCategory()) {
            return panel;
         }
      }

      return null;
   }

   private void toggleHighlighted() {
      Module module = this.highlighted;
      if (!module.isSavedEnabled() && !module.canActivate()) {
         this.flash(module.blockedReason());
      } else {
         module.toggle();
      }
   }

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
            Gemini.getInstance().config().save();
            String clash = gemini.input.BindWarnings.afterMenuKey(Gemini.getInstance().modules());
            if (clash != null) {
               this.flash(clash);
            }
         }

         this.bindingMenu = false;
         return true;
      } else if (this.bindingModule != null) {
         if (key != Keys.ESCAPE) {
            if (key != Keys.DELETE && key != Keys.BACKSPACE) {
               this.bindingModule.setKeybind(key);
               String clash = gemini.input.BindWarnings.afterBind(Gemini.getInstance().modules(), this.bindingModule, key);
               if (clash != null) {
                  this.flash(clash);
               }
            } else {
               this.bindingModule.setKeybind(-1);
               gemini.input.BindWarnings.invalidate();
            }

            Gemini.getInstance().config().save();
         }

         this.bindingModule = null;
         return true;
      }

      if (GeminiKeys.opensPalette(event) && this.draggingWidget == null) {
         gemini.ui.palette.PaletteScreen.open(this.minecraft);
         return true;
      }

      SettingControl editing = this.focusedControl();
      if (editing != null) {
         editing.keyPressed(event);
         return true;
      }

      if (key == Keys.ESCAPE) {
         // Esc clears the search first and closes the menu second
         if (this.searchFocused || !this.search.isEmpty()) {
            this.search = "";
            this.searchFocused = false;
            this.searchChanged();
         } else {
            this.onClose();
         }

         return true;
      } else if (GeminiKeys.OPEN_GUI.matches(event)) {
         this.onClose();
         return true;
      } else if (key == Keys.F && event.hasControlDown()) {
         this.searchFocused = true;
         return true;
      }

      boolean arrow = key == Keys.UP || key == Keys.DOWN || key == Keys.LEFT || key == Keys.RIGHT;
      if (arrow) {
         // arrows belong to the selected HUD widget in the HUD editor, and outside it while nothing is searched
         boolean widgetKeys = this.selectedWidget != null && (this.hudEditMode || this.search.isEmpty() && this.highlighted == null);
         if (widgetKeys) {
            int step = event.hasShiftDown() ? 10 : 1;
            this.nudgeSelected(key == Keys.LEFT ? -step : (key == Keys.RIGHT ? step : 0), key == Keys.UP ? -step : (key == Keys.DOWN ? step : 0));
            return true;
         } else if (this.hudEditMode) {
            return true;
         } else if (key == Keys.UP || key == Keys.DOWN) {
            this.moveHighlight(key == Keys.DOWN ? 1 : -1);
            return true;
         } else if (this.highlighted != null) {
            CategoryPanel panel = this.panelOf(this.highlighted);
            if (panel != null) {
               panel.setExpanded(this.highlighted, key == Keys.RIGHT);
            }

            return true;
         }

         return super.keyPressed(event);
      } else if (Keys.isEnter(key)) {
         if (this.highlighted != null && !this.hudEditMode) {
            CategoryPanel panel = this.panelOf(this.highlighted);
            if (event.hasShiftDown() && panel != null) {
               panel.setExpanded(this.highlighted, !panel.isExpanded(this.highlighted));
            } else {
               this.toggleHighlighted();
            }
         }

         this.searchFocused = false;
         return true;
      } else if (key == Keys.BACKSPACE && !this.search.isEmpty()) {
         this.searchFocused = true;
         this.search = event.hasControlDown() ? "" : this.search.substring(0, this.search.offsetByCodePoints(this.search.length(), -1));
         this.searchChanged();
         return true;
      }

      return super.keyPressed(event);
   }

   public boolean charTyped(CharacterEvent event) {
      if (this.closing || this.bindingMenu || this.bindingModule != null) {
         return true;
      }

      SettingControl editing = this.focusedControl();
      if (editing != null) {
         editing.charTyped(event);
         return true;
      }

      if (!this.hudEditMode && event.isAllowedChatCharacter() && this.search.length() < 32) {
         this.searchFocused = true;
         this.search = this.search + event.codepointAsString();
         this.searchChanged();
         return true;
      }

      return super.charTyped(event);
   }

   public void openListEditor(Module module, ListSetting setting) {
      this.minecraft.gui.setScreen(new ListEditorScreen(this, module, setting));
   }

   public void beginModuleBind(Module module) {
      this.bindingModule = module;
      this.bindingMenu = false;
   }

   public boolean isBinding(Module module) {
      return this.bindingModule == module;
   }

   public void flash(String message) {
      if (message != null) {
         this.flashText = message;
         this.flashUntil = Util.getMillis() + 2600L;
      }
   }

   // ---- lifecycle ------------------------------------------------------------------------------------------

   /** Starts the close animation; the screen really closes from {@link #tick()} once it has faded out. */
   @Override
   public void onClose() {
      if (this.closing) {
         return;
      }

      SettingControl editing = this.focusedControl();
      if (editing != null) {
         editing.blur();
      }

      if (Glass.animations() && this.animationProgress > 0.05F) {
         this.closing = true;
         this.bindingMenu = false;
         this.bindingModule = null;
         this.draggingWidget = null;
      } else {
         super.onClose();
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.closing && (this.animationProgress <= 0.03F || Util.getMillis() - this.lastFrame > 500L)) {
         this.closing = false;
         super.onClose();
      }
   }

   @Override
   public void added() {
      super.added();
      // coming back from the list editor or reopening: never stay stuck in a half-finished close
      this.closing = false;
      this.lastFrame = Util.getMillis();
   }

   public void removed() {
      this.minecraft.textInputManager().stopTextInput(this);
      rememberedSearch = this.search;
      if (Gemini.getInstance() != null) {
         Gemini.getInstance().config().save();
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private ClickGuiModule style() {
      return Gemini.getInstance() == null ? null : Gemini.getInstance().modules().byClass(ClickGuiModule.class).orElse(null);
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private record Chip(String label, int x, int y, int w, int h) {
      private boolean contains(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private static final class Particle {
      private float x;
      private float y;
      private float speed;
      private int size;
      private int alpha;

      private static ClickGuiScreen.Particle random(int width, int height) {
         ClickGuiScreen.Particle particle = new ClickGuiScreen.Particle();
         particle.x = ThreadLocalRandom.current().nextInt(Math.max(1, width));
         particle.y = ThreadLocalRandom.current().nextInt(Math.max(1, height));
         particle.speed = 8.0F + ThreadLocalRandom.current().nextFloat() * 18.0F;
         particle.size = 1 + ThreadLocalRandom.current().nextInt(2);
         particle.alpha = 20 + ThreadLocalRandom.current().nextInt(40);
         return particle;
      }

      private void tick(float delta, int width, int height) {
         this.y = this.y - this.speed * delta;
         this.x = this.x + Mth.sin((this.y + this.x) * 0.02F) * 8.0F * delta;
         if (this.y < -4.0F) {
            this.y = height + 4;
            this.x = ThreadLocalRandom.current().nextInt(Math.max(1, width));
         }
      }
   }
}
