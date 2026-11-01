package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.AttributeBoost;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

public final class InstantMineModule extends Module {
   private final BooleanSetting drops = this.addSetting(new BooleanSetting("Drops", "Blocks drop their items", true));

   public InstantMineModule() {
      super("InstantMine", "Break the block you are mining immediately.", Category.PLAYER);
   }

   public boolean breakNow(BlockPos pos) {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null && !client.player.isCreative()) {
         BlockState state = client.level.getBlockState(pos);
         if (!state.isAir() && !(state.getDestroySpeed(client.level, pos) < 0.0F)) {
            BlockPos target = pos.immutable();
            boolean drop = this.drops.get();
            if (client.getConnection() != null) {
               client.getConnection().send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, target, Direction.UP));
               client.getConnection().send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, target, Direction.UP));
            }
            client.level.destroyBlock(target, drop, client.player);
            client.player.swing(InteractionHand.MAIN_HAND);
            AttributeBoost.onServer(serverPlayer -> {
               if (drop) {
                  serverPlayer.gameMode.destroyBlock(target);
               } else {
                  serverPlayer.level().destroyBlock(target, false, serverPlayer);
               }
            });
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
