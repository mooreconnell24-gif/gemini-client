package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.RotationManager;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ChestAura extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away a container may be", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between opens", 5.0, 1.0, 40.0, 1.0));
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the container in packets", true));
   private final BooleanSetting onlyOnce = this.addSetting(new BooleanSetting("Open Once", "Remember containers that were already opened", true));
   private final BooleanSetting shulkers = this.addSetting(new BooleanSetting("Shulker Boxes", "Also open shulker boxes", true));
   private final BooleanSetting enderChests = this.addSetting(new BooleanSetting("Ender Chests", "Also open ender chests", false));
   private final Set<BlockPos> opened = new HashSet<>();
   private int wait;

   public ChestAura() {
      super("Chest Aura", "Automatically open containers near you.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      this.opened.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         Level level = client.level;
         int r = (int)Math.ceil(this.range.get());
         BlockPos center = player.blockPosition();
         BlockPos best = null;
         double bestDistance = Double.MAX_VALUE;

         for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (this.container(level.getBlockState(pos))
               && (!this.onlyOnce.get() || !this.opened.contains(pos))
               && BlockPlacer.inReach(player, pos, this.range.get())) {
               double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
               if (distance < bestDistance) {
                  bestDistance = distance;
                  best = pos.immutable();
               }
            }
         }

         if (best != null) {
            if (this.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, best), 0.0F, true, ActionPriority.WORLD_UTILITY)) {
               this.wait = 0;
            } else {
               Vec3 hit = Vec3.atCenterOf(best).add(0.0, 0.5, 0.0);
               client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
               Swing.interact(player, InteractionHand.MAIN_HAND);
               this.opened.add(best);
            }
         }
      }
   }

   private boolean container(BlockState state) {
      Block block = state.getBlock();
      if (block instanceof EnderChestBlock) {
         return this.enderChests.get();
      } else {
         return block instanceof ShulkerBoxBlock ? this.shulkers.get() : block instanceof AbstractChestBlock || block instanceof BarrelBlock;
      }
   }

   @Override
   public String info() {
      return this.opened.isEmpty() ? null : this.opened.size() + " opened";
   }
}
