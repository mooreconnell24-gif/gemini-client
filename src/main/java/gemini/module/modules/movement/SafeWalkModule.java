package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;

public final class SafeWalkModule extends Module {
   public SafeWalkModule() {
      super("SafeWalk", "Stops you walking off edges without having to sneak.", Category.MOVEMENT);
   }
}
