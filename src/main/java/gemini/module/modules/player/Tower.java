package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class Tower extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Vanilla jumps normally; Fast pops you up on every placement", "Fast", "Vanilla", "Fast")
   );
   private final BooleanSetting requireJump = this.addSetting(new BooleanSetting("Hold Jump", "Only tower while jump is held", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your arm on each placement", true));

   public Tower() {
      super("Tower", "Build straight up by jumping and placing blocks.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (!this.requireJump.get() || client.options.keyJump.isDown()) {
            BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
            if (BlockPlacer.replaceable(client.level, below) && player.getY() - below.getY() > 0.9) {
               if (BlockPlacer.place(client, below, BlockPlacer::solidFiller, this.swing.get(), true, true) && this.mode.is("Fast")) {
                  player.setDeltaMovement(player.getDeltaMovement().x * 0.3, 0.42, player.getDeltaMovement().z * 0.3);
                  player.setPos(player.getX(), below.getY() + 1.0, player.getZ());
               }
            } else if (player.onGround()) {
               player.setDeltaMovement(player.getDeltaMovement().x, 0.42, player.getDeltaMovement().z);
            }
         }
      }
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
