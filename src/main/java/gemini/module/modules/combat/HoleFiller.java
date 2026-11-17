package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Holes;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class HoleFiller extends PlacementModuleBase {
   /** How far away an enemy is looked for. */
   private static final double ENEMY_RANGE = 8.0;
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "How far around you to look for holes, in blocks", 5.0, 2.0, 6.0, 1.0));
   private final BooleanSetting smart = this.addSetting(new BooleanSetting("Smart Only", "Only fill holes while an enemy is within 8 blocks", true));

   private final Targeting targeting = Targeting.builder(this).box("Outline", 120).build();

   public HoleFiller() {
      super("Hole Filler", "Fills nearby safe holes so enemies cannot use them, only near the chosen enemy when one is in range.", Category.COMBAT, 1, 3, 4.8);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.targeting.clear();
   }

   @Override
   public void onWorldChange() {
      super.onWorldChange();
      this.targeting.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      super.onRender3D(partialTick);
      this.targeting.render(partialTick);
   }

   /** The enemy is picked every tick, so the box follows it even while the placement delay runs. */
   @Override
   protected boolean shouldPlace(Minecraft client, LocalPlayer player) {
      LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), ENEMY_RANGE, null);
      return enemy != null || !this.smart.get();
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      LivingEntity enemy = this.targeting.target();
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
