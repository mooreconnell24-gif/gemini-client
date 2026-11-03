package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;

public final class NoBobModule extends Module {
   private boolean previous = true;

   public NoBobModule() {
      super("NoBob", "Disable view bobbing.", Category.RENDER);
   }

   @Override
   public void onEnable() {
      this.previous = (Boolean)Minecraft.getInstance().options.bobView().get();
      Minecraft.getInstance().options.bobView().set(false);
   }

   @Override
   public void onDisable() {
      Minecraft.getInstance().options.bobView().set(this.previous);
   }
}
