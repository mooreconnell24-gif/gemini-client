package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class Animations extends Module {
   private static Animations instance;
   private final NumberSetting swingDuration = this.addSetting(
      new NumberSetting("Swing Duration", "Ticks a swing takes (vanilla is 6, less with haste)", 6.0, 1.0, 20.0, 1.0)
   );
   private final BooleanSetting othersToo = this.addSetting(new BooleanSetting("Other Players", "Also apply to other players' swings", false));

   public Animations() {
      super("Animations", "Speed up or slow down arm swing animations.", Category.RENDER);
      instance = this;
   }

   public static int swingDuration(Entity entity) {
      Animations module = instance;
      if (module != null && module.isEnabled()) {
         LocalPlayer self = Minecraft.getInstance().player;
         return entity != self && (!module.othersToo.get() || !(entity instanceof Player)) ? -1 : module.swingDuration.intValue();
      } else {
         return -1;
      }
   }

   @Override
   public String info() {
      return this.swingDuration.intValue() + "t";
   }
}
