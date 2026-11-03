package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Holes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class AnchorPull extends Module {
   private final NumberSetting maxDrop = this.addSetting(new NumberSetting("Max Drop", "Maximum vertical distance to pull into a hole", 4.0, 1.5, 6.0, 0.5));
   private final NumberSetting pullSpeed = this.addSetting(new NumberSetting("Pull Speed", "Downward pull velocity", 0.38, 0.15, 0.8, 0.02));

   public AnchorPull() {
      super("AnchorPull", "Pull straight down into safe PvP holes.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client)) {
         LocalPlayer player = client.player;
         BlockPos pos = player.blockPosition();

         for (int d = 1; d <= this.maxDrop.intValue(); d++) {
            BlockPos below = pos.below(d);
            if (Holes.isHole(client.level, below)) {
               double cx = below.getX() + 0.5 - player.getX();
               double cz = below.getZ() + 0.5 - player.getZ();
               player.setDeltaMovement(cx * 0.25, -this.pullSpeed.get(), cz * 0.25);
               return;
            }

            if (!MovementHelpers.passable(client.level, below)) {
               break;
            }
         }
      }
   }
}
