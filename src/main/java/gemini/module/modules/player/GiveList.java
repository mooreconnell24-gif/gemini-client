package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;

public final class GiveList extends Module {
   private final ListSetting items = this.addSetting(
      new ListSetting(
         "Items", "Items to give (ids such as diamond_sword or minecraft:ender_pearl)", ListSetting.Kind.ITEM, "diamond_sword", "ender_pearl", "golden_apple"
      )
   );
   private final NumberSetting count = this.addSetting(new NumberSetting("Count", "How many of each", 1.0, 1.0, 64.0, 1.0));
   private final BooleanSetting clearFirst = this.addSetting(new BooleanSetting("Clear First", "Empty your inventory before giving the items", false));

   public GiveList() {
      super("Give List", "Give yourself your own list of items in one click.", Category.PLAYER);
      this.addSetting(new ActionSetting("Give", "Hand out every item in the list", this::give));
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
      } else if (this.items.isEmpty()) {
         CommandManager.feedback("§cGive List §7is empty. Add items in the menu or with §f.set GiveList Items add <item>");
      } else {
         if (this.clearFirst.get()) {
            SpCommands.run("clear @s");
         }

         int given = 0;

         for (String item : this.items.get()) {
            if (this.items.isKnown(item) && SpCommands.run("give @s " + item + " " + this.count.intValue())) {
               given++;
            }
         }

         CommandManager.feedback("§bGive List §7gave you §f" + given + " §7item type" + (given == 1 ? "" : "s") + ".");
      }
   }

   @Override
   public String info() {
      return this.items.size() + " items";
   }
}
