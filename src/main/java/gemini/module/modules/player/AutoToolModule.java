package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.Hands;
import gemini.util.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class AutoToolModule extends Module {
   private final BooleanSetting restore = this.addSetting(new BooleanSetting("Restore Slot", "Switch back to the previous slot when you stop", true));
   private final BooleanSetting saveTools = this.addSetting(new BooleanSetting("Save Durability", "Skip tools that are about to break", true));
   private int previousSlot = -1;

   public AutoToolModule() {
      super("Auto Tool", "Switches to the best tool for the block you are mining.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.restoreSlot();
   }

   /** The slot to go back to belonged to the player that just left. */
   @Override
   public void onWorldChange() {
      this.previousSlot = -1;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gui.screen() == null) {
         if (client.options.keyAttack.isDown() && client.hitResult != null) {
            int best = -1;
            if (client.hitResult.getType() == Type.BLOCK) {
               BlockPos pos = ((BlockHitResult)client.hitResult).getBlockPos();
               BlockState state = client.level.getBlockState(pos);
               int slot = Tools.bestSlot(player, state, this.saveTools.get());
               best = slot == player.getInventory().getSelectedSlot() ? -1 : slot;
            }

            if (best >= 0 && best != player.getInventory().getSelectedSlot() && Hands.claimMainHand()) {
               if (this.previousSlot < 0) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
               }

               player.getInventory().setSelectedSlot(best);
            }
         } else {
            this.restoreSlot();
         }
      }
   }

   private void restoreSlot() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.restore.get() && player != null && this.previousSlot >= 0) {
         if (!Hands.claimMainHand()) {
            return; // someone more important holds the hand this tick; restore next tick
         }

         player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.previousSlot = -1;
   }
}
