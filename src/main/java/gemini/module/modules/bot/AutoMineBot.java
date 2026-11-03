package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;
import java.util.Set;
import net.minecraft.world.level.block.Block;

public final class AutoMineBot extends Module {
   private final ListSetting blocks = this.addSetting(
      new ListSetting(
         "Target Blocks",
         "Ores or blocks for Baritone to locate and mine",
         ListSetting.Kind.BLOCK,
         "minecraft:diamond_ore",
         "minecraft:deepslate_diamond_ore",
         "minecraft:ancient_debris",
         "minecraft:gold_ore",
         "minecraft:iron_ore"
      )
   );

   public AutoMineBot() {
      super("Auto Mine Bot", "Pathfind and mine target ores via Baritone.", Category.WORLD);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      Set<Block> selected = this.blocks.blocks();
      BaritoneBridge.mineBlocks(selected);
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopAll();
   }

   @Override
   public String info() {
      return BaritoneBridge.isActive() ? BaritoneBridge.statusSummary() : this.blocks.size() + " ores";
   }
}
