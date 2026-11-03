package gemini.module.modules.world;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.util.Inventories;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Single-player sandbox tools: game rules, tick freezing, macros and inventory helpers. */
public final class SandboxModules {
   private SandboxModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new SandboxModules.WorldRules());
      sink.accept(new SandboxModules.TickFreeze());
      sink.accept(new SandboxModules.Macro(1));
      sink.accept(new SandboxModules.Macro(2));
      sink.accept(new SandboxModules.Macro(3));
      sink.accept(new SandboxModules.InventorySorter());
      sink.accept(new SandboxModules.DurabilityAlert());
   }

   /**
    * Game rules on switches. Rule ids changed from camelCase to snake_case in recent versions, so each rule is sent
    * under every known spelling; the ones the running version does not know fail silently.
    */
   public static final class WorldRules extends Module {
      private final List<SandboxModules.WorldRules.Rule> rules = new ArrayList<>();
      private final NumberSetting tickSpeed;
      private final BooleanSetting restore = this.addSetting(
         new BooleanSetting("Restore On Disable", "Put every rule back to its vanilla default when you switch this off", false)
      );
      private boolean applying;

      public WorldRules() {
         super("World Rules", "Flip game rules like Keep Inventory and Mob Griefing without typing commands.", Category.WORLD);
         this.rule("Keep Inventory", "Keep your items and XP when you die", false, true, "keepInventory", "keep_inventory");
         this.rule("Daylight Cycle", "Let the sun move; off freezes the time of day", true, true, "doDaylightCycle", "advance_time", "do_daylight_cycle");
         this.rule("Weather Cycle", "Let the weather change by itself", true, true, "doWeatherCycle", "advance_weather", "do_weather_cycle");
         this.rule("Mob Spawning", "Let monsters and animals spawn naturally", true, true, "doMobSpawning", "spawn_mobs", "do_mob_spawning");
         this.rule("Mob Griefing", "Creepers, endermen and others can change blocks", true, true, "mobGriefing", "mob_griefing");
         this.rule("Fire Spread", "Fire spreads and burns blocks", true, true, "doFireTick", "fire_tick", "do_fire_tick");
         this.rule("Phantoms", "Phantoms spawn when you skip sleeping", true, true, "doInsomnia", "spawn_phantoms", "do_insomnia");
         this.rule("Fall Damage", "Take damage from falling", true, true, "fallDamage", "fall_damage");
         this.rule("Instant Respawn", "Respawn without the death screen", false, false, "doImmediateRespawn", "immediate_respawn", "do_immediate_respawn");
         this.tickSpeed = this.addSetting(
            new NumberSetting("Random Tick Speed", "How fast crops, saplings and grass grow (vanilla is 3)", 3.0, 0.0, 300.0, 1.0)
               .onChange(() -> this.sendTickSpeed(false))
         );
      }

      private void rule(String name, String description, boolean ruleDefault, boolean vanilla, String... ids) {
         SandboxModules.WorldRules.Rule rule = new SandboxModules.WorldRules.Rule(
            this.addSetting(new BooleanSetting(name, description, ruleDefault)), vanilla, ids
         );
         rule.setting.onChange(() -> {
            if (this.isEnabled() && !this.applying) {
               rule.send(rule.setting.get());
            }
         });
         this.rules.add(rule);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onEnable() {
         // Only rules you changed from vanilla are sent, so switching this on never undoes rules you set yourself.
         this.applying = true;
         try {
            for (SandboxModules.WorldRules.Rule rule : this.rules) {
               if (rule.setting.get() != rule.vanilla) {
                  rule.send(rule.setting.get());
               }
            }

            if (this.tickSpeed.intValue() != 3) {
               this.sendTickSpeed(true);
            }
         } finally {
            this.applying = false;
         }
      }

      @Override
      public void onDisable() {
         if (this.restore.get() && Minecraft.getInstance().player != null) {
            for (SandboxModules.WorldRules.Rule rule : this.rules) {
               rule.send(rule.vanilla);
            }

            send("3", "randomTickSpeed", "random_tick_speed");
         }
      }

      private void sendTickSpeed(boolean force) {
         if (force || this.isEnabled() && !this.applying) {
            send(Integer.toString(this.tickSpeed.intValue()), "randomTickSpeed", "random_tick_speed");
         }
      }

      private static void send(String value, String... ids) {
         for (String id : ids) {
            SpCommands.run("gamerule " + id + " " + value);
         }
      }

      @Override
      public String info() {
         int changed = 0;
         for (SandboxModules.WorldRules.Rule rule : this.rules) {
            if (rule.setting.get() != rule.vanilla) {
               changed++;
            }
         }

         return changed == 0 ? null : changed + " changed";
      }

      private record Rule(BooleanSetting setting, boolean vanilla, String[] ids) {
         void send(boolean value) {
            SandboxModules.WorldRules.send(Boolean.toString(value), this.ids);
         }
      }
   }

   /** Freezes the world with /tick freeze; the player can still move, build and step time forward. */
   public static final class TickFreeze extends Module {
      private final NumberSetting stepTicks = this.addSetting(
         new NumberSetting("Step Ticks", "How many ticks the Step button advances", 20.0, 1.0, 400.0, 1.0)
      );

      public TickFreeze() {
         super("Tick Freeze", "Freeze mobs, redstone and time while you keep moving.", Category.WORLD);
         this.addSetting(new ActionSetting("Step", "Advance the frozen world by Step Ticks", this::step));
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         if (SpCommands.run("tick freeze")) {
            CommandManager.feedback("§bTick Freeze §7the world is frozen. Use §fStep§7 to advance it.");
         }
      }

      @Override
      public void onDisable() {
         SpCommands.run("tick unfreeze");
      }

      private void step() {
         if (this.isEnabled()) {
            SpCommands.run("tick step " + this.stepTicks.intValue());
         } else {
            CommandManager.feedback("§bTick Freeze §7switch the module on first.");
         }
      }

      @Override
      public String info() {
         return "frozen";
      }
   }

   /** Runs a list of commands when switched on (bind it to a key). Lines starting with / run as world commands. */
   public static final class Macro extends Module {
      private final ListSetting lines;

      public Macro(int slot) {
         super("Macro " + slot, "Run a list of commands with one key: /commands, .gemini commands or chat.", Category.CLIENT);
         this.lines = this.addSetting(
            new ListSetting(
               "Commands",
               "One per line: /time set day runs in your world, .speed toggles a module, anything else is sent as chat",
               ListSetting.Kind.TEXT,
               slot == 1 ? new String[]{"/time set day", "/weather clear"} : new String[0]
            )
         );
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public boolean showInArrayList() {
         return false;
      }

      @Override
      public void onEnable() {
         int ran = 0;
         for (String line : this.lines.get()) {
            String command = line.trim();
            if (command.isEmpty()) {
               continue;
            }

            if (command.startsWith("/")) {
               SpCommands.run(command.substring(1));
            } else if ((command.startsWith(".") || command.startsWith("#")) && Gemini.getInstance() != null) {
               Gemini.getInstance().commands().handle(command);
            } else {
               CommandManager.sendRaw(command);
            }

            ran++;
         }

         CommandManager.feedback(ran == 0 ? "§b" + this.getName() + " §7has no commands yet. Add some in its settings." : "§b" + this.getName() + " §7ran " + ran + (ran == 1 ? " command." : " commands."));
         this.setEnabled(false, false);
      }
   }

   /** Sorts the main inventory by item id (or by stack size), one swap per step so the server keeps up. */
   public static final class InventorySorter extends Module {
      private final ModeSetting order = this.addSetting(new ModeSetting("Order", "How stacks are ordered", "Item", "Item", "Count"));
      private final BooleanSetting hotbar = this.addSetting(new BooleanSetting("Include Hotbar", "Also sort the nine hotbar slots", false));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between moves", 1.0, 0.0, 10.0, 1.0));
      private int cooldown;
      private int moves;
      private int guard;

      public InventorySorter() {
         super("Inventory Sorter", "Tidy your inventory into sorted, merged stacks.", Category.PLAYER);
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         this.cooldown = 0;
         this.moves = 0;
         this.guard = 0;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player == null || client.gameMode == null) {
            return;
         }

         if (client.gui.screen() != null && !(client.gui.screen() instanceof InventoryScreen) || !Inventories.inventoryOpenOrClosed(player)) {
            return;
         }

         if (!player.inventoryMenu.getCarried().isEmpty()) {
            return;
         }

         if (this.cooldown > 0) {
            this.cooldown--;
            return;
         }

         if (++this.guard > 400) {
            CommandManager.feedback("§bInventory Sorter §7stopped: the inventory kept changing.");
            this.setEnabled(false, false);
            return;
         }

         List<Integer> slots = new ArrayList<>();
         for (int i = this.hotbar.get() ? 0 : 9; i < 36; i++) {
            slots.add(i);
         }

         List<ItemStack> current = new ArrayList<>();
         for (int index : slots) {
            current.add(player.getInventory().getItem(index));
         }

         List<ItemStack> wanted = new ArrayList<>();
         for (ItemStack stack : current) {
            if (!stack.isEmpty()) {
               wanted.add(stack);
            }
         }

         Comparator<ItemStack> byId = Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
         Comparator<ItemStack> byCount = Comparator.<ItemStack>comparingInt(ItemStack::getCount).reversed();
         wanted.sort(this.order.is("Count") ? byCount.thenComparing(byId) : byId.thenComparing(byCount));
         for (int i = 0; i < slots.size(); i++) {
            ItemStack want = i < wanted.size() ? wanted.get(i) : ItemStack.EMPTY;
            ItemStack have = current.get(i);
            if (same(have, want)) {
               continue;
            }

            int from = -1;
            for (int j = i + 1; j < slots.size(); j++) {
               if (!want.isEmpty() && same(current.get(j), want)) {
                  from = j;
                  break;
               }
            }

            if (from < 0) {
               continue;
            }

            int container = player.inventoryMenu.containerId;
            int source = Inventories.menuSlot(slots.get(from));
            int target = Inventories.menuSlot(slots.get(i));
            client.gameMode.handleContainerInput(container, source, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(container, target, 0, ContainerInput.PICKUP, player);
            if (!player.inventoryMenu.getCarried().isEmpty()) {
               client.gameMode.handleContainerInput(container, source, 0, ContainerInput.PICKUP, player);
            }

            this.moves++;
            this.cooldown = this.delay.intValue();
            return;
         }

         CommandManager.feedback("§bInventory Sorter §7done" + (this.moves == 0 ? ", already tidy." : " in " + this.moves + " moves."));
         this.setEnabled(false, false);
      }

      private static boolean same(ItemStack a, ItemStack b) {
         if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
         } else {
            return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
         }
      }

      @Override
      public String info() {
         return this.moves > 0 ? this.moves + " moves" : null;
      }
   }

   /** Warns once when worn armour or a held tool gets close to breaking. */
   public static final class DurabilityAlert extends Module {
      private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
         EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      };
      private final NumberSetting percent = this.addSetting(new NumberSetting("Warn Below %", "Warn when durability drops below this", 10.0, 1.0, 50.0, 1.0));
      private final BooleanSetting chat = this.addSetting(new BooleanSetting("Chat", "Also write the warning in chat", true));
      private final Set<String> warned = new HashSet<>();

      public DurabilityAlert() {
         super("Durability Alert", "Warn you before your armour or tools break.", Category.PLAYER);
      }

      @Override
      public void onDisable() {
         this.warned.clear();
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player == null) {
            return;
         }

         for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = player.getItemBySlot(SLOTS[i]);
            String key = "slot" + i;
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
               this.warned.removeIf(entry -> entry.startsWith(key + ":"));
               continue;
            }

            int left = stack.getMaxDamage() - stack.getDamageValue();
            double pct = 100.0 * left / stack.getMaxDamage();
            String id = key + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (pct < this.percent.get()) {
               if (!this.warned.contains(id)) {
                  this.warned.removeIf(entry -> entry.startsWith(key + ":"));
                  this.warned.add(id);
                  String name = stack.getHoverName().getString();
                  HudOverlay.pushNotification("Durability", name + " has " + left + " uses left", false);
                  if (this.chat.get()) {
                     CommandManager.feedback(String.format(Locale.ROOT, "§bDurability §e%s §7is at §c%.0f%%§7 (%d uses left).", name, pct, left));
                  }
               }
            } else {
               this.warned.remove(id);
            }
         }
      }
   }
}
