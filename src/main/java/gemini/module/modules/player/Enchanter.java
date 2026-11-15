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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Enchanter extends Module {
   private final ModeSetting damage = this.addSetting(
      new ModeSetting("Weapon Damage", "Which damage enchantment weapons get", "Sharpness", "Sharpness", "Smite", "Bane of Arthropods")
   );
   private final ModeSetting mining = this.addSetting(new ModeSetting("Tool Drops", "Fortune or Silk Touch on tools", "Fortune", "Fortune", "Silk Touch"));
   private final ModeSetting protection = this.addSetting(
      new ModeSetting(
         "Armour Protection", "Which protection armour gets", "Protection", "Protection", "Blast Protection", "Fire Protection", "Projectile Protection"
      )
   );
   private final ModeSetting boots = this.addSetting(
      new ModeSetting("Boots Extra", "Depth Strider or Frost Walker on boots", "Depth Strider", "Depth Strider", "Frost Walker")
   );
   private final ModeSetting bow = this.addSetting(new ModeSetting("Bow Extra", "Infinity or Mending on bows", "Infinity", "Infinity", "Mending"));
   private final ModeSetting trident = this.addSetting(new ModeSetting("Trident Extra", "Loyalty with Channeling, or Riptide", "Loyalty", "Loyalty", "Riptide"));
   private final BooleanSetting mendingUnbreaking = this.addSetting(
      new BooleanSetting("Mending + Unbreaking", "Add Mending and Unbreaking III to everything that can take them", true)
   );
   private final BooleanSetting curses = this.addSetting(new BooleanSetting("Curses", "Also add Binding and Vanishing curses", false));

   public Enchanter() {
      super("Enchanter", "Max out the item in your hand with the best vanilla enchantments.", Category.PLAYER);
      this.addSetting(new ActionSetting("Enchant Held Item", "Apply the best enchantments to what you are holding", this::enchantHeld));
      this.addSetting(new ActionSetting("Enchant Armour", "Apply the best enchantments to everything you are wearing", this::enchantArmour));
      this.addSetting(
         new ActionSetting("+30 Levels", "Give yourself 30 experience levels", () -> this.run(List.of("xp add @s 30 levels"), "gave you 30 levels"))
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
   public void onEnable() {
      this.enchantHeld();
      this.setEnabled(false, false);
   }

   private void enchantHeld() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         ItemStack held = player.getMainHandItem();
         if (held.isEmpty()) {
            CommandManager.feedback("§cEnchanter §7hold the item you want enchanted.");
         } else {
            List<String> commands = this.forStack(held, "@s");
            if (commands.isEmpty()) {
               CommandManager.feedback("§cEnchanter §7does not know any enchantments for " + held.getHoverName().getString() + ".");
            } else {
               this.run(commands, "enchanted your " + held.getHoverName().getString());
            }
         }
      }
   }

   private void enchantArmour() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         List<String> commands = new ArrayList<>();
         String[] slots = new String[]{"armor.head", "armor.chest", "armor.legs", "armor.feet"};
         ItemStack[] worn = new ItemStack[]{
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET)
         };
         int pieces = 0;

         for (int i = 0; i < slots.length; i++) {
            if (!worn[i].isEmpty()) {
               List<String> enchants = this.forStack(worn[i], "@s");
               if (!enchants.isEmpty()) {
                  commands.add("item replace entity @s container.40 from entity @s weapon.mainhand");
                  commands.add("item replace entity @s weapon.mainhand from entity @s " + slots[i]);
                  commands.addAll(enchants);
                  commands.add("item replace entity @s " + slots[i] + " from entity @s weapon.mainhand");
                  commands.add("item replace entity @s weapon.mainhand from entity @s container.40");
                  pieces++;
               }
            }
         }

         if (pieces == 0) {
            CommandManager.feedback("§cEnchanter §7you are not wearing any armour.");
         } else if (!player.getOffhandItem().isEmpty()) {
            CommandManager.feedback("§cEnchanter §7empty your offhand first; it is used as a scratch slot while armour is enchanted.");
         } else {
            commands.add("item replace entity @s container.40 with minecraft:air");
            this.run(commands, "enchanted " + pieces + " armour piece" + (pieces == 1 ? "" : "s"));
         }
      }
   }

   private void run(List<String> commands, String done) {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else {
         for (String command : commands) {
            if (!SpCommands.run(command)) {
               CommandManager.feedback(SingleplayerGate.denialMessage());
               return;
            }
         }

         CommandManager.feedback("§bEnchanter §7" + done + ".");
      }
   }

   private List<String> forStack(ItemStack stack, String selector) {
      List<String> commands = new ArrayList<>();
      boolean known = false;
      if (stack.is(ItemTags.SWORDS)) {
         known = true;
         this.add(commands, selector, this.damageId(), 5);
         this.add(commands, selector, "fire_aspect", 2);
         this.add(commands, selector, "looting", 3);
         this.add(commands, selector, "knockback", 2);
         this.add(commands, selector, "sweeping_edge", 3);
      } else if (stack.is(ItemTags.AXES)) {
         known = true;
         this.add(commands, selector, this.damageId(), 5);
         this.add(commands, selector, "efficiency", 5);
         this.add(commands, selector, this.mining.is("Silk Touch") ? "silk_touch" : "fortune", this.mining.is("Silk Touch") ? 1 : 3);
      } else if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
         known = true;
         this.add(commands, selector, "efficiency", 5);
         this.add(commands, selector, this.mining.is("Silk Touch") ? "silk_touch" : "fortune", this.mining.is("Silk Touch") ? 1 : 3);
      } else if (stack.is(Items.BOW)) {
         known = true;
         this.add(commands, selector, "power", 5);
         this.add(commands, selector, "punch", 2);
         this.add(commands, selector, "flame", 1);
         if (this.bow.is("Infinity")) {
            this.add(commands, selector, "infinity", 1);
         }
      } else if (stack.is(Items.CROSSBOW)) {
         known = true;
         this.add(commands, selector, "quick_charge", 3);
         this.add(commands, selector, "multishot", 1);
      } else if (stack.is(Items.TRIDENT)) {
         known = true;
         this.add(commands, selector, "impaling", 5);
         if (this.trident.is("Riptide")) {
            this.add(commands, selector, "riptide", 3);
         } else {
            this.add(commands, selector, "loyalty", 3);
            this.add(commands, selector, "channeling", 1);
         }
      } else if (stack.is(Items.MACE)) {
         known = true;
         this.add(commands, selector, "density", 5);
         this.add(commands, selector, "wind_burst", 3);
         this.add(commands, selector, "fire_aspect", 2);
      } else if (stack.is(Items.FISHING_ROD)) {
         known = true;
         this.add(commands, selector, "luck_of_the_sea", 3);
         this.add(commands, selector, "lure", 3);
      } else if (stack.is(ItemTags.HEAD_ARMOR)) {
         known = true;
         this.add(commands, selector, this.protectionId(), 4);
         this.add(commands, selector, "respiration", 3);
         this.add(commands, selector, "aqua_affinity", 1);
         this.add(commands, selector, "thorns", 3);
      } else if (stack.is(ItemTags.CHEST_ARMOR)) {
         known = true;
         if (!stack.is(Items.ELYTRA)) {
            this.add(commands, selector, this.protectionId(), 4);
            this.add(commands, selector, "thorns", 3);
         }
      } else if (stack.is(ItemTags.LEG_ARMOR)) {
         known = true;
         this.add(commands, selector, this.protectionId(), 4);
         this.add(commands, selector, "swift_sneak", 3);
         this.add(commands, selector, "thorns", 3);
      } else if (stack.is(ItemTags.FOOT_ARMOR)) {
         known = true;
         this.add(commands, selector, this.protectionId(), 4);
         this.add(commands, selector, "feather_falling", 4);
         this.add(commands, selector, this.boots.is("Frost Walker") ? "frost_walker" : "depth_strider", this.boots.is("Frost Walker") ? 2 : 3);
         this.add(commands, selector, "soul_speed", 3);
         this.add(commands, selector, "thorns", 3);
      } else if (stack.is(Items.SHIELD) || stack.is(Items.SHEARS) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.CARROT_ON_A_STICK) || stack.is(Items.BRUSH)) {
         known = true;
      }

      if (known && this.mendingUnbreaking.get()) {
         this.add(commands, selector, "unbreaking", 3);
         if (!stack.is(Items.BOW) || !this.bow.is("Infinity")) {
            this.add(commands, selector, "mending", 1);
         }
      }

      if (known && this.curses.get()) {
         this.add(commands, selector, "vanishing_curse", 1);
         if (stack.is(ItemTags.ARMOR_ENCHANTABLE)) {
            this.add(commands, selector, "binding_curse", 1);
         }
      }

      return commands;
   }

   private String damageId() {
      String mode = this.damage.get();

      return switch (mode) {
         case "Smite" -> "smite";
         case "Bane of Arthropods" -> "bane_of_arthropods";
         default -> "sharpness";
      };
   }

   private String protectionId() {
      String mode = this.protection.get();

      return switch (mode) {
         case "Blast Protection" -> "blast_protection";
         case "Fire Protection" -> "fire_protection";
         case "Projectile Protection" -> "projectile_protection";
         default -> "protection";
      };
   }

   private void add(List<String> commands, String selector, String enchantment, int level) {
      commands.add("enchant " + selector + " minecraft:" + enchantment + " " + level);
   }

   @Override
   public String info() {
      return this.damage.get();
   }
}
