package gemini.ui;

import gemini.util.SingleplayerGate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Everything the player sees about the sandbox switch: the screen shown instead of a refused connection, the
 * notice that replaces the server list while the sandbox is on, and the on/off button on the title screen.
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
         addTitleButton(client, screen);
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
            SingleplayerGate.Change change = SingleplayerGate.setSandboxMode(!SingleplayerGate.sandboxMode());
            pressed.setMessage(label());
            pressed.setTooltip(Tooltip.create(tooltip()));
            toast(client, SingleplayerGate.sandboxMode() ? "Gemini sandbox mode on" : "Gemini switched off", strip(change.message()));
         })
         .bounds(4, 4, BUTTON_WIDTH, 20)
         .tooltip(Tooltip.create(tooltip()))
         .build();
      Screens.getWidgets(screen).add(button);
   }

   private static Component label() {
      return Component.literal(SingleplayerGate.sandboxMode() ? "Gemini: Sandbox (offline)" : "Gemini: Off (online play)");
   }

   private static Component tooltip() {
      return Component.literal(
         SingleplayerGate.sandboxMode()
            ? "Gemini is active in single-player worlds and server connections are refused. Click to switch Gemini off and play online."
            : "Gemini is switched off: no modules, HUD or commands, and you can join servers. Click to switch it back on for single-player."
      );
   }

   private static void toast(Minecraft client, String title, String body) {
      SystemToast.addOrUpdate(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title), Component.literal(body));
   }

   private static String strip(String formatted) {
      return formatted == null ? "" : formatted.replaceAll("§.", "");
   }
}
