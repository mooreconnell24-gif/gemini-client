package gemini.command;

import gemini.config.ConfigManager;
import gemini.module.ModuleManager;
import gemini.module.SelfTest;
import gemini.module.modules.world.Home;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.Scheduler;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import gemini.util.VersionBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec3;

final class UtilityCommands {
   private static final double MAX_CLIP = 400.0;
   private final ModuleManager modules;
   private final ConfigManager config;

   UtilityCommands(ModuleManager modules, ConfigManager config) {
      this.modules = modules;
      this.config = config;
   }

   boolean run(String command, String[] parts, String rest) {
      switch (command) {
         case "vclip":
            this.clip(parts, true);
            break;
         case "hclip":
            this.clip(parts, false);
            break;
         case "dismount":
            this.dismount();
            break;
         case "drop":
            this.drop(parts);
            break;
         case "leave":
         case "disconnect":
         case "dc":
            this.disconnect(rest);
            break;
         case "say":
            this.say(rest);
            break;
         case "rotate":
            this.rotate(parts);
            break;
         case "coords":
            this.coords();
            break;
         case "sethome":
            this.home("set");
            break;
         case "home":
            this.home("go");
            break;
         case "back":
            this.home("back");
            break;
         case "gm":
         case "gamemode":
            this.gamemode(parts);
            break;
         case "day":
            this.world("time set day", "Time set to day.");
            break;
         case "noon":
            this.world("time set noon", "Time set to noon.");
            break;
         case "night":
            this.world("time set night", "Time set to night.");
            break;
         case "sun":
            this.world("weather clear", "Weather cleared.");
            break;
         case "rain":
            this.world("weather rain", "Weather set to rain.");
            break;
         case "heal":
            this.world("effect give @s minecraft:instant_health 1 20 true", "Healed.");
            break;
         case "feed":
            this.world("effect give @s minecraft:saturation 1 1 true", "Fed.");
            break;
         case "clear":
            this.clearChat();
            break;
         case "reload":
            this.reload();
            break;
         case "selftest":
            this.selfTest(parts);
            break;
         default:
            return false;
      }

      return true;
   }

   private static void feedback(String message) {
      CommandManager.feedback(message);
   }

   private static LocalPlayer usablePlayer() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null) {
         feedback("§cJoin a world first.");
         return null;
      } else if (!SingleplayerGate.allowed()) {
         feedback(SingleplayerGate.denialMessage());
         return null;
      } else {
         return client.player;
      }
   }

   private static Double number(String[] parts, int index, String usage) {
      if (parts.length <= index) {
         feedback("§cUsage: §f" + usage);
         return null;
      } else {
         try {
            double value = Double.parseDouble(parts[index]);
            if (!Double.isNaN(value) && !Double.isInfinite(value)) {
               return value;
            } else {
               throw new NumberFormatException();
            }
         } catch (NumberFormatException var51) {
            feedback("§f" + parts[index] + " §cis not a number. Usage: §f" + usage);
            return null;
         }
      }
   }

   private void clip(String[] parts, boolean vertical) {
      String usage = vertical ? ".vclip <blocks>" : ".hclip <blocks>";
      Double requested = number(parts, 1, usage);
      if (requested != null) {
         LocalPlayer player = usablePlayer();
         if (player != null) {
            Minecraft client = Minecraft.getInstance();
            double amount = Mth.clamp(requested, -400.0, 400.0);
            Vec3 destination;
            if (vertical) {
               double y = Mth.clamp(player.getY() + amount, VersionBridge.minWorldY(client.level), VersionBridge.maxWorldY(client.level));
               destination = new Vec3(player.getX(), y, player.getZ());
            } else {
               double yaw = Math.toRadians(player.getYRot());
               destination = player.position().add(-Math.sin(yaw) * amount, 0.0, Math.cos(yaw) * amount);
            }

            MoveUtil.teleport(client, player, destination, true);
            feedback(
               String.format(
                  Locale.ROOT,
                  "§aMoved §f%.1f §ablocks %s.",
                  Math.abs(amount),
                  vertical ? (amount >= 0.0 ? "up" : "down") : (amount >= 0.0 ? "forward" : "back")
               )
            );
         }
      }
   }

   private void dismount() {
      LocalPlayer player = usablePlayer();
      if (player != null) {
         ClientPacketListener connection = Minecraft.getInstance().getConnection();
         if (player.getVehicle() != null && connection != null) {
            Input held = player.input != null ? player.input.keyPresses : Input.EMPTY;
            connection.send(
               new ServerboundPlayerInputPacket(new Input(held.forward(), held.backward(), held.left(), held.right(), held.jump(), true, held.sprint()))
            );
            Scheduler.later(2, () -> {
               Minecraft client = Minecraft.getInstance();
               if (client.player != null && client.getConnection() != null) {
                  client.getConnection().send(new ServerboundPlayerInputPacket(client.player.input != null ? client.player.input.keyPresses : Input.EMPTY));
               }
            });
            feedback("§aDismounting.");
         } else {
            feedback("§cYou are not riding anything.");
         }
      }
   }

   private void drop(String[] parts) {
      LocalPlayer player = usablePlayer();
      if (player != null) {
         String what = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "hand";
         List<Integer> slots = new ArrayList<>();
         switch (what) {
            case "hand":
               player.drop(true);
               feedback("§aDropped the stack in your hand.");
               return;
            case "offhand":
               collect(player, slots, 0, 0, true);
               break;
            case "hotbar":
               collect(player, slots, 0, 9, false);
               break;
            case "inventory":
               collect(player, slots, 9, 36, false);
               break;
            case "all":
               collect(player, slots, 0, 36, true);
               break;
            default:
               feedback("§cUsage: §f.drop hand|offhand|hotbar|inventory|all");
               return;
         }

         if (slots.isEmpty()) {
            feedback("§7Nothing to drop there.");
         } else {
            int delay = 0;

            for (int slot : slots) {
               Scheduler.later(delay++, () -> throwStack(slot));
            }

            feedback("§aDropping §f" + slots.size() + " §astacks.");
         }
      }
   }

   private static void collect(LocalPlayer player, List<Integer> slots, int from, int to, boolean offhand) {
      for (int index = from; index < to; index++) {
         if (!player.getInventory().getItem(index).isEmpty()) {
            slots.add(Inventories.menuSlot(index));
         }
      }

      if (offhand && !player.getOffhandItem().isEmpty()) {
         slots.add(45);
      }
   }

   private static void throwStack(int menuSlot) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null) {
         client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, player);
      }
   }

   private void disconnect(String reason) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null) {
         feedback("§cYou are not in a world.");
      } else {
         client.level.disconnect(Component.literal(reason.isEmpty() ? "[Gemini] Left the world by command" : reason));
      }
   }

   private void say(String text) {
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      if (connection == null) {
         feedback("§cYou are not connected to a world.");
      } else if (text.isEmpty()) {
         feedback("§cUsage: §f.say <text>");
      } else {
         CommandManager.sendRaw(() -> {
            if (text.startsWith("/")) {
               connection.sendCommand(text.substring(1));
            } else {
               connection.sendChat(text);
            }
         });
      }
   }

   private void rotate(String[] parts) {
      String usage = ".rotate <yaw> [pitch]";
      Double yaw = number(parts, 1, usage);
      if (yaw != null) {
         LocalPlayer player = usablePlayer();
         if (player != null) {
            double pitch = player.getXRot();
            if (parts.length > 2) {
               Double given = number(parts, 2, usage);
               if (given == null) {
                  return;
               }

               pitch = given;
            }

            float newYaw = Mth.wrapDegrees(yaw.floatValue());
            float newPitch = Mth.clamp((float)pitch, -90.0F, 90.0F);
            player.setYRot(newYaw);
            player.setXRot(newPitch);
            player.yRotO = newYaw;
            player.xRotO = newPitch;
            feedback(String.format(Locale.ROOT, "§aFacing yaw §f%.1f §apitch §f%.1f", newYaw, newPitch));
         }
      }
   }

   private void home(String action) {
      Home home = Home.get();
      if (home == null) {
         feedback("§cHome is not available.");
      } else {
         switch (action) {
            case "set":
               home.setHere();
               break;
            case "back":
               home.back();
               break;
            default:
               home.go();
         }
      }
   }

   private void gamemode(String[] parts) {
      if (parts.length < 2) {
         feedback("§cUsage: §f.gm survival|creative|adventure|spectator");
      } else if (SpCommands.gamemode(parts[1])) {
         feedback("§bGamemode §7" + parts[1].toLowerCase(Locale.ROOT) + ".");
      } else if (!SingleplayerGate.allowed()) {
         feedback(SingleplayerGate.denialMessage());
      } else {
         feedback("§cUsage: §f.gm survival|creative|adventure|spectator");
      }
   }

   private void world(String command, String done) {
      if (usablePlayer() != null) {
         if (SpCommands.run(command)) {
            feedback("§bGemini §7" + done);
         } else {
            feedback(SingleplayerGate.denialMessage());
         }
      }
   }

   private void coords() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null) {
         feedback("§cJoin a world first.");
      } else {
         String here = String.format(Locale.ROOT, "%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ());
         client.keyboardHandler.setClipboard(here);
         String dimension = player.level().dimension().identifier().getPath();
         feedback("§bCoordinates §f" + here + " §7in " + dimension + " (copied)");
         if (dimension.equals("overworld")) {
            feedback(String.format(Locale.ROOT, "§7  nether equivalent §f%.0f %.0f", player.getX() / 8.0, player.getZ() / 8.0));
         } else if (dimension.equals("the_nether")) {
            feedback(String.format(Locale.ROOT, "§7  overworld equivalent §f%.0f %.0f", player.getX() * 8.0, player.getZ() * 8.0));
         }
      }
   }

   private void clearChat() {
      Minecraft client = Minecraft.getInstance();
      if (client.gui != null && client.gui.hud != null) {
         client.gui.hud.getChat().clearMessages(false);
      }
   }

   private void reload() {
      this.config.load();
      feedback("§aConfig reloaded from disk.");
   }

   private void selfTest(String[] parts) {
      if (parts.length > 1 && parts[1].equalsIgnoreCase("run")) {
         SelfTest.start(this.modules);
      } else {
         feedback("§bSelf-test §7turns on every module for a few seconds, reports any that crash, and restores your setup.");
         feedback("§7It places blocks and uses items near you, so run it in a throwaway world: §f.selftest run");
      }
   }
}
