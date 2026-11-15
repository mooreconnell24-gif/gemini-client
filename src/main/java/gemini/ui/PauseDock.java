package gemini.ui;

import gemini.Gemini;
import gemini.module.ModuleManager;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.ui.clickgui.WindowGuiScreen;
import gemini.util.SingleplayerGate;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The quick-action dock on the Esc menu: one-click glass buttons for both menus, the HUD layout editor, the
 * config manager, the keybind report and Panic.
 *
 * <p>It is added from Fabric's screen-init hook (see {@link SandboxUi#onScreenInit}) and only where Gemini may
 * act, i.e. in a private single-player world with sandbox mode on. {@link DockLayout} keeps it clear of the
 * vanilla buttons: a column down the left edge on roomy screens, a row above the "Game Menu" title otherwise.
 */
public final class PauseDock {
   private static final String[] KEYS = {"clickgui", "windowgui", "hud", "configs", "keybinds", "panic"};

   private PauseDock() {
   }

   public static void attach(Minecraft client, PauseScreen screen) {
      if (!screen.showsPauseMenu() || !SingleplayerGate.allowed() || Gemini.getInstance() == null) {
         return;
      }

      Font font = client.font;
      int count = KEYS.length;
      Component[] full = new Component[count];
      Component[] brief = new Component[count];
      int[] fullWidths = new int[count];
      int[] briefWidths = new int[count];
      for (int i = 0; i < count; i++) {
         full[i] = Component.translatable("gemini.dock." + KEYS[i]);
         brief[i] = Component.translatable("gemini.dock." + KEYS[i] + ".short");
         fullWidths[i] = font.width(full[i]);
         briefWidths[i] = font.width(brief[i]);
      }

      DockLayout layout = DockLayout.compute(screen.width, screen.height, fullWidths, briefWidths);
      GlassButton[] buttons = new GlassButton[count];
      List<AbstractWidget> widgets = Screens.getWidgets(screen);
      // The backdrop goes in first so the buttons are drawn on top of it.
      widgets.add(new Backdrop(layout, buttons));
      for (int i = 0; i < count; i++) {
         int action = i;
         buttons[i] = new GlassButton(
               layout.x()[i], layout.y()[i], layout.w()[i], layout.buttonHeight(), layout.shortLabels() ? brief[i] : full[i], pressed -> run(action, client, screen)
            )
            .expand(layout.side() ? 2 : 0);
         buttons[i].setTooltip(Tooltip.create(Component.translatable("gemini.dock." + KEYS[i] + ".tooltip")));
         widgets.add(buttons[i]);
      }
   }

   private static void run(int action, Minecraft client, PauseScreen screen) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || !SingleplayerGate.allowed()) {
         // The world may have been opened to LAN, or Gemini switched off, since the dock was built.
         SandboxUi.notify(client, "Gemini", SingleplayerGate.denialMessage().replaceAll("§.", ""), HudOverlay.Severity.WARNING);
         return;
      }

      switch (action) {
         case 0 -> client.gui.setScreen(new ClickGuiScreen());
         case 1 -> client.gui.setScreen(new WindowGuiScreen());
         case 2 -> client.gui.setScreen(ClickGuiScreen.hudEditor());
         case 3 -> GeminiTitleScreen.openConfigs(screen);
         case 4 -> KeybindReportScreen.open(client, screen);
         default -> panic(client, gemini.modules());
      }
   }

   private static void panic(Minecraft client, ModuleManager modules) {
      int before = modules.getEnabledModules().size();
      modules.panic();
      int stopped = Math.max(0, before - modules.getEnabledModules().size());
      SandboxUi.notify(
         client,
         "Panic",
         stopped == 0 ? "No modules were running." : stopped + (stopped == 1 ? " module disabled." : " modules disabled."),
         HudOverlay.Severity.WARNING
      );
   }

   /** The glass card behind the buttons. It is never active, so it takes no focus, clicks or narration. */
   private static final class Backdrop extends AbstractWidget {
      private final DockLayout layout;
      private final GlassButton[] buttons;
      private float shown;
      private long lastFrame;

      Backdrop(DockLayout layout, GlassButton[] buttons) {
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
         // Gemini can stop being allowed while the menu is open (Open to LAN); the dock then fades away.
         boolean allowed = SingleplayerGate.allowed();
         this.shown = Glass.approach(this.shown, allowed ? 1.0F : 0.0F, 10.0F, delta);
         for (GlassButton button : this.buttons) {
            if (button != null) {
               button.visible = allowed;
               button.setAlpha(this.shown);
            }
         }

         if (this.shown <= 0.05F) {
            return;
         }

         Glass.panel(g, this.getX(), this.getY(), this.getRight(), this.getBottom(), this.shown, 0.0F);
         if (this.layout.side()) {
            Font font = Minecraft.getInstance().font;
            g.text(font, "GEMINI", this.getX() + 6, this.getY() + 5, Glass.fade(Glass.accent2(), this.shown), false);
            Glass.gradientBar(
               g,
               this.getX() + 6 + font.width("GEMINI") + 5,
               this.getY() + 8,
               this.getRight() - 6,
               this.getY() + 9,
               Glass.fade(Glass.withAlpha(Glass.accent(), 150), this.shown),
               Glass.withAlpha(Glass.accent2(), 0)
            );
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput output) {
         // Decoration only: the buttons narrate themselves.
      }
   }
}
