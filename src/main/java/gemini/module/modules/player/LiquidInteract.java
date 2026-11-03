package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;

public final class LiquidInteract extends Module {
   public static LiquidInteract instance;

   public LiquidInteract() {
      super("Liquid Interact", "Allow placing blocks directly against water and lava.", Category.PLAYER);
      instance = this;
   }

   public static boolean active() {
      LiquidInteract module = instance;
      return module != null && module.isEnabled();
   }
}
