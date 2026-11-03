package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class Rotation extends Module {
   private final ModeSetting yawMode = this.addSetting(
      new ModeSetting("Yaw", "Yaw lock mode (None, Snap to step, or Fixed angle)", "Snap", "None", "Snap", "Fixed")
   );
   private final NumberSetting step = this.addSetting(
      new NumberSetting("Snap Step", "Degrees between snap angles", 45.0, 5.0, 90.0, 5.0).showIf(() -> this.yawMode.is("Snap"))
   );
   private final NumberSetting yawValue = this.addSetting(
      new NumberSetting("Yaw Angle", "Angle to hold", 0.0, -180.0, 180.0, 5.0).showIf(() -> this.yawMode.is("Fixed"))
   );
   private final ModeSetting pitchMode = this.addSetting(new ModeSetting("Pitch", "None leaves it alone, Fixed holds an angle", "None", "None", "Fixed"));
   private final NumberSetting pitchValue = this.addSetting(
      new NumberSetting("Pitch Angle", "Angle to hold", 0.0, -90.0, 90.0, 5.0).showIf(() -> this.pitchMode.is("Fixed"))
   );

   public Rotation() {
      super("Rotation", "Snap your yaw to fixed steps or lock your view angles.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (PlayerHelpers.ready(client) && client.gui.screen() == null) {
         float yaw = player.getYRot();
         if (this.yawMode.is("Snap")) {
            yaw = Math.round(yaw / this.step.floatValue()) * this.step.floatValue();
         } else if (this.yawMode.is("Fixed")) {
            yaw = this.yawValue.floatValue();
         }

         player.setYRot(yaw);
         player.yHeadRot = yaw;
         if (this.pitchMode.is("Fixed")) {
            player.setXRot(this.pitchValue.floatValue());
         }
      }
   }
}
