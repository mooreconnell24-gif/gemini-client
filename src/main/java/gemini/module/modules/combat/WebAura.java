package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.Targeting;
import gemini.module.setting.NumberSetting;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class WebAura extends PlacementModuleBase {
   private static final Predicate<ItemStack> COBWEB = stack -> stack.getItem() instanceof BlockItem item && item.getBlock() == Blocks.COBWEB;
   private final NumberSetting range = this.addSetting(new NumberSetting("Enemy Range", "Target distance for cobwebs", 4.5, 2.0, 6.0, 0.5));

   private final Targeting targeting = Targeting.builder(this).box("Outline", 190).build();

   public WebAura() {
      super("WebAura", "Place cobwebs at the feet of the enemy Priority picks to stall its movement.", Category.COMBAT, 1, 2, 4.5);
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

   /** The target is picked every tick, so the box follows it even while the placement delay runs. */
   @Override
   protected boolean shouldPlace(Minecraft client, LocalPlayer player) {
      return this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), null) != null;
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      LivingEntity enemy = this.targeting.target();
      if (enemy != null) {
         targets.add(enemy.blockPosition());
         targets.add(enemy.blockPosition().above());
      }
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return COBWEB;
   }

   /** The whole point is to place where the enemy stands. */
   @Override
   protected boolean avoidEntities() {
      return false;
   }
}
