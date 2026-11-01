package gemini.module.modules;

import gemini.util.VersionBridge;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class AntiVoidModule extends Module {
   private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "Blocks below the world floor to start rescuing", 2.0, 0.0, 20.0, 1.0));
   private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Upward speed of the rescue", 0.7, 0.2, 2.0, 0.1));

   public AntiVoidModule() {
      super("AntiVoid", "Catch yourself before falling into the void.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && !player.getAbilities().flying) {
         double floor = VersionBridge.minWorldY(client.level) - this.depth.get();
         if (!(player.getY() >= floor + 2.0)) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < this.boost.get()) {
               player.setDeltaMovement(motion.x, this.boost.get(), motion.z);
            }

            player.resetFallDistance();
         }
      }
   }
}
