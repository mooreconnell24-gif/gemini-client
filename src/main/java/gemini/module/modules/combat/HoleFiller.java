package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Holes;
import gemini.util.Targets;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class HoleFiller extends PlacementModuleBase {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Hole scan radius", 5.0, 2.0, 6.0, 1.0));
   private final BooleanSetting smart = this.addSetting(new BooleanSetting("Smart Only", "Only fill holes close to an enemy", true));

   public HoleFiller() {
      super("HoleFiller", "Fill nearby safe holes before enemies can enter them.", Category.COMBAT, 1, 3, 4.8);
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      LivingEntity enemy = Targets.nearestEnemy(player, client.level.entitiesForRendering(), 8.0, true);
      if (this.smart.get() && enemy == null) {
         return;
      }

      BlockPos own = player.blockPosition();
      for (BlockPos hole : Holes.around(client.level, own, this.radius.intValue(), 2)) {
         if (!hole.equals(own) && (enemy == null || enemy.blockPosition().distSqr(hole) <= 9.0)) {
            targets.add(hole);
         }
      }
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return CombatHelpers.obsidianOrResistant();
   }
}
