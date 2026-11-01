package gemini.command;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import gemini.Gemini;
import gemini.config.ConfigManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.ui.clickgui.ClickGuiScreen;
import gemini.util.BaritoneBridge;
import gemini.util.BuiltInBaritone;
import gemini.util.SingleplayerGate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class CommandManager {
   private static final int LIST_PREVIEW = 40;
   private static volatile boolean rawSending;
   private final ModuleManager moduleManager;
   private final ConfigManager configManager;
   private final UtilityCommands utilities;

   public CommandManager(ModuleManager moduleManager, ConfigManager configManager) {
      this.moduleManager = moduleManager;
      this.configManager = configManager;
      this.utilities = new UtilityCommands(moduleManager, configManager);
   }

   public static boolean rawSending() {
      return rawSending;
   }

   static void sendRaw(Runnable send) {
      rawSending = true;

      try {
         send.run();
      } finally {
         rawSending = false;
      }
   }

   public static void sendRaw(String line) {
      Minecraft client = Minecraft.getInstance();
      if (line != null && !line.isBlank() && client.getConnection() != null) {
         String trimmed = line.length() > 256 ? line.substring(0, 256) : line;
         sendRaw(() -> {
            if (trimmed.startsWith("/")) {
               client.getConnection().sendCommand(trimmed.substring(1));
            } else {
               client.getConnection().sendChat(trimmed);
            }
         });
      }
   }

   public boolean handle(String rawMessage) {
      if (rawMessage != null && (rawMessage.startsWith(".") || rawMessage.startsWith("#"))) {
         boolean hashPrefix = rawMessage.startsWith("#");
         String trimmed = rawMessage.substring(1).trim();
         if (trimmed.isEmpty()) {
            return true;
         } else {
            String[] parts = trimmed.split("\\s+");
            String command = parts[0].toLowerCase(Locale.ROOT);
            if (hashPrefix) {
               this.handleBaritoneCommand(parts);
               return true;
            } else {
               try {
                  switch (command) {
                     case "help":
                     case "?":
                        this.sendHelp();
                        break;
                     case "b":
                     case "baritone":
                        this.handleBaritoneCommand(Arrays.copyOfRange(parts, 1, parts.length));
                        break;
                     case "bind":
                        this.handleBind(parts);
                        break;
                     case "unbind":
                        this.handleUnbind(parts);
                        break;
                     case "toggle":
                     case "t":
                        this.handleToggle(parts);
                        break;
                     case "modules":
                        this.listModules(parts);
                        break;
                     case "set":
                        this.handleSet(parts);
                        break;
                     case "get":
                     case "settings":
                        this.handleGet(parts);
                        break;
                     case "reset":
                        this.handleReset(parts);
                        break;
                     case "panic":
                        this.moduleManager.panic();
                        feedback("§bPanic §7all modules disabled.");
                        break;
                     case "config":
                     case "cfg":
                        this.handleConfig(parts);
                        break;
                     case "gui":
                     case "menu":
                        this.openGui();
                        break;
                     default:
                        String rest = trimmed.substring(parts[0].length()).trim();
                        if (!this.utilities.run(command, parts, rest) && !this.handleModuleCommand(command, parts)) {
                           feedback("§cUnknown command. Type §f.help §cor §f#help §cfor a list.");
                        }
                  }
               } catch (RuntimeException var9) {
                  Gemini.LOGGER.error("Command failed: {}", rawMessage, var9);
                  feedback("§cThat command failed: " + var9.getClass().getSimpleName());
               }

               return true;
            }
         }
      } else {
         return false;
      }
   }

   private void handleBaritoneCommand(String[] args) {
      if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null && client.level != null) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
               case "stop":
               case "cancel":
                  BuiltInBaritone.stop();
                  feedback("§bBaritone §7stopped all tasks.");
                  break;
               case "pause":
                  BaritoneBridge.pause("command");
                  feedback("§bBaritone §7paused.");
                  break;
               case "resume":
                  BaritoneBridge.resume("command");
                  BaritoneBridge.resume("manual");
                  feedback("§bBaritone §7resumed.");
                  break;
               case "status":
                  feedback("§bBaritone §7status: §f" + BuiltInBaritone.statusSummary());
                  break;
               case "goto":
                  if (args.length == 3) {
                     try {
                        int x = Integer.parseInt(args[1]);
                        int z = Integer.parseInt(args[2]);
                        int y = BuiltInBaritone.findSurfaceY(client.level, x, z, player.getBlockY());
                        BuiltInBaritone.gotoBlock(new BlockPos(x, y, z));
                        feedback("§bBaritone §7pathing to §f" + x + " " + y + " " + z + "§7.");
                     } catch (NumberFormatException var15) {
                        feedback("§cUsage: §f#goto <x> [y] <z>");
                     }
                  } else if (args.length >= 4) {
                     try {
                        int x = Integer.parseInt(args[1]);
                        int y = Integer.parseInt(args[2]);
                        int z = Integer.parseInt(args[3]);
                        BuiltInBaritone.gotoBlock(new BlockPos(x, y, z));
                        feedback("§bBaritone §7pathing to §f" + x + " " + y + " " + z + "§7.");
                     } catch (NumberFormatException var14) {
                        feedback("§cUsage: §f#goto <x> <y> <z>");
                     }
                  } else {
                     feedback("§cUsage: §f#goto <x> [y] <z>");
                  }
                  break;
               case "mine":
                  if (args.length < 2) {
                     feedback("§cUsage: §f#mine <block1> [block2...] §7(e.g. §f#mine diamond_ore§7)");
                  } else {
                     String[] names = Arrays.copyOfRange(args, 1, args.length);
                     if (BuiltInBaritone.mineByNames(names)) {
                        feedback("§bBaritone §7mining §f" + String.join(", ", names) + "§7.");
                     } else {
                        feedback("§cNo matching blocks found for: §f" + String.join(", ", names));
                     }
                  }
                  break;
               case "follow":
                  if (args.length >= 2 && !args[1].equalsIgnoreCase("players") && !args[1].equalsIgnoreCase("mobs")) {
                     String targetName = args[args.length - 1];
                     BuiltInBaritone.follow(e -> e.getName().getString().equalsIgnoreCase(targetName));
                     feedback("§bBaritone §7following §f" + targetName + "§7.");
                  } else {
                     boolean onlyPlayers = args.length < 2 || !args[1].equalsIgnoreCase("mobs");
                     BuiltInBaritone.follow(e -> onlyPlayers ? e instanceof Player : true);
                     feedback("§bBaritone §7following nearest " + (onlyPlayers ? "player" : "entity") + ".");
                  }
                  break;
               case "tunnel":
                  int len = 64;
                  if (args.length >= 2) {
                     try {
                        len = Integer.parseInt(args[1]);
                     } catch (NumberFormatException var13) {
                     }
                  }

                  BuiltInBaritone.startTunnel(player.getDirection(), len);
                  feedback("§bBaritone §7tunneling §f" + len + "§7 blocks " + player.getDirection().getName() + ".");
                  break;
               case "farm":
                  BuiltInBaritone.startFarm();
                  feedback("§bBaritone §7farming ripe crops nearby.");
                  break;
               case "explore":
                  BuiltInBaritone.startExplore(player.blockPosition());
                  feedback("§bBaritone §7exploring outward from current position.");
                  break;
               case "excavate":
                  int r = 4;
                  int d = 3;
                  if (args.length >= 2) {
                     try {
                        r = Integer.parseInt(args[1]);
                     } catch (NumberFormatException var12) {
                     }
                  }

                  if (args.length >= 3) {
                     try {
                        d = Integer.parseInt(args[2]);
                     } catch (NumberFormatException var11) {
                     }
                  }

                  BlockPos p = player.blockPosition();
                  BuiltInBaritone.startExcavate(p.offset(-r, -d, -r), p.offset(r, 2, r));
                  feedback("§bBaritone §7excavating radius §f" + r + "§7, depth §f" + d + "§7.");
                  break;
               default:
                  feedback("§cUnknown Baritone command. Type §f#help §cfor a list.");
            }
         } else {
            feedback("§cJoin a world first.");
         }
      } else {
         feedback("§bBuilt-In Baritone §7commands (use §f#<cmd> §7or §f.b <cmd>§7):");
         feedback("§f#goto <x> [y] <z> §7- A* pathfind to coordinates");
         feedback("§f#mine <block1> [block2...] §7- scan & mine target ores/blocks");
         feedback("§f#follow [playerName|players|mobs] §7- follow players or entities");
         feedback("§f#tunnel [length] §7- dig a 1x2 tunnel ahead");
         feedback("§f#farm §7- harvest ripe crops nearby");
         feedback("§f#explore §7- spiral outward exploring chunks");
         feedback("§f#excavate [radius] [depth] §7- clear an area around you");
         feedback("§f#pause §7/ §f#resume §7/ §f#stop §7/ §f#status");
      }
   }

   private void sendHelp() {
      feedback("§bGemini Client §7commands:");
      feedback("§f#<cmd> §7or §f.b <cmd> §7- Built-in Baritone (§f#goto§7, §f#mine§7, §f#follow§7, §f#tunnel§7, §f#farm§7, §f#explore§7, §f#stop§7)");
      feedback("§f.toggle <module> [on|off] §7- toggle a module (or just §f.<module>§7)");
      feedback("§f.set <module> <setting> <value> §7- change a setting (§f_ §7stands for a space)");
      feedback("§f.set <module> <list> add|remove|toggle|set|clear|list <items> §7- edit block, item and entity lists");
      feedback("§f.get <module> [setting] §7- inspect a module");
      feedback("§f.reset §7- restore every setting  §f.reset <module> [setting] §7- restore one");
      feedback("§f.<module> <setting> [value] §7- shortcut for get and set");
      feedback("§f.bind <module> <key> §7/ §f.unbind <module> §7- keybinds");
      feedback("§f.modules [category] §7- list modules");
      feedback("§f.config save|load|list|reset <name> §7/ §f.reload §7- profiles and the config file");
      feedback("§f.vclip <blocks> §7/ §f.hclip <blocks> §7/ §f.rotate <yaw> [pitch] §7- move and aim");
      feedback("§f.drop hand|offhand|hotbar|inventory|all §7/ §f.dismount §7- inventory and mounts");
      feedback("§f.say <text> §7/ §f.leave [reason] §7/ §f.coords §7/ §f.clear");
      feedback("§f.sethome §7/ §f.home §7/ §f.back §7- saved teleport in this world");
      feedback("§f.gm survival|creative|adventure|spectator §7/ §f.day §7/ §f.night §7/ §f.sun §7/ §f.rain §7/ §f.heal §7/ §f.feed");
      feedback("§f.selftest §7- crash-test every module in a throwaway world");
      feedback("§f.panic §7- turn every module off (also the Panic module)  §f.gui §7- open the menu");
      feedback("§7Module commands: §f.xray reload§7, §f.marker add|remove|list|clear");
   }

   private Optional<Module> module(String name) {
      Optional<Module> found = this.moduleManager.getByName(name);
      if (found.isEmpty()) {
         feedback("§cModule not found: §f" + name);
      }

      return found;
   }

   private boolean handleModuleCommand(String name, String[] parts) {
      Optional<Module> found = this.moduleManager.getByName(name);
      if (found.isEmpty()) {
         return false;
      } else {
         Module module = found.get();
         String[] args = Arrays.copyOfRange(parts, 1, parts.length);
         if (module.onCommand(args)) {
            return true;
         } else if (args.length == 0) {
            this.handleToggle(new String[]{"toggle", name});
            return true;
         } else {
            Optional<Setting<?>> setting = findSetting(module, args[0]);
            if (setting.isEmpty() && args.length == 1 && isSwitchWord(args[0])) {
               this.handleToggle(new String[]{"toggle", name, args[0]});
               return true;
            } else {
               if (setting.isEmpty() && isListVerb(args[0])) {
                  ListSetting only = soleList(module);
                  if (only != null) {
                     this.applyValue(module, only, args);
                     return true;
                  }
               }

               if (setting.isEmpty()) {
                  feedback("§f" + module.getName() + " §chas no command or setting called §f" + args[0] + "§c. Try §f.get " + name.toLowerCase(Locale.ROOT));
                  return true;
               } else if (args.length == 1) {
                  if (setting.get() instanceof ActionSetting action) {
                     action.run();
                     return true;
                  } else if (setting.get() instanceof BooleanSetting bool) {
                     bool.toggle();
                     this.configManager.save();
                     feedback("§b" + module.getName() + " §7" + bool.name() + " §f= " + display(bool));
                     return true;
                  } else {
                     this.showSetting(setting.get());
                     return true;
                  }
               } else {
                  this.applyValue(module, setting.get(), Arrays.copyOfRange(args, 1, args.length));
                  return true;
               }
            }
         }
      }
   }

   private static boolean isSwitchWord(String word) {
      String var1 = word.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "on", "off", "true", "false", "enable", "disable" -> true;
         default -> false;
      };
   }

   private static boolean isListVerb(String word) {
      String var1 = word.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "add", "remove", "del", "delete", "toggle", "clear", "list", "set" -> true;
         default -> false;
      };
   }

   private static ListSetting soleList(Module module) {
      ListSetting found = null;

      for (Setting<?> setting : module.settings()) {
         if (setting instanceof ListSetting list) {
            if (found != null) {
               return null;
            }

            found = list;
         }
      }

      return found;
   }

   private static Optional<Setting<?>> findSetting(Module module, String query) {
      String clean = query.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");

      for (Setting<?> setting : module.settings()) {
         String key = setting.name().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
         if (key.equals(clean)) {
            return Optional.of(setting);
         }
      }

      for (Setting<?> settingx : module.settings()) {
         String key = settingx.name().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
         if (key.startsWith(clean)) {
            return Optional.of(settingx);
         }
      }

      return Optional.empty();
   }

   private Optional<Setting<?>> setting(Module module, String query) {
      Optional<Setting<?>> found = findSetting(module, query);
      if (found.isEmpty()) {
         feedback("§f" + module.getName() + " §chas no setting called §f" + query + "§c. Try §f.get " + module.getName().toLowerCase(Locale.ROOT));
      }

      return found;
   }

   private void showSetting(Setting<?> setting) {
      feedback("§b" + setting.name() + " §f= " + display(setting) + " §7(" + describeRange(setting) + ")");
      feedback("§7  " + setting.description());
   }

   private void handleToggle(String[] parts) {
      if (parts.length < 2) {
         feedback("§cUsage: §f.toggle <module> [on|off]");
      } else {
         this.module(parts[1]).ifPresent(module -> {
            if (parts.length >= 3) {
               String state = parts[2].toLowerCase(Locale.ROOT);
               if (state.equals("on") || state.equals("true") || state.equals("enable")) {
                  module.setEnabled(true);
                  return;
               }

               if (state.equals("off") || state.equals("false") || state.equals("disable")) {
                  module.setEnabled(false);
                  return;
               }
            }

            module.toggle();
         });
      }
   }

   private void handleBind(String[] parts) {
      if (parts.length < 3) {
         feedback("§cUsage: §f.bind <module> <key>");
      } else {
         this.module(parts[1]).ifPresent(module -> {
            if (!module.isBindable()) {
               feedback("§f" + module.getName() + " §ccannot be bound.");
            } else if (isUnbindWord(parts[2])) {
               module.setKeybind(-1);
               feedback("§b" + module.getName() + " §7unbound.");
            } else {
               int code = resolveKey(parts[2]);
               if (code == -1) {
                  feedback("§cUnknown key: §f" + parts[2]);
               } else {
                  module.setKeybind(code);
                  feedback("§b" + module.getName() + " §7bound to §f" + parts[2].toUpperCase(Locale.ROOT));
               }
            }
         });
      }
   }

   private void handleUnbind(String[] parts) {
      if (parts.length < 2) {
         feedback("§cUsage: §f.unbind <module>");
      } else {
         this.module(parts[1]).ifPresent(module -> {
            module.setKeybind(-1);
            feedback("§b" + module.getName() + " §7unbound.");
         });
      }
   }

   private void listModules(String[] parts) {
      if (parts.length >= 2) {
         String wanted = parts[1].toLowerCase(Locale.ROOT);

         for (Category category : Category.values()) {
            if (category.name().toLowerCase(Locale.ROOT).equals(wanted) || category.displayName().toLowerCase(Locale.ROOT).equals(wanted)) {
               this.printCategory(category);
               return;
            }
         }

         feedback("§cUnknown category: §f" + parts[1]);
      } else {
         for (Category categoryx : Category.values()) {
            this.printCategory(categoryx);
         }
      }
   }

   private void printCategory(Category category) {
      List<Module> list = this.moduleManager.of(category);
      List<String> names = new ArrayList<>(list.size());

      for (Module module : list) {
         names.add((module.isEnabled() ? "§a" : "§7") + module.getName());
      }

      feedback("§b" + category.displayName() + " §7(" + list.size() + "): " + String.join("§7, ", names));
   }

   private void handleSet(String[] parts) {
      if (parts.length < 4) {
         feedback("§cUsage: §f.set <module> <setting> <value>");
      } else {
         this.module(parts[1]).ifPresent(module -> this.setting(module, parts[2]).ifPresent(setting -> {
            String[] values = Arrays.copyOfRange(parts, 3, parts.length);
            this.applyValue(module, (Setting<?>)setting, values);
         }));
      }
   }

   private void applyValue(Module module, Setting<?> setting, String[] values) {
      if (setting instanceof ActionSetting action) {
         action.run();
      } else if (setting instanceof ListSetting list) {
         this.editList(module, list, values);
      } else {
         String raw = String.join(" ", values).replace('_', ' ');
         if (setting instanceof BooleanSetting bool) {
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.equals("true") || lower.equals("on") || lower.equals("yes") || lower.equals("1")) {
               bool.set(true);
            } else if (!lower.equals("false") && !lower.equals("off") && !lower.equals("no") && !lower.equals("0")) {
               if (!lower.equals("toggle")) {
                  feedback("§cExpected §fon §cor §foff§c.");
                  return;
               }

               bool.toggle();
            } else {
               bool.set(false);
            }
         } else if (setting instanceof NumberSetting number) {
            try {
               number.set(Double.parseDouble(raw));
            } catch (NumberFormatException var13) {
               feedback("§f" + raw + " §cis not a number (" + describeRange(setting) + ").");
               return;
            }
         } else if (setting instanceof ColorSetting color) {
            try {
               color.set(Integer.parseInt(raw));
            } catch (NumberFormatException var12) {
               feedback("§f" + raw + " §cis not a hue between 0 and 360.");
               return;
            }
         } else if (setting instanceof ModeSetting mode) {
            String match = mode.modes().stream().filter(candidate -> candidate.equalsIgnoreCase(raw.replace("_", " "))).findFirst().orElse(null);
            if (match == null) {
               feedback("§cUnknown mode §f" + raw + "§c. Choices: §f" + String.join("§7, §f", mode.modes()));
               return;
            }

            mode.set(match);
         }

         this.configManager.save();
         feedback("§b" + module.getName() + " §7" + setting.name() + " §f= " + display(setting));
      }
   }

   private void editList(Module module, ListSetting list, String[] args) {
      String verb = args[0].toLowerCase(Locale.ROOT);
      String[] items = Arrays.copyOfRange(args, 1, args.length);
      switch (verb) {
         case "list":
            List<String> all = list.get();
            if (all.isEmpty()) {
               feedback("§b" + module.getName() + " §7" + list.name() + " is empty.");
            } else {
               List<String> shown = all.size() > 40 ? all.subList(0, 40) : all;
               feedback(
                  "§b"
                     + module.getName()
                     + " §7"
                     + list.name()
                     + " ("
                     + all.size()
                     + "): §f"
                     + String.join("§7, §f", shown)
                     + (all.size() > 40 ? " §7... +" + (all.size() - 40) + " more" : "")
               );
            }

            return;
         case "clear":
            list.clear();
            this.configManager.save();
            feedback("§b" + module.getName() + " §7" + list.name() + " cleared.");
            return;
         case "reset":
            list.reset();
            this.configManager.save();
            feedback("§b" + module.getName() + " §7" + list.name() + " reset (" + list.size() + " entries).");
            return;
         case "add":
         case "remove":
         case "del":
         case "delete":
         case "toggle":
         case "set":
            if (items.length == 0) {
               feedback("§cUsage: §f.set " + module.getName() + " " + list.name().replace(' ', '_') + " " + verb + " <" + list.kind().noun() + "...>");
               return;
            } else {
               if (list.kind() == ListSetting.Kind.TEXT) {
                  String line = String.join(" ", items);
                  if (verb.equals("set")) {
                     list.set(List.of(line));
                  } else if (verb.equals("add")) {
                     list.add(line);
                  } else if (verb.equals("toggle")) {
                     if (!list.remove(line)) {
                        list.add(line);
                     }
                  } else {
                     list.remove(line);
                  }

                  this.configManager.save();
                  feedback("§b" + module.getName() + " §7" + list.name() + " now has §f" + list.size() + " §7entries.");
                  return;
               }

               if (verb.equals("set")) {
                  list.clear();
               }

               int changed = 0;

               for (String token : items) {
                  for (String piece : token.split(",")) {
                     String trimmed = piece.trim();
                     if (!trimmed.isEmpty()) {
                        if (!verb.equals("add") && !verb.equals("set")) {
                           if (verb.equals("toggle")) {
                              if (list.contains(trimmed)) {
                                 if (list.remove(trimmed)) {
                                    changed++;
                                 }
                              } else if (list.add(trimmed)) {
                                 changed++;
                              }
                           } else if (list.remove(trimmed)) {
                              changed++;
                           }
                        } else if (list.add(trimmed)) {
                           changed++;
                        } else if (!list.isKnown(trimmed)) {
                           feedback("§cUnknown " + list.kind().noun() + ": §f" + trimmed);
                        }
                     }
                  }
               }

               this.configManager.save();
               feedback("§b" + module.getName() + " §7" + list.name() + ": §f" + changed + " §7changed (" + list.size() + " total).");
               return;
            }
         default:
            String[] impliedAdd = new String[args.length + 1];
            impliedAdd[0] = "add";
            System.arraycopy(args, 0, impliedAdd, 1, args.length);
            this.editList(module, list, impliedAdd);
      }
   }

   private void handleGet(String[] parts) {
      if (parts.length < 2) {
         feedback("§cUsage: §f.get <module> [setting]");
      } else {
         Optional<Module> module = this.module(parts[1]);
         if (!module.isEmpty()) {
            List<Setting<?>> settings = module.get().settings();
            if (parts.length >= 3) {
               this.setting(module.get(), parts[2]).ifPresent(this::showSetting);
            } else {
               feedback("§b" + module.get().getName() + " §7- " + module.get().getDescription());

               for (Setting<?> setting : settings) {
                  if (!(setting instanceof ActionSetting)) {
                     feedback("§7  " + setting.name().replace(" ", "_") + " §f= " + display(setting));
                  }
               }
            }
         }
      }
   }

   private void handleReset(String[] parts) {
      if (parts.length >= 2 && !parts[1].equalsIgnoreCase("all") && !parts[1].equalsIgnoreCase("*")) {
         Optional<Module> module = this.module(parts[1]);
         if (!module.isEmpty()) {
            if (parts.length >= 3) {
               this.setting(module.get(), parts[2]).ifPresent(settingx -> {
                  settingx.reset();
                  this.configManager.save();
                  feedback("§b" + settingx.name() + " §7reset to §f" + display((Setting<?>)settingx));
               });
            } else {
               this.configManager.setSavingPaused(true);

               try {
                  for (Setting<?> setting : module.get().settings()) {
                     if (!(setting instanceof ActionSetting)) {
                        setting.reset();
                     }
                  }
               } finally {
                  this.configManager.setSavingPaused(false);
                  this.configManager.save();
               }

               feedback("§b" + module.get().getName() + " §7settings reset.");
            }
         }
      } else {
         this.configManager.resetAllSettings();
         feedback("§bGemini §7all settings reset to defaults.");
      }
   }

   private void handleConfig(String[] parts) {
      String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "list";
      switch (action) {
         case "save":
            if (parts.length >= 3 && this.configManager.saveProfile(parts[2])) {
               feedback("§aSaved profile §f" + parts[2]);
            } else {
               feedback("§cUsage: §f.config save <name>");
            }
            break;
         case "load":
            if (parts.length < 3) {
               feedback("§cUsage: §f.config load <name>");
            } else if (this.configManager.loadProfile(parts[2])) {
               feedback("§aLoaded profile §f" + parts[2]);
            } else {
               feedback("§cNo profile named §f" + parts[2]);
            }
            break;
         case "reset":
            this.configManager.resetAllSettings();
            feedback("§aEvery setting is back to its default.");
            break;
         default:
            List<String> profiles = this.configManager.profiles();
            feedback(profiles.isEmpty() ? "§7No saved profiles yet. Use §f.config save <name>" : "§bProfiles: §f" + String.join(", ", profiles));
      }
   }

   private void openGui() {
      Minecraft client = Minecraft.getInstance();
      if (!SingleplayerGate.allowed()) {
         feedback(SingleplayerGate.denialMessage());
      } else {
         client.execute(() -> client.gui.setScreen(new ClickGuiScreen()));
      }
   }

   private static String display(Setting<?> setting) {
      if (setting instanceof NumberSetting number) {
         double value = number.get();
         return Math.abs(value - Math.rint(value)) < 1.0E-6 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
      } else if (setting instanceof ListSetting list) {
         List<String> all = list.get();
         if (all.isEmpty()) {
            return "none";
         } else {
            String preview = String.join(", ", all.subList(0, Math.min(all.size(), 3))).replace("minecraft:", "");
            return all.size() + " (" + preview + (all.size() > 3 ? ", ..." : "") + ")";
         }
      } else {
         return String.valueOf(setting.get());
      }
   }

   private static String describeRange(Setting<?> setting) {
      if (setting instanceof NumberSetting number) {
         return "number " + number.min() + " to " + number.max();
      } else if (setting instanceof ColorSetting) {
         return "hue 0 to 360";
      } else if (setting instanceof ModeSetting mode) {
         return String.join(" / ", mode.modes());
      } else if (setting instanceof ListSetting list) {
         return "list of " + list.kind().noun() + "s: add / remove / toggle / set / clear / list";
      } else {
         return setting instanceof ActionSetting ? "runs when you use it" : "on / off";
      }
   }

   private static boolean isUnbindWord(String name) {
      String var1 = name.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "none", "unknown", "unbind" -> true;
         default -> false;
      };
   }

   private static int resolveKey(String keyName) {
      String name = keyName.trim().toLowerCase(Locale.ROOT);
      if (name.startsWith("key.keyboard.")) {
         name = name.substring("key.keyboard.".length());
      } else if (name.startsWith("key.")) {
         name = name.substring("key.".length());
      }

      switch (name) {
         case "none":
         case "unknown":
         case "unbind":
            return -1;
         case "rshift":
         case "right_shift":
            return 344;
         case "lshift":
         case "left_shift":
         case "shift":
            return 340;
         case "rctrl":
         case "right_control":
         case "right_ctrl":
            return 345;
         case "lctrl":
         case "left_control":
         case "left_ctrl":
         case "ctrl":
         case "control":
            return 341;
         case "alt":
         case "left_alt":
         case "lalt":
            return 342;
         case "right_alt":
         case "ralt":
            return 346;
         case "space":
            return 32;
         default:
            try {
               Key key = InputConstants.getKey("key.keyboard." + name.replace('_', '.'));
               return key.getValue();
            } catch (RuntimeException var5) {
               return -1;
            }
      }
   }

   public static void feedback(String message) {
      if (message != null) {
         Minecraft client = Minecraft.getInstance();
         if (client.gui != null && client.gui.hud != null) {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
         } else {
            Gemini.LOGGER.info(message.replaceAll("§.", ""));
         }
      }
   }
}
