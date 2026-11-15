package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.util.BaritoneBridge;
import gemini.util.Targets;
import gemini.event.ActionPriority;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class FollowBot extends Module {
   private final ModeSetting targetKind = this.addSetting(
      new ModeSetting("Target", "Entities to follow", "Players", "Players", "Hostile", "Passive", "All")
   );

   public FollowBot() {
      super("Follow Bot", "Pathfind and follow nearby players or mobs.", Category.MOVEMENT);
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
      BaritoneBridge.follow(entity -> {
         if (!(entity instanceof LivingEntity living)) {
            return false;
         }

         return switch (this.targetKind.get()) {
            case "Players" -> living instanceof Player;
            case "Hostile" -> Targets.isHostile(living);
            case "Passive" -> Targets.isPassive(living);
            default -> true;
         };
      });
      BaritoneBridge.claimTask(this);
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
      return this.targetKind.get();
   }
}
