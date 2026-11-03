package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Holes;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class HoleSnap extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max distance to pull into a safe hole", 4.0, 1.5, 6.0, 0.5));

   public HoleSnap() {
      super("HoleSnap", "Snap smoothly into nearby Bedrock/Obsidian holes.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client)) {
         LocalPlayer player = client.player;
         if (!Holes.isHole(client.level, player.blockPosition())) {
            List<BlockPos> found = Holes.around(client.level, player.blockPosition(), this.range.intValue(), 2);
            if (!found.isEmpty()) {
               BlockPos hole = found.getFirst();
               Vec3 center = new Vec3(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5);
               Vec3 pull = center.subtract(player.position()).normalize().scale(0.24);
               player.setDeltaMovement(pull.x, Math.min(player.getDeltaMovement().y, -0.15), pull.z);
            }
         }
      }
   }
}
