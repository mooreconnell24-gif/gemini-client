package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class GhostHand extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max reach through walls", 4.5, 2.0, 6.0, 0.5));
   private boolean wasDown;

   public GhostHand() {
      super("GhostHand", "Open containers through walls.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (PlayerHelpers.ready(client) && client.gui.screen() == null) {
         boolean down = client.options.keyUse.isDown();
         if (down && !this.wasDown) {
            LocalPlayer player = client.player;
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();

            for (double d = 1.0; d <= this.range.get(); d += 0.4) {
               BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
               BlockState s = client.level.getBlockState(pos);
               if (s.getBlock() instanceof AbstractChestBlock
                  || s.getBlock() instanceof EnderChestBlock
                  || s.getBlock() instanceof ShulkerBoxBlock
                  || s.getBlock() instanceof BarrelBlock) {
                  client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                  player.swing(InteractionHand.MAIN_HAND);
                  break;
               }
            }
         }

         this.wasDown = down;
      }
   }
}
