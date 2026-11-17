package gemini.ui;

import gemini.Gemini;
import gemini.util.SingleplayerGate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Everything the player sees about the sandbox switch: the screen shown instead of a refused connection, the
 * notice that replaces the server list while the sandbox is on, and the on/off switch on the title screens (the
 * Gemini home screen draws its own; the vanilla title screen gets a button). It is also where Fabric's
 * screen-init hook fans out to the home screen swap and the pause-menu dock.
 */
public final class SandboxUi {
   private static final int BUTTON_WIDTH = 150;

   private SandboxUi() {
   }

   /**
    * Shown wherever a server or Realms connection was refused. "Switch Gemini off" turns the sandbox off and goes
    * back to {@code parent} so the player can join; "Back" just goes back.
    */
   public static Screen blockedScreen(Minecraft client, Screen parent) {
      return blockedScreen(client, parent, () -> parent);
   }

   /** As {@link #blockedScreen(Minecraft, Screen)}, with a different screen to continue on once Gemini was switched off. */
   public static Screen blockedScreen(Minecraft client, Screen back, Supplier<Screen> afterSwitchOff) {
      return new ConfirmScreen(
         switchOff -> {
            if (switchOff) {
               SingleplayerGate.Change change = SingleplayerGate.setSandboxMode(false);
               toast(client, "Gemini switched off", change.changed() ? "Online play is allowed. Switch it back on from the title screen." : "Nothing changed.");
               client.gui.setScreen(afterSwitchOff.get());
            } else {
               client.gui.setScreen(back);
            }
         },
         Component.literal(SingleplayerGate.BLOCKED_TITLE),
         Component.literal(SingleplayerGate.BLOCKED_BODY),
         Component.literal("Switch Gemini off"),
         CommonComponents.GUI_BACK
      );
   }

   public static void showBlocked(Minecraft client, Screen parent) {
      client.gui.setScreen(blockedScreen(client, parent));
   }

   /** Fabric's after-init screen hook. */
   public static void onScreenInit(Minecraft client, Screen screen) {
      if (screen instanceof TitleScreen) {
         if (GeminiTitleScreen.replaces(screen)) {
            // Normally TitleScreenMixin has swapped the screen before it ever got this far. Should that injection
            // miss, swap here once the current screen change has finished.
            client.execute(() -> {
               if (client.gui.screen() == screen && GeminiTitleScreen.replaces(screen)) {
                  client.gui.setScreen(new GeminiTitleScreen());
               }
            });
         } else {
            addTitleButton(client, screen);
            addHomeButton(client, screen);
         }
      } else if (screen instanceof PauseScreen pause) {
         PauseDock.attach(client, pause);
      } else if (screen instanceof JoinMultiplayerScreen && SingleplayerGate.connectionsBlocked()) {
         // Showing a server list in which nothing can be pinged or joined would look like a network fault.
         // Replace it with the explanation once the current screen change has finished.
         client.execute(() -> {
            if (client.gui.screen() == screen && SingleplayerGate.connectionsBlocked()) {
               // "Back" returns to the title screen; switching Gemini off carries on to the server list that was asked for.
               client.gui.setScreen(blockedScreen(client, new TitleScreen(), () -> new JoinMultiplayerScreen(new TitleScreen())));
            }
         });
      }
   }

   private static void addTitleButton(Minecraft client, Screen screen) {
      Button button = Button.builder(label(), pressed -> {
            toggleSandbox(client);
            pressed.setMessage(label());
            pressed.setTooltip(Tooltip.create(tooltip()));
         })
         .bounds(4, 4, BUTTON_WIDTH, 20)
         .tooltip(Tooltip.create(tooltip()))
         .build();
      Screens.getWidgets(screen).add(button);
   }

   /** The way back from the vanilla title screen to the Gemini home screen; pressing it also switches the option back on. */
   private static void addHomeButton(Minecraft client, Screen screen) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || gemini.config() == null) {
         return;
      }

      Button button = Button.builder(Component.translatable("gemini.title.home"), pressed -> {
            gemini.config().setGeminiTitleScreen(true);
            client.gui.setScreen(new GeminiTitleScreen());
         })
         .bounds(4, 28, BUTTON_WIDTH, 20)
         .tooltip(Tooltip.create(Component.translatable("gemini.title.home.tooltip")))
         .build();
      Screens.getWidgets(screen).add(button);
   }

   /** Flips the sandbox switch on the player's request and says what happened in a toast. */
   public static SingleplayerGate.Change toggleSandbox(Minecraft client) {
      SingleplayerGate.Change change = SingleplayerGate.setSandboxMode(!SingleplayerGate.sandboxMode());
      toast(client, SingleplayerGate.sandboxMode() ? "Gemini sandbox mode on" : "Gemini switched off", strip(change.message()));
      return change;
   }

   /**
    * A short notice from a Gemini screen. Gemini's own HUD toasts are only drawn in a world and sit behind (and
    * are blurred by) an open screen, so while a screen is up or no world is loaded this uses {@link GlassToast},
    * which is drawn over screens.
    */
   public static void notify(Minecraft client, String title, String body, HudOverlay.Severity severity) {
      if (client.player != null && client.gui.screen() == null) {
         HudOverlay.toast(title, body, severity);
      } else {
         GlassToast.show(client, title, body, severity == null ? Glass.INFO : severity.color);
      }
   }

   public static Component label() {
      return Component.literal(SingleplayerGate.sandboxMode() ? "Gemini: Sandbox (offline)" : "Gemini: Off (online play)");
   }

   public static Component tooltip() {
      return Component.literal(
         SingleplayerGate.sandboxMode()
            ? "Gemini is active in single-player worlds and server connections are refused. Click to switch Gemini off and play online."
            : "Gemini is switched off: no modules, HUD or commands, and you can join servers. Click to switch it back on for single-player."
      );
   }

   public static void toast(Minecraft client, String title, String body) {
      GlassToast.show(client, title, body, Glass.accent());
   }

   private static String strip(String formatted) {
      return formatted == null ? "" : formatted.replaceAll("§.", "");
   }
}
