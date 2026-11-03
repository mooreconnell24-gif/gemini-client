package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class AutoExcavate extends Module {
   private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Horizontal excavation radius", 4.0, 1.0, 16.0, 1.0));
   private final NumberSetting depth = this.addSetting(new NumberSetting("Depth", "Layers downward to clear", 3.0, 1.0, 16.0, 1.0));

   public AutoExcavate() {
      super("Auto Excavate", "Excavate a cubic area around you layer by layer.", Category.WORLD);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         BlockPos p = player.blockPosition();
         int r = this.radius.intValue();
         int d = this.depth.intValue();
         BaritoneBridge.startExcavate(p.offset(-r, -d, -r), p.offset(r, 2, r));
      }
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopAll();
   }
}
