package gemini.ui;

import gemini.Gemini;
import gemini.input.BindWarnings;
import gemini.module.ModuleManager;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.ui.clickgui.Menus;
import gemini.ui.help.HelpScreen;
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
 * A small Gemini group on the Esc menu: buttons for the module menu, the HUD editor and the guide, then Panic as a
 * plain text link, and a "key conflicts" link only while there are conflicting key binds. Everything else (presets,
 * the key-bind report, quick search) is one click further, inside the module menu.
 *
 * <p>It is added from Fabric's screen-init hook (see {@link SandboxUi#onScreenInit}) and only where Gemini may
 * act, i.e. in a private single-player world with sandbox mode on. {@link DockLayout} keeps it clear of the
 * vanilla buttons: a short stack in the bottom-left corner on roomy screens, a row above the "Game Menu" title
 * otherwise.
 */
public final class PauseDock {
   private static final String[] KEYS = {"menu", "hud", "guide", "panic", "keybinds"};
   /** Index of the first plain text link; the entries before it are buttons. */
   private static final int FIRST_LINK = 3;
   private static final int PANIC = 3;
   private static final int KEYBINDS = 4;

   private PauseDock() {
   }

   public static void attach(Minecraft client, PauseScreen screen) {
      if (!screen.showsPauseMenu() || !SingleplayerGate.allowed() || Gemini.getInstance() == null) {
         return;
      }

      ModuleManager modules = Gemini.getInstance().modules();
      int conflicts = BindWarnings.conflictCount(modules);
      // The conflicts link only exists while something is actually wrong.
      int count = conflicts > 0 ? KEYS.length : KEYS.length - 1;
      Font font = client.font;
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

         fullWidths[i] = font.width(full[i]);
         briefWidths[i] = font.width(brief[i]);
      }

      DockLayout layout = DockLayout.compute(screen.width, screen.height, fullWidths, briefWidths);
      GlassButton[] buttons = new GlassButton[count];
      List<AbstractWidget> widgets = Screens.getWidgets(screen);
      widgets.add(new Fader(buttons));
      for (int i = 0; i < count; i++) {
         int action = i;
         buttons[i] = new GlassButton(
            layout.x()[i], layout.y()[i], layout.w()[i], layout.buttonHeight(), layout.shortLabels() ? brief[i] : full[i], pressed -> run(action, client, screen)
         );
         if (i >= FIRST_LINK) {
            buttons[i].plain();
         }

         buttons[i].setTooltip(Tooltip.create(Component.translatable("gemini.dock." + KEYS[i] + ".tooltip")));
         widgets.add(buttons[i]);
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
         case 2 -> HelpScreen.open(HelpScreen.Tab.WHATS_NEW, screen);
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
    * Draws nothing; it fades the group out (and hides it) if Gemini stops being allowed while the menu is open,
    * e.g. after Open to LAN. It is never active, so it takes no focus, clicks or narration.
    */
   private static final class Fader extends AbstractWidget {
      private final GlassButton[] buttons;
      private float shown = 1.0F;
      private long lastFrame;

      Fader(GlassButton[] buttons) {
         super(0, 0, 0, 0, Component.empty());
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
         for (GlassButton button : this.buttons) {
            if (button != null) {
               button.visible = allowed || this.shown > 0.05F;
               button.active = allowed;
               button.setAlpha(this.shown);
            }
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput output) {
         // Nothing to narrate: the buttons narrate themselves.
      }
   }
}
