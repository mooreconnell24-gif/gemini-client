package gemini.module.modules.player;

import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class Derp extends Module {
   private final NumberSetting spinSpeed = this.addSetting(new NumberSetting("Spin Speed", "Degrees per tick to spin in packets", 35.0, 5.0, 90.0, 5.0));
   private final BooleanSetting headless = this.addSetting(new BooleanSetting("Pitch Flip", "Oscillate pitch between -90 and 90", true));
   private float yaw;

   public Derp() {
      super("Derp", "Spin server-side head rotations without moving camera.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         this.yaw = Mth.wrapDegrees(this.yaw + this.spinSpeed.floatValue());
         float pitch = this.headless.get() ? Mth.sin(player.tickCount * 0.35F) * 89.0F : player.getXRot();
         RotationManager.request(player, this.yaw, pitch, 180.0F, true, ActionPriority.WORLD_UTILITY, 180.0F);
      }
   }
}
