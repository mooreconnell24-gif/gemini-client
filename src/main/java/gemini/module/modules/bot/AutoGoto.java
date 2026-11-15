package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;
import net.minecraft.core.BlockPos;

public final class AutoGoto extends Module {
   private final NumberSetting targetX = this.addSetting(new NumberSetting("Target X", "Destination X coordinate", 0.0, -30000.0, 30000.0, 1.0));
   private final NumberSetting targetY = this.addSetting(new NumberSetting("Target Y", "Destination Y coordinate", 64.0, -64.0, 320.0, 1.0));
   private final NumberSetting targetZ = this.addSetting(new NumberSetting("Target Z", "Destination Z coordinate", 0.0, -30000.0, 30000.0, 1.0));

   public AutoGoto() {
      super("Auto Goto", "Pathfind to X/Y/Z coordinates via Baritone.", Category.MOVEMENT);
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
      if (BaritoneBridge.gotoBlock(new BlockPos(this.targetX.intValue(), this.targetY.intValue(), this.targetZ.intValue()))) {
         BaritoneBridge.claimTask(this);
      }
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopTaskOf(this);
   }

   /** Switches the module off once its task is over or another request replaced it, so the list shows what is really running. */
   @Override
   public void onTick() {
      if (!BaritoneBridge.holdsTask(this)) {
         this.setEnabled(false);
      }
   }

   @Override
   public String info() {
      return this.targetX.intValue() + " " + this.targetZ.intValue();
   }
}
