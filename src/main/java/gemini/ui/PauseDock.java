package gemini.ui;

import gemini.Gemini;
import gemini.input.BindWarnings;
import gemini.module.ModuleManager;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.ui.clickgui.Menus;
import gemini.ui.font.UiFont;
import gemini.ui.help.HelpScreen;
import gemini.ui.mods.ModBrowserScreen;
import gemini.util.SingleplayerGate;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The Gemini bar on the Esc menu: one slim pill along the top edge, above the "Game Menu" title, with Modules,
 * Edit HUD, Mods, Guide and Panic, each an icon and a label. A "key conflicts" entry is added only while there are
 * conflicting key binds. Everything else (presets, the key-bind report, quick search) is one click further, inside
 * the module menu.
 *
 * <p>It is added from Fabric's screen-init hook (see {@link SandboxUi#onScreenInit}) and only where Gemini may
 * act, i.e. in a private single-player world with sandbox mode on. {@link DockLayout} sizes it: full labels, short
 * labels or icons alone, whichever fits the window. The entries are real buttons (see {@link GlassButton}), so
 * Tab, Enter and the narrator work on them as on the vanilla buttons below.
 */
public final class PauseDock {
   private static final String[] KEYS = {"menu", "hud", "mods", "guide", "panic", "keybinds"};
   private static final GlassIcon[] ICONS = {GlassIcon.MODULES, GlassIcon.HUD, GlassIcon.MODS, GlassIcon.GUIDE, GlassIcon.PANIC, GlassIcon.ALERT};
   private static final int MODS = 2;
   private static final int GUIDE = 3;
   private static final int PANIC = 4;
   private static final int KEYBINDS = 5;

   private PauseDock() {
   }

   public static void attach(Minecraft client, PauseScreen screen) {
      if (!screen.showsPauseMenu() || !SingleplayerGate.allowed() || Gemini.getInstance() == null) {
         return;
      }

      ModuleManager modules = Gemini.getInstance().modules();
      int conflicts = BindWarnings.conflictCount(modules);
      // The conflicts entry only exists while something is actually wrong.
      int count = conflicts > 0 ? KEYS.length : KEYS.length - 1;
      Component[] full = new Component[count];
      Component[] brief = new Component[count];
      int[] fullWidths = new int[count];
      int[] briefWidths = new int[count];
      for (int i = 0; i < count; i++) {
         if (i == KEYBINDS) {
            full[i] = Component.translatable(conflicts == 1 ? "gemini.dock.keybinds.one" : "gemini.dock.keybinds", conflicts);
            brief[i] = Component.translatable("gemini.dock.keybinds.short", conflicts);
         } else {
            full[i] = Component.translatable("gemini.dock." + KEYS[i]);
            brief[i] = Component.translatable("gemini.dock." + KEYS[i] + ".short");
         }

         fullWidths[i] = UiFont.width(full[i]);
         briefWidths[i] = UiFont.width(brief[i]);
      }

      DockLayout layout = DockLayout.compute(screen.width, screen.height, fullWidths, briefWidths);
      GlassButton[] buttons = new GlassButton[count];
      List<AbstractWidget> widgets = Screens.getWidgets(screen);
      widgets.add(new Bar(layout, buttons));
      for (int i = 0; i < count; i++) {
         int action = i;
         GlassButton button = new GlassButton(
               layout.x()[i],
               layout.y()[i],
               layout.w()[i],
               layout.buttonHeight(),
               layout.shortLabels() ? brief[i] : full[i],
               pressed -> run(action, client, screen)
            )
            .segment()
            .radius(layout.buttonHeight() / 2)
            .icon(ICONS[i]);
         if (layout.iconsOnly()) {
            button.iconOnly();
         }

         if (i == PANIC) {
            button.tint(Glass.ERROR);
         } else if (i == KEYBINDS) {
            button.tint(Glass.WARN);
         }

         button.setTooltip(UiFont.tooltip(Component.translatable("gemini.dock." + KEYS[i] + ".tooltip")));
         buttons[i] = button;
         widgets.add(button);
      }
   }

   private static void run(int action, Minecraft client, PauseScreen screen) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || !SingleplayerGate.allowed()) {
         // The world may have been opened to LAN, or Gemini switched off, since the group was built.
         SandboxUi.notify(client, "Gemini", SingleplayerGate.denialMessage().replaceAll("§.", ""), HudOverlay.Severity.WARNING);
         return;
      }

      switch (action) {
         case 0 -> client.gui.setScreen(Menus.create());
         case 1 -> client.gui.setScreen(ClickGuiScreen.hudEditor());
         case MODS -> ModBrowserScreen.open(screen);
         case GUIDE -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, screen);
         case PANIC -> panic(client, gemini.modules());
         default -> KeybindReportScreen.open(client, screen);
      }
   }

   private static void panic(Minecraft client, ModuleManager modules) {
      int before = modules.getEnabledModules().size();
      modules.panic();
      int stopped = Math.max(0, before - modules.getEnabledModules().size());
      SandboxUi.notify(
         client,
         "Panic",
         stopped == 0 ? "No modules were running." : stopped + (stopped == 1 ? " module switched off." : " modules switched off."),
         HudOverlay.Severity.INFO
      );
   }

   /**
    * The pill behind the entries. It also slides the bar in when the menu opens, and fades it out (and hides it)
    * if Gemini stops being allowed while the menu is open, e.g. after Open to LAN. It is never active, so it
    * takes no focus, clicks or narration.
    */
   private static final class Bar extends AbstractWidget {
      private final DockLayout layout;
      private final GlassButton[] buttons;
      private float shown = 1.0F;
      private float enter;
      private long lastFrame;

      Bar(DockLayout layout, GlassButton[] buttons) {
         super(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), Component.empty());
         this.layout = layout;
         this.buttons = buttons;
         this.active = false;
      }

      @Override
      protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
         long now = Util.getMillis();
         float delta = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
         this.lastFrame = now;
         boolean allowed = SingleplayerGate.allowed();
         this.shown = Glass.approach(this.shown, allowed ? 1.0F : 0.0F, 10.0F, delta);
         this.enter = Glass.animations() ? Glass.approach(this.enter, 1.0F, Theme.FADE_SPEED, delta) : 1.0F;
         float a = this.shown * this.enter;
         float slide = -(1.0F - Glass.easeOutCubic(this.enter)) * 8.0F;
         for (GlassButton button : this.buttons) {
            if (button != null) {
               button.visible = allowed || this.shown > 0.05F;
               button.active = allowed;
               button.setAlpha(a);
               button.setSlide(slide);
            }
         }

         if (a <= 0.02F) {
            return;
         }

         DockLayout l = this.layout;
         int x1 = l.panelX();
         int y1 = l.panelY();
         int x2 = x1 + l.panelWidth();
         int y2 = y1 + l.panelHeight();
         g.pose().pushMatrix();
         g.pose().translate(0.0F, slide);
         Glass.shadow(g, x1, y1, x2, y2, DockLayout.RADIUS, 12, 3, 0.4F * a);
         Glass.roundRect(g, x1, y1, x2, y2, DockLayout.RADIUS, Glass.fade(Glass.withAlpha(Glass.surface(0), 232), a));
         Glass.roundOutline(g, x1, y1, x2, y2, DockLayout.RADIUS, Glass.fade(Glass.withAlpha(Glass.lerp(0xFFFFFFFF, Glass.accent(), 0.5F), 34), a));
         // A hairline sets Panic (and the conflicts entry after it) apart from the entries that open something.
         if (PANIC < l.x().length && PANIC > 0) {
            int dx = l.x()[PANIC] - 1;
            g.fill(dx, y1 + 7, dx + 1, y2 - 7, Glass.fade(Glass.withAlpha(0xFFFFFF, 26), a));
         }

         g.pose().popMatrix();
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput output) {
         // Nothing to narrate: the buttons narrate themselves.
      }
   }
}
