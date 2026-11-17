package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.TickControl;

/** Freezes the world with /tick freeze; the player can still move, build and step time forward. */
public final class TickFreeze extends Module {
   private final NumberSetting stepTicks = this.addSetting(
      new NumberSetting("Step Ticks", "How many ticks the Step button advances", 20.0, 1.0, 400.0, 1.0)
   );

   public TickFreeze() {
      super("Tick Freeze", "Freezes mobs, redstone and time while you keep moving.", Category.WORLD);
      this.addSetting(new ActionSetting("Step", "Advance the frozen world by Step Ticks", this::step));
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      if (TickControl.freeze(this)) {
         CommandManager.feedback("§bTick Freeze §7the world is frozen. Use §fStep§7 to advance it.");
      }
   }

   @Override
   public void onDisable() {
      TickControl.unfreeze(this);
   }

   private void step() {
      if (this.isEnabled()) {
         if (!TickControl.step(this, this.stepTicks.intValue())) {
            CommandManager.feedback("§bTick Freeze §7could not step the world right now.");
         }
      } else {
         CommandManager.feedback("§bTick Freeze §7switch the module on first.");
      }
   }

   @Override
   public String info() {
      return "frozen";
   }
}
