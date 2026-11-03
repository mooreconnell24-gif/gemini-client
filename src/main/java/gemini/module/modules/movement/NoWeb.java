package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class NoWeb extends Module {
   private static volatile NoWeb instance;
   private final BooleanSetting webs = this.addSetting(new BooleanSetting("Cobwebs", "Walk through cobwebs at full speed", true));
   private final BooleanSetting berries = this.addSetting(new BooleanSetting("Berry Bushes", "Walk through sweet berry bushes at full speed", true));
   private final BooleanSetting snow = this.addSetting(new BooleanSetting("Powder Snow", "Walk through powder snow at full speed", true));

   public NoWeb() {
      super("NoWeb", "Prevent cobwebs, berries, and snow from slowing you.", Category.MOVEMENT);
      instance = this;
   }

   /** Called from the entity mixin: whether the local player's "stuck in block" slowdown for this block is skipped. */
   public static boolean cancels(BlockState state) {
      NoWeb module = instance;
      if (module == null || !module.isEnabled()) {
         return false;
      } else if (state.is(Blocks.COBWEB)) {
         return module.webs.get();
      } else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
         return module.berries.get();
      } else {
         return state.is(Blocks.POWDER_SNOW) && module.snow.get();
      }
   }

   @Override
   public void onTick() {
      // Fallback in case the entity hook is not available: push back toward walking speed.
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client)) {
         LocalPlayer player = client.player;
         BlockState feet = client.level.getBlockState(player.blockPosition());
         BlockState head = client.level.getBlockState(player.blockPosition().above());
         if (cancels(feet) || cancels(head)) {
            if (MoveUtil.hasInput(player) && MoveUtil.horizontalSpeed(player) < MoveUtil.baseSpeed(player)) {
               MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player));
            }
         }
      }
   }
}
