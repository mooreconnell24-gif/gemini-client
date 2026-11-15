package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;

public final class CinematicCamera extends Module {
   private boolean previous;

   public CinematicCamera() {
      super("Cinematic Camera", "Smooth, eased camera movement for recording.", Category.RENDER);
   }

   @Override
   public void onEnable() {
      Minecraft client = Minecraft.getInstance();
      this.previous = client.options.smoothCamera;
      client.options.smoothCamera = true;
   }

   @Override
   public void onDisable() {
      Minecraft.getInstance().options.smoothCamera = this.previous;
   }

   @Override
   public void onTick() {
      Minecraft.getInstance().options.smoothCamera = true;
   }
}
