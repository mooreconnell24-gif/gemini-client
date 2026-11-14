package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class SelfTrap extends PlacementModuleBase {
   public SelfTrap() {
      super("SelfTrap", "Encase your upper body and head in obsidian.", Category.COMBAT, 1, 3, 4.5);
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      BlockPos head = player.blockPosition().above();
      targets.add(head.north());
      targets.add(head.south());
      targets.add(head.east());
      targets.add(head.west());
      targets.add(head.above());
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return CombatHelpers.obsidianOrResistant();
   }
}
