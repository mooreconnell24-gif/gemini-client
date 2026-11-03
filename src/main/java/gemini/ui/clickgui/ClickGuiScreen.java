package gemini.ui.clickgui;

import gemini.input.Keys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import gemini.Gemini;
import gemini.config.ConfigManager;
import gemini.input.GeminiKeys;
import gemini.input.KeybindReport;
import gemini.module.Category;
import gemini.module.HudWidget;
import gemini.module.Module;
import gemini.module.modules.misc.ClickGuiModule;
import gemini.module.modules.render.HudModule;
import gemini.module.setting.ListSetting;
import gemini.ui.GeminiMark;
import gemini.ui.HudOverlay;
import java.util.ArrayList;
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

public final class ClickGuiScreen extends Screen {
   private static final int GAP = 8;
   private static final int TOP_BAR = 54;
   private static final int FOOTER = 30;
   private static final String DEFAULT_TIP = "LMB toggle   RMB settings   MMB bind   Shift+drag HUD widgets";
   private static String rememberedSearch = "";
   public float animationProgress = 0.0F;
   private final List<CategoryPanel> panels = new ArrayList<>();
   private final List<ClickGuiScreen.Particle> particles = new ArrayList<>();
   private final List<ClickGuiScreen.Chip> chips = new ArrayList<>();
   private final long openedAt = Util.getMillis();
   private long lastFrame = this.openedAt;
   private String search = "";
   private boolean searchFocused;
   private String filterMode = "All";
   private Module bindingModule;
   private boolean bindingMenu;
   private boolean hudEditMode;
   private HudWidget draggingWidget;
   private final HudWidget.Guides guides = new HudWidget.Guides();
   private float widgetGrabX;
   private float widgetGrabY;
   private String hoveredDescription = "LMB toggle   RMB settings   MMB bind   Shift+drag HUD widgets";
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

   public void updateAnimations() {
      boolean open = true;
      float elapsed = Math.min(1.0F, (float)(Util.getMillis() - this.openedAt) / 260.0F);
      this.animationProgress = ease(elapsed);
   }

   protected void init() {
      // Minecraft 26.3 reads keys through SDL, which only sends typed characters to a screen that asks for them.
      this.minecraft.textInputManager().startTextInput(this);
      ClickGuiModule style = this.style();
      int count = Category.values().length;
      int needed = count * 128 + (count + 1) * 8;
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
      int totalWidth = count * 128 + (count - 1) * 8;
      int startX = Math.max(8, (this.vw - totalWidth) / 2);
      if (this.panels.size() == count) {
         for (CategoryPanel panel : this.panels) {
            panel.setDefaultPosition(startX + panel.category().ordinal() * 136, 62);
         }
      } else {
         this.panels.clear();

         for (Category category : Category.values()) {
            CategoryPanel panel = new CategoryPanel(category, Gemini.getInstance().modules().of(category), config.panel(category.name()));
            panel.setDefaultPosition(startX + category.ordinal() * 136, 62);
            this.panels.add(panel);
         }
      }

      for (int i = 0; i < 42; i++) {
         this.particles.add(ClickGuiScreen.Particle.random(this.vw, this.vh));
      }

      this.buildChips();
   }

   private void buildChips() {
      this.chips.clear();
      int x = 56;
      int y = 35;

      for (String label : new String[]{
         "All", "Active Only", "Keybound", "Collapse all", "Expand all", "Reset layout", "Edit HUD", "Save Preset", "Load Preset", "Window layout"
      }) {
         int w = this.font.width(label) + 12;
         this.chips.add(new ClickGuiScreen.Chip(label, x, y, w, 13));
         x += w + 4;
      }
   }

   private MouseButtonEvent scaled(MouseButtonEvent event) {
      return new MouseButtonEvent(event.x() / this.uiScale, event.y() / this.uiScale, event.buttonInfo());
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int rawMouseX, int rawMouseY, float partialTick) {
      long now = Util.getMillis();
      float delta = Math.min(0.05F, (float)(now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      this.updateAnimations();
      ClickGuiModule style = this.style();
      boolean animate = style == null || style.animations();
      float open = animate ? Math.max(this.animationProgress, ease(Math.min(1.0F, (float)(now - this.openedAt) / 320.0F))) : 1.0F;
      int accent = style == null ? -6467875 : style.accentColor();
      int accentB = style == null ? -16718337 : style.secondaryColor();
      boolean glow = style == null || style.glow();
      boolean snapGrid = style == null || style.snapGrid();
      float pulse = animate ? 0.55F + 0.45F * (float)Math.sin(now / 380.0) : 1.0F;
      if (style == null || style.dim()) {
         graphics.fill(0, 0, this.width, this.height, GeminiMark.withAlpha(-16447473, (int)(175.0F * open)));
      }

      int mouseX = Math.round(rawMouseX / this.uiScale);
      int mouseY = Math.round(rawMouseY / this.uiScale);
      graphics.pose().pushMatrix();
      graphics.pose().scale(this.uiScale, this.uiScale);
      if (style == null || style.dim()) {
         graphics.fill(0, 0, this.vw, 54, GeminiMark.withAlpha(-15987178, (int)(210.0F * open)));
         drawHorizontalGradient(graphics, 0, 52, this.vw, 54, accent, accentB, (int)(180.0F * open));
      }

      if (style == null || style.particles()) {
         for (ClickGuiScreen.Particle particle : this.particles) {
            particle.tick(delta, this.vw, this.vh);
            int px = (int)particle.x;
            int py = (int)particle.y;
            int color = (particle.size + px) % 2 == 0 ? accent : accentB;
            graphics.fill(px, py, px + particle.size, py + particle.size, GeminiMark.withAlpha(color, particle.alpha));
         }
      }

      GeminiMark.draw(graphics, 14, 9, 34, accent, pulse);
      graphics.text(this.font, "GEMINI", 56, 11, -1, true);
      graphics.text(this.font, "NEXTGEN", 56 + this.font.width("GEMINI ") + 2, 11, accent, false);
      int enabled = Gemini.getInstance().modules().getEnabledModules().size();
      int total = Gemini.getInstance().modules().getModules().size();
      int fps = Minecraft.getInstance().getFps();
      String meta = this.hudEditMode
         ? "v" + Gemini.VERSION + "  ·  HUD EDIT MODE (drag widgets or click Edit HUD to exit)"
         : "v" + Gemini.VERSION + "  ·  " + fps + " FPS  ·  " + enabled + "/" + total + " modules active";
      graphics.text(this.font, meta, 56, 22, this.hudEditMode ? accentB : -8351581, false);
      String stats = "Ctrl+F search  ·  " + enabled + "/" + total + " active";
      int searchW = Math.min(180, Math.max(110, this.vw / 4));
      int searchX = this.vw - 16 - searchW;
      graphics.text(this.font, stats, this.vw - 16 - this.font.width(stats), 12, -4733999, false);
      graphics.fill(searchX - 1, 27, searchX + searchW + 1, 45, GeminiMark.withAlpha(this.searchFocused ? accentB : accent, 45));
      graphics.fill(searchX, 28, searchX + searchW, 44, -586147808);
      drawHorizontalGradient(
         graphics, searchX, 43, searchX + searchW, 44, this.searchFocused ? accent : -14405567, this.searchFocused ? accentB : -14405567, 255
      );
      boolean blink = this.searchFocused && now / 420L % 2L == 0L;
      String query = this.search.isEmpty() && !this.searchFocused ? "Search modules..." : this.search + (blink ? "|" : "");

      while (this.font.width(query) > searchW - 26 && query.length() > 1) {
         query = query.substring(1);
      }

      graphics.text(this.font, query, searchX + 8, 32, this.search.isEmpty() ? -9800056 : -1577226, false);
      if (!this.search.isEmpty()) {
         graphics.text(this.font, "x", searchX + searchW - 12, 32, inside(mouseX, mouseY, searchX + searchW - 16, 28, 16, 16) ? -1 : -8351581, false);
      }

      for (ClickGuiScreen.Chip chip : this.chips) {
         boolean activeChip = "Edit HUD".equals(chip.label) && this.hudEditMode || chip.label.equals(this.filterMode);
         boolean over = chip.contains((double)mouseX, (double)mouseY);
         int bg = activeChip ? GeminiMark.withAlpha(accentB, 130) : (over ? GeminiMark.withAlpha(accent, 95) : -1441785824);
         graphics.fill(chip.x, chip.y, chip.x + chip.w, chip.y + chip.h, bg);
         graphics.fill(chip.x, chip.y + chip.h - 1, chip.x + chip.w, chip.y + chip.h, activeChip ? accentB : (over ? accent : -14405567));
         graphics.text(this.font, chip.label, chip.x + 6, chip.y + 3, !over && !activeChip ? -4733999 : -1, false);
      }

      this.hoveredDescription = "LMB toggle   RMB settings   MMB bind   Shift+drag HUD widgets";
      String frameTooltipTitle = null;
      String frameTooltipKey = null;
      String frameTooltipBody = null;
      int frameAnchorX = 0;
      int frameAnchorY = 0;
      int frameAnchorW = 0;
      if (this.hudEditMode || isShiftDown()) {
         this.renderHudEditOverlay(graphics, rawMouseX, rawMouseY, accent, accentB);
      }

      if (!this.hudEditMode) {
         int slide = (int)((1.0F - open) * 34.0F);
         float opacity = style == null ? 0.88F : style.opacity();
         float scrollSpeed = style == null ? 2.0F : style.scrollSpeed();
         CategoryPanel.Look look = new CategoryPanel.Look(accent, accentB, opacity, animate, open, scrollSpeed, glow, snapGrid);
         CategoryPanel top = null;

         for (CategoryPanel panel : this.panels) {
            if (panel.isDragging()) {
               top = panel;
               break;
            }
         }

         if (top == null) {
            for (int i = this.panels.size() - 1; i >= 0; i--) {
               if (this.panels.get(i).contains(mouseX, mouseY)) {
                  top = this.panels.get(i);
                  break;
               }
            }
         }

         for (CategoryPanel panelx : this.panels) {
            panelx.setFilterMode(this.filterMode);
            panelx.tick(delta, animate);
            String description = panelx.render(
               graphics, this.font, mouseX, mouseY, panelx == top, look, slide, this.search, this.vw, this.vh, this.bindingModule
            );
            if (description != null && panelx == top) {
               this.hoveredDescription = description;
               if (panelx.hoveredTitle() != null) {
                  frameTooltipTitle = panelx.hoveredTitle();
                  frameTooltipKey = panelx.hoveredKeyBadge();
                  frameTooltipBody = description;
                  frameAnchorX = panelx.hoveredAnchorX();
                  frameAnchorY = panelx.hoveredAnchorY();
                  frameAnchorW = panelx.hoveredAnchorW();
               }
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

      this.tooltipProgress = animate
         ? Mth.lerp(Math.min(1.0F, delta * 18.0F), this.tooltipProgress, hasSlideTooltip ? 1.0F : 0.0F)
         : (hasSlideTooltip ? 1.0F : 0.0F);
      if (this.tooltipProgress > 0.02F && this.tooltipTitle != null && this.tooltipBody != null) {
         this.drawSlideTooltip(graphics, accent, accentB);
      }

      if (this.bindingModule != null) {
         this.hoveredDescription = "Binding §f" + this.bindingModule.getName() + "§7  ·  press a key  ·  Esc cancel  ·  Del clear";
      }

      if (this.flashText != null && now < this.flashUntil) {
         this.hoveredDescription = this.flashText;
      }

      this.drawFooter(graphics, accent, accentB, style == null || style.tooltips(), now);
      graphics.pose().popMatrix();
      super.extractRenderState(graphics, rawMouseX, rawMouseY, partialTick);
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
         } else if (this.font.width(current + " " + word) <= maxWidth) {
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
      int titleW = this.font.width(this.tooltipTitle) + (this.tooltipKeyBadge != null ? this.font.width(" [" + this.tooltipKeyBadge + "]") + 8 : 0);
      int maxTextW = Math.max(175, Math.min(245, titleW + 16));
      List<String> wrapped = this.wrapLines(cleanBody, maxTextW);
      int widestLine = titleW;
      for (String line : wrapped) {
         widestLine = Math.max(widestLine, this.font.width(line));
      }

      int cardW = widestLine + 16;
      int cardH = 18 + Math.max(1, wrapped.size()) * 10 + 4;
      int slideOffset = Math.round((1.0F - this.tooltipProgress) * 10.0F);
      int tx = this.tooltipAnchorX + this.tooltipAnchorW + 8 - slideOffset;
      if (tx + cardW > this.vw - 6) {
         tx = Math.max(6, this.tooltipAnchorX - cardW - 8 + slideOffset);
      }

      int ty = Mth.clamp(this.tooltipAnchorY - 2, 58, Math.max(58, this.vh - 30 - cardH - 4));
      int alpha = Math.round(238.0F * this.tooltipProgress);
      graphics.fill(tx - 1, ty - 1, tx + cardW + 1, ty + cardH + 1, GeminiMark.withAlpha(accent, Math.round(78.0F * this.tooltipProgress)));
      graphics.fill(tx, ty, tx + cardW, ty + cardH, GeminiMark.withAlpha(-15987178, alpha));
      graphics.fill(tx, ty, tx + 2, ty + cardH, GeminiMark.withAlpha(accentB, Math.round(255.0F * this.tooltipProgress)));
      graphics.text(this.font, this.tooltipTitle, tx + 7, ty + 5, GeminiMark.withAlpha(-1, Math.round(255.0F * this.tooltipProgress)), false);
      if (this.tooltipKeyBadge != null && !"NONE".equals(this.tooltipKeyBadge)) {
         String badge = "[" + this.tooltipKeyBadge + "]";
         graphics.text(
            this.font, badge, tx + cardW - 6 - this.font.width(badge), ty + 5, GeminiMark.withAlpha(accentB, Math.round(255.0F * this.tooltipProgress)), false
         );
      }

      int lineY = ty + 16;
      for (String line : wrapped) {
         graphics.text(this.font, line, tx + 7, lineY, GeminiMark.withAlpha(-3617580, Math.round(255.0F * this.tooltipProgress)), false);
         lineY += 10;
      }
   }

   private void renderHudEditOverlay(GuiGraphicsExtractor graphics, int rawMouseX, int rawMouseY, int accent, int accentB) {
      HudModule hud = Gemini.getInstance().modules().byClass(HudModule.class).orElse(null);
      float hudScale = hud == null ? 1.0F : hud.scale();
      double hudMx = rawMouseX / hudScale;
      double hudMy = rawMouseY / hudScale;
      int hudW = Math.round(this.width / hudScale);
      int hudH = Math.round(this.height / hudScale);
      graphics.fill(this.vw / 2, 54, this.vw / 2 + 1, this.vh - 30, GeminiMark.withAlpha(accentB, 40));
      graphics.fill(0, this.vh / 2, this.vw, this.vh / 2 + 1, GeminiMark.withAlpha(accentB, 40));
      if (this.draggingWidget != null) {
         // the lines the dragged widget is snapped to right now
         if (!Float.isNaN(this.guides.lineX)) {
            int gx = Math.round(this.guides.lineX * hudScale / this.uiScale);
            graphics.fill(gx, 54, gx + 1, this.vh - 30, GeminiMark.withAlpha(accentB, 170));
         }

         if (!Float.isNaN(this.guides.lineY)) {
            int gy = Math.round(this.guides.lineY * hudScale / this.uiScale);
            graphics.fill(0, gy, this.vw, gy + 1, GeminiMark.withAlpha(accentB, 170));
         }
      }

      for (Module module : Gemini.getInstance().modules().of(Category.HUD)) {
         if (module instanceof HudWidget widget && widget.isEnabled() && widget.lastScreenW() > 0.0F) {
            int wx = Math.round(widget.lastScreenX() * hudScale / this.uiScale);
            int wy = Math.round(widget.lastScreenY() * hudScale / this.uiScale);
            int ww = Math.round(widget.lastScreenW() * hudScale / this.uiScale);
            int wh = Math.round(widget.lastScreenH() * hudScale / this.uiScale);
            boolean over = widget == this.draggingWidget || widget.containsScreenPoint(hudMx, hudMy);
            int border = over ? accentB : accent;
            graphics.fill(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, GeminiMark.withAlpha(border, over ? 90 : 45));
            graphics.text(this.font, widget.getName(), wx + 2, Math.max(56, wy - 10), over ? -1 : accentB, false);
         }
      }

      if (this.draggingWidget != null) {
         this.hoveredDescription = "Dragging HUD widget §f" + this.draggingWidget.getName() + "§7  ·  release mouse to snap & save";
      } else if (hudW > 0 && hudH > 0) {
         this.hoveredDescription = "HUD Edit Mode  ·  drag any enabled HUD widget  ·  click Edit HUD to return to modules";
      }
   }

   private static void drawHorizontalGradient(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int c1, int c2, int alpha) {
      int width = Math.max(1, right - left);
      int steps = Math.min(28, width);

      for (int i = 0; i < steps; i++) {
         int sx = left + width * i / steps;
         int ex = left + width * (i + 1) / steps;
         float t = steps <= 1 ? 0.0F : (float)i / (steps - 1);
         graphics.fill(sx, top, ex, bottom, GeminiMark.withAlpha(HudOverlay.lerp(c1, c2, t), alpha));
      }
   }

   private String menuKeyLabel() {
      return this.bindingMenu ? "Press any key..." : "Menu key  " + GeminiKeys.OPEN_GUI.getTranslatedKeyMessage().getString();
   }

   private void drawFooter(GuiGraphicsExtractor graphics, int accent, int accentB, boolean tooltips, long now) {
      String bindLabel = this.menuKeyLabel();
      int bindWidth = this.font.width(bindLabel) + 18;
      int bindX = 14;
      int bindY = this.vh - 30 + 4;
      graphics.fill(bindX, bindY, bindX + bindWidth, bindY + 18, this.bindingMenu ? GeminiMark.withAlpha(accent, 90) : -586147808);
      graphics.fill(bindX, bindY, bindX + 2, bindY + 18, accent);
      graphics.text(this.font, bindLabel, bindX + 8, bindY + 5, this.bindingMenu ? -1 : accent, false);
      String hint = "Wheel scroll · Shift+drag HUD";
      int hintW = this.font.width(hint);
      boolean flashing = this.flashText != null && now < this.flashUntil;
      if (tooltips || flashing) {
         String tip = this.hoveredDescription.replace("§f", "").replace("§7", "").replace("§b", "").replace("§a", "").replace("§c", "");
         int maxWidth = Math.max(120, this.vw - (bindX + bindWidth + 12) - hintW - 22);
         if (this.font.width(tip) <= maxWidth) {
            graphics.text(this.font, tip, bindX + bindWidth + 12, bindY + 5, flashing ? accentB : -5920072, false);
         } else {
            List<String> lines = this.wrapLines(tip, maxWidth);
            int topY = lines.size() > 1 ? bindY + 1 : bindY + 5;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
               graphics.text(this.font, lines.get(i), bindX + bindWidth + 12, topY + i * 9, flashing ? accentB : -5920072, false);
            }
         }
      }

      graphics.text(this.font, hint, this.vw - 14 - hintW, bindY + 5, -10523522, false);
   }

   public boolean mouseClicked(MouseButtonEvent rawEvent, boolean doubled) {
      MouseButtonEvent event = this.scaled(rawEvent);
      double mx = event.x();
      double my = event.y();
      int searchW = Math.min(180, Math.max(110, this.vw / 4));
      int searchX = this.vw - 16 - searchW;
      if (mx >= searchX && mx <= searchX + searchW && my >= 28.0 && my <= 44.0) {
         if (!this.search.isEmpty() && mx >= searchX + searchW - 16) {
            this.search = "";
         }

         this.searchFocused = true;
         return true;
      } else {
         this.searchFocused = false;

         for (ClickGuiScreen.Chip chip : this.chips) {
            if (Keys.glfwButton(event.button()) == 0 && chip.contains(mx, my)) {
               this.runChip(chip);
               return true;
            }
         }

         String bindLabel = this.menuKeyLabel();
         int bindWidth = this.font.width(bindLabel) + 18;
         int bindY = this.vh - 30 + 4;
         if (Keys.glfwButton(event.button()) == 0 && mx >= 14.0 && mx <= 14 + bindWidth && my >= bindY && my <= bindY + 18) {
            this.bindingMenu = true;
            this.bindingModule = null;
            return true;
         } else {
            if (Keys.glfwButton(event.button()) == 0 && (this.hudEditMode || isShiftDown())) {
               HudModule hud = Gemini.getInstance().modules().byClass(HudModule.class).orElse(null);
               float hudScale = hud == null ? 1.0F : hud.scale();
               double hudMx = rawEvent.x() / hudScale;
               double hudMy = rawEvent.y() / hudScale;
               List<Module> hudModules = Gemini.getInstance().modules().of(Category.HUD);

               for (int i = hudModules.size() - 1; i >= 0; i--) {
                  if (hudModules.get(i) instanceof HudWidget widget && widget.containsScreenPoint(hudMx, hudMy)) {
                     this.draggingWidget = widget;
                     this.widgetGrabX = (float)(hudMx - widget.lastScreenX());
                     this.widgetGrabY = (float)(hudMy - widget.lastScreenY());
                     return true;
                  }
               }

               if (this.hudEditMode) {
                  return true;
               }
            }

            for (int ix = this.panels.size() - 1; ix >= 0; ix--) {
               CategoryPanel panel = this.panels.get(ix);
               if (panel.mouseClicked(event, this.search, this)) {
                  this.panels.remove(ix);
                  this.panels.add(panel);
                  return true;
               }
            }

            return super.mouseClicked(rawEvent, doubled);
         }
      }
   }

   private void runChip(ClickGuiScreen.Chip chip) {
      String var2 = chip.label;
      switch (var2) {
         case "All":
         case "Active Only":
         case "Keybound":
            this.filterMode = var2;
            this.flash("Filter: " + var2);
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
            this.flash(this.hudEditMode ? "HUD Edit Mode enabled · drag any HUD widget" : "Returned to module panels");
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
      if (this.draggingWidget != null && Keys.glfwButton(rawEvent.button()) == 0) {
         HudModule hud = Gemini.getInstance().modules().byClass(HudModule.class).orElse(null);
         ClickGuiModule style = this.style();
         float hudScale = hud == null ? 1.0F : hud.scale();
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

         this.draggingWidget.applyScreenPosition(hudW, hudH, newX, newY, style == null || style.snapGrid(), others, this.guides);
         return true;
      } else {
         MouseButtonEvent event = this.scaled(rawEvent);

         for (CategoryPanel panel : this.panels) {
            if (panel.mouseDragged(event)) {
               return true;
            }
         }

         return super.mouseDragged(rawEvent, deltaX, deltaY);
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      if (this.hudEditMode) {
         return super.mouseScrolled(x, y, scrollX, scrollY);
      } else {
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
   }

   public boolean keyPressed(KeyEvent event) {
      if (this.bindingMenu) {
         if (event.key() != Keys.ESCAPE) {
            GeminiKeys.OPEN_GUI.setKey(InputConstants.getKey(event));
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            Gemini.getInstance().config().save();
         }

         this.bindingMenu = false;
         return true;
      } else if (this.bindingModule != null) {
         if (event.key() == Keys.ESCAPE) {
            this.bindingModule = null;
            return true;
         } else {
            if (event.key() != Keys.DELETE && event.key() != Keys.BACKSPACE) {
               this.bindingModule.setKeybind(event.key());
               String clash = KeybindReport.warning(Gemini.getInstance().modules(), this.bindingModule, event.key());
               if (clash != null) {
                  this.flash("§e" + clash);
               }
            } else {
               this.bindingModule.setKeybind(-1);
            }

            this.bindingModule = null;
            return true;
         }
      } else if (event.key() != Keys.ESCAPE || !this.searchFocused && this.search.isEmpty()) {
         if (GeminiKeys.OPEN_GUI.matches(event) || event.key() == Keys.ESCAPE) {
            this.onClose();
            return true;
         } else if (event.key() == Keys.F && isCtrlDown()) {
            this.searchFocused = true;
            return true;
         } else if (Keys.isEnter(event.key())) {
            this.searchFocused = false;
            return true;
         } else if (event.key() == Keys.BACKSPACE && !this.search.isEmpty()) {
            this.searchFocused = true;
            this.search = this.search.substring(0, this.search.length() - 1);
            return true;
         } else {
            return super.keyPressed(event);
         }
      } else {
         this.search = "";
         this.searchFocused = false;
         return true;
      }
   }

   public boolean charTyped(CharacterEvent event) {
      if (!this.bindingMenu && this.bindingModule == null) {
         if (event.isAllowedChatCharacter() && this.search.length() < 32) {
            this.searchFocused = true;
            this.search = this.search + event.codepointAsString();
            return true;
         } else {
            return super.charTyped(event);
         }
      } else {
         return true;
      }
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

   private static boolean isShiftDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(Keys.LEFT_SHIFT) || InputConstants.isKeyDown(Keys.RIGHT_SHIFT);
   }

   private static boolean isCtrlDown() {
      Window window = Minecraft.getInstance().getWindow();
      return InputConstants.isKeyDown(Keys.LEFT_CONTROL) || InputConstants.isKeyDown(Keys.RIGHT_CONTROL);
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private static float ease(float t) {
      return 1.0F - (float)Math.pow(1.0F - t, 3.0);
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
