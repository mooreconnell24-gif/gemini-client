package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;

public final class UnfocusedCpu extends Module {
   public static UnfocusedCpu instance;
   private final NumberSetting limit = this.addSetting(
      new NumberSetting("Background FPS", "Frame rate cap while the window is unfocused", 15.0, 1.0, 60.0, 1.0)
   );

   public UnfocusedCpu() {
      super("Unfocused CPU", "Save power by limiting the frame rate when you tab out.", Category.RENDER);
      instance = this;
   }

   public int cap(int vanilla) {
      return Minecraft.getInstance().isWindowActive() ? vanilla : Math.min(vanilla, this.limit.intValue());
   }
}
