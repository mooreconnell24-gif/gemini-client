package gemini.util;

import gemini.Gemini;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * The one place that decides where Gemini may run.
 *
 * <p>Gemini is a single-player sandbox. That is enforced by a single switch, {@code sandboxMode} in
 * {@code gemini_config.json} (on by default), with two states:
 *
 * <ul>
 * <li><b>Sandbox on</b>: modules, HUD, commands and the bot are available in private single-player worlds, and
 * connections to servers and Realms are refused with a screen that says why and offers the way out.</li>
 * <li><b>Sandbox off</b>: Gemini is switched off completely (no modules, no HUD, no commands, no bot), and the
 * game can go online like an unmodded client.</li>
 * </ul>
 *
 * <p>There is deliberately no state in which modules run while connected to a server: {@link #allowed()} needs
 * the sandbox to be on <em>and</em> a private (not opened to LAN) integrated server, and every module, command and
 * hook goes through it. The switch cannot be turned on while connected, so the two states never overlap.
 *
 * <p>The mixins in {@code gemini.mixin.lock} ask {@link #connectionsBlocked()} and hand the explanation to
 * {@link gemini.ui.SandboxUi}; {@link #tick} is the last line of defence should a remote level ever appear while
 * the sandbox is on.
 */
public final class SingleplayerGate {
   public static final String BLOCKED_TITLE = "Gemini sandbox mode is on";
   public static final String BLOCKED_BODY = "Gemini only runs in single-player worlds, so this connection was not opened.\n\n"
      + "To play online, switch Gemini off. Its modules, HUD and commands stay disabled until you switch it back on "
      + "from the title screen or with .sandbox on.";
   /** One line for logs and exception messages. */
   public static final String BLOCKED_REASON = "Gemini sandbox mode is on: server connections are refused. Switch Gemini off (.sandbox off or the title screen button) to play online.";
   private static volatile boolean sandboxMode = true;
   private static boolean dormantHintShown;

   private SingleplayerGate() {
   }

   /** True while Gemini is active and confined to single-player. False means Gemini is switched off. */
   public static boolean sandboxMode() {
      return sandboxMode;
   }

   /** Applies the value read from the config file, without feedback. */
   public static void loadSandboxMode(boolean on) {
      // A config reload must not arm the sandbox while a server session is open.
      sandboxMode = on && !connectedToServer(Minecraft.getInstance());
   }

   /**
    * Flips the switch on request of the player. Turning the sandbox on is refused while connected to a server;
    * the returned message says what happened either way and is written for the player.
    */
   public static Change setSandboxMode(boolean on) {
      Minecraft client = Minecraft.getInstance();
      if (on == sandboxMode) {
         return new Change(false, on ? "§bGemini §7sandbox mode is already on." : "§bGemini §7is already switched off.");
      }

      if (on && connectedToServer(client)) {
         return new Change(false, "§cGemini §7can't be switched on while you are connected to a server. Leave the server first.");
      }

      sandboxMode = on;
      dormantHintShown = false;
      Gemini gemini = Gemini.getInstance();
      if (gemini != null) {
         if (!on && gemini.modules() != null) {
            gemini.modules().suspendAll();
         }

         if (gemini.config() != null) {
            gemini.config().save();
            gemini.config().flush();
         }
      }

      Gemini.LOGGER.info("Sandbox mode switched {}", on ? "on: Gemini is active, server connections are refused" : "off: Gemini is dormant, online play is allowed");
      return new Change(
         true,
         on
            ? "§bGemini §7sandbox mode §aon§7: modules are available in single-player and server connections are refused."
            : "§bGemini §7switched §coff§7: you can play online; modules, HUD and commands are disabled until §f.sandbox on§7."
      );
   }

   /** Whether opening a connection to a server or Realm must be refused right now. */
   public static boolean connectionsBlocked() {
      return sandboxMode;
   }

   /** True when a world served by anything other than this client's own integrated server is loaded or loading. */
   public static boolean connectedToServer(Minecraft client) {
      return client != null && !client.hasSingleplayerServer() && (client.level != null || client.getConnection() != null);
   }

   /** Last line of defence: a remote level while the sandbox is on is closed with the same explanation the connect screens give. */
   public static void tick(Minecraft client) {
      if (client == null) {
         return;
      }

      if (sandboxMode && client.level != null && !client.hasSingleplayerServer()) {
         Gemini.LOGGER.warn("A server session was open while sandbox mode is on; closing it");
         client.level.disconnect(Component.literal(BLOCKED_TITLE + ". " + BLOCKED_BODY.replace("\n\n", " ")));
      } else if (!sandboxMode && !dormantHintShown && client.player != null && client.hasSingleplayerServer()) {
         // Dormant in a single-player world: say so once, or the missing menu looks like a bug.
         dormantHintShown = true;
         client.gui.hud.getChat().addClientSystemMessage(Component.literal(denialMessage()));
      }
   }

   /** True when Gemini may act: the sandbox is on and a private (not opened to LAN) single-player world is loaded. */
   public static boolean allowed() {
      if (!sandboxMode) {
         return false;
      }

      Minecraft client = Minecraft.getInstance();
      return client != null && client.player != null && client.level != null && isPrivateSingleplayer(client);
   }

   public static boolean isPrivateSingleplayer(Minecraft client) {
      if (!client.hasSingleplayerServer()) {
         return false;
      }

      IntegratedServer server = client.getSingleplayerServer();
      return server != null && !server.isPublished();
   }

   public static boolean singleplayerOnly() {
      Minecraft client = Minecraft.getInstance();
      return client != null && client.hasSingleplayerServer();
   }

   /** Why Gemini is not acting right now, for chat. */
   public static String denialMessage() {
      if (!sandboxMode) {
         return "§bGemini Client §7is switched off (sandbox mode is off). Type §f.sandbox on §7in a single-player world to use it.";
      }

      Minecraft client = Minecraft.getInstance();
      if (client == null || client.player == null || client.level == null) {
         return "§bGemini Client §7needs a single-player world loaded.";
      }

      return client.hasSingleplayerServer()
         ? "§bGemini Client §7is paused while this world is open to other players."
         : "§bGemini Client §7only runs in single-player worlds.";
   }

   /** The outcome of a switch request. */
   public record Change(boolean changed, String message) {
   }
}
