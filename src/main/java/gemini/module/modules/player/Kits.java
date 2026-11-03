package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import java.util.ArrayList;
import java.util.List;

/** Hands out ready-made loadouts with /give and /item so a test world is kitted in one click. */
public final class Kits extends Module {
   private final ModeSetting kit = this.addSetting(
      new ModeSetting("Kit", "What to hand out", "PvP", "PvP", "Crystal", "Builder", "Miner", "Explorer", "Food")
   );
   private final BooleanSetting clearFirst = this.addSetting(new BooleanSetting("Clear First", "Empty your inventory before giving the kit", false));
   private final BooleanSetting equip = this.addSetting(new BooleanSetting("Equip Armour", "Put the kit's armour straight on", true));

   public Kits() {
      super("Kits", "Give yourself a PvP, crystal, builder, miner, explorer or food kit.", Category.PLAYER);
      this.addSetting(new ActionSetting("Give", "Hand out the selected kit", this::give));
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
      this.give();
      this.setEnabled(false, false);
   }

   private void give() {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
         return;
      }

      List<String> commands = new ArrayList<>();
      if (this.clearFirst.get()) {
         commands.add("clear @s");
      }

      switch (this.kit.get()) {
         case "Crystal" -> {
            this.armour(commands, "netherite");
            this.item(commands, "netherite_sword", 1);
            this.item(commands, "netherite_pickaxe", 1);
            this.item(commands, "end_crystal", 128);
            this.item(commands, "obsidian", 128);
            this.item(commands, "enchanted_golden_apple", 8);
            this.item(commands, "experience_bottle", 64);
            this.item(commands, "ender_pearl", 16);
            this.item(commands, "totem_of_undying", 16);
            this.offhand(commands, "totem_of_undying");
         }
         case "Builder" -> {
            this.item(commands, "diamond_pickaxe", 1);
            this.item(commands, "diamond_axe", 1);
            this.item(commands, "diamond_shovel", 1);
            this.item(commands, "stone", 256);
            this.item(commands, "oak_planks", 128);
            this.item(commands, "glass", 64);
            this.item(commands, "scaffolding", 64);
            this.item(commands, "torch", 64);
            this.item(commands, "ladder", 32);
            this.item(commands, "water_bucket", 1);
            this.item(commands, "bread", 32);
         }
         case "Miner" -> {
            this.armour(commands, "iron");
            this.item(commands, "netherite_pickaxe", 1);
            this.item(commands, "netherite_shovel", 1);
            this.item(commands, "torch", 128);
            this.item(commands, "cobblestone", 64);
            this.item(commands, "ladder", 64);
            this.item(commands, "water_bucket", 1);
            this.item(commands, "cooked_beef", 32);
         }
         case "Explorer" -> {
            if (this.equip.get()) {
               commands.add("item replace entity @s armor.head with minecraft:diamond_helmet");
               commands.add("item replace entity @s armor.chest with minecraft:elytra");
               commands.add("item replace entity @s armor.legs with minecraft:diamond_leggings");
               commands.add("item replace entity @s armor.feet with minecraft:diamond_boots");
            } else {
               this.item(commands, "diamond_helmet", 1);
               this.item(commands, "elytra", 1);
               this.item(commands, "diamond_leggings", 1);
               this.item(commands, "diamond_boots", 1);
            }

            this.item(commands, "firework_rocket", 128);
            this.item(commands, "diamond_sword", 1);
            this.item(commands, "ender_pearl", 16);
            this.item(commands, "compass", 1);
            this.item(commands, "oak_boat", 1);
            this.item(commands, "red_bed", 1);
            this.item(commands, "water_bucket", 1);
            this.item(commands, "bread", 32);
         }
         case "Food" -> {
            this.item(commands, "golden_carrot", 64);
            this.item(commands, "cooked_beef", 64);
            this.item(commands, "bread", 64);
            this.item(commands, "golden_apple", 16);
         }
         default -> {
            this.armour(commands, "diamond");
            this.item(commands, "diamond_sword", 1);
            this.item(commands, "bow", 1);
            this.item(commands, "arrow", 64);
            this.item(commands, "golden_apple", 16);
            this.item(commands, "ender_pearl", 16);
            this.item(commands, "cooked_beef", 32);
            this.item(commands, "totem_of_undying", 4);
            this.offhand(commands, "shield");
         }
      }

      for (String command : commands) {
         if (!SpCommands.run(command)) {
            CommandManager.feedback(SingleplayerGate.denialMessage());
            return;
         }
      }

      CommandManager.feedback("§bKits §7gave you the §f" + this.kit.get() + " §7kit.");
   }

   private void item(List<String> out, String id, int count) {
      out.add("give @s minecraft:" + id + " " + count);
   }

   private void armour(List<String> out, String material) {
      if (this.equip.get()) {
         out.add("item replace entity @s armor.head with minecraft:" + material + "_helmet");
         out.add("item replace entity @s armor.chest with minecraft:" + material + "_chestplate");
         out.add("item replace entity @s armor.legs with minecraft:" + material + "_leggings");
         out.add("item replace entity @s armor.feet with minecraft:" + material + "_boots");
      } else {
         this.item(out, material + "_helmet", 1);
         this.item(out, material + "_chestplate", 1);
         this.item(out, material + "_leggings", 1);
         this.item(out, material + "_boots", 1);
      }
   }

   private void offhand(List<String> out, String id) {
      if (this.equip.get()) {
         out.add("item replace entity @s weapon.offhand with minecraft:" + id);
      } else {
         this.item(out, id, 1);
      }
   }

   @Override
   public String info() {
      return this.kit.get();
   }
}
