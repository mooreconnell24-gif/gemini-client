package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public final class AntiLevitation extends Module {
   public AntiLevitation() {
      super("AntiLevitation", "Cancel upward drift from Levitation.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && player.hasEffect(MobEffects.LEVITATION)) {
         Vec3 m = player.getDeltaMovement();
         if (m.y > 0.0) {
            player.setDeltaMovement(m.x, 0.0, m.z);
         }
      }
   }
}
