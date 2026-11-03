package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import net.minecraft.core.BlockPos;

public final class AutoGoto extends Module {
   private final NumberSetting targetX = this.addSetting(new NumberSetting("Target X", "Destination X coordinate", 0.0, -30000.0, 30000.0, 10.0));
   private final NumberSetting targetY = this.addSetting(new NumberSetting("Target Y", "Destination Y coordinate", 64.0, -64.0, 320.0, 1.0));
   private final NumberSetting targetZ = this.addSetting(new NumberSetting("Target Z", "Destination Z coordinate", 0.0, -30000.0, 30000.0, 10.0));

   public AutoGoto() {
      super("Auto Goto", "Pathfind to X/Y/Z coordinates via Baritone.", Category.MOVEMENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      BaritoneBridge.gotoBlock(new BlockPos(this.targetX.intValue(), this.targetY.intValue(), this.targetZ.intValue()));
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopAll();
   }

   @Override
   public String info() {
      return this.targetX.intValue() + " " + this.targetZ.intValue();
   }
}
