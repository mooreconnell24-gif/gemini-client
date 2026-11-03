package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;

public final class NoHurtCamModule extends Module {
   private double previous = 1.0;

   public NoHurtCamModule() {
      super("NoHurtCam", "Disable the hurt camera tilt.", Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.previous = (Double)Minecraft.getInstance().options.damageTiltStrength().get();
      Minecraft.getInstance().options.damageTiltStrength().set(0.0);
   }

   @Override
   public void onDisable() {
      Minecraft.getInstance().options.damageTiltStrength().set(this.previous);
   }

   @Override
   public void onTick() {
      Minecraft.getInstance().options.damageTiltStrength().set(0.0);
   }
}
