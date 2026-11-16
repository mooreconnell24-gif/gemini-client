package gemini.module.modules.bot;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import net.minecraft.client.Minecraft;
import gemini.module.setting.ModeSetting;
import gemini.util.BaritoneBridge;
import gemini.event.ActionPriority;

public final class FollowBot extends Module {
   private final ModeSetting targetKind = this.addSetting(
      new ModeSetting("Target", "Kind of entity to follow; Priority picks which one of them", "Players", "Players", "Hostile", "Passive", "All")
   );
   // Target above is the kind filter; Priority picks which one of that kind to follow, Stick To Target keeps following them.
   private final Targeting targeting = Targeting.builder(this)
      .filters(
         () -> this.targetKind.is("Players") || this.targetKind.is("All"),
         () -> this.targetKind.is("Hostile") || this.targetKind.is("All"),
         () -> this.targetKind.is("Passive") || this.targetKind.is("All"),
         () -> true
      )
      .stick(true)
      .build();

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
      this.choose();
      // Re-read every tick by both engines, so the path follows whoever Targeting holds right now.
      BaritoneBridge.follow(entity -> entity != null && entity == this.targeting.target());
      BaritoneBridge.claimTask(this);
   }

   @Override
   public void onDisable() {
      BaritoneBridge.stopTaskOf(this);
      this.targeting.clear();
   }

   /** Switches the module off once its task is over or another request replaced it, so the list shows what is really running. */
   @Override
   public void onTick() {
      if (!BaritoneBridge.holdsTask(this)) {
         this.setEnabled(false);
         return;
      }

      this.choose();
   }

   private void choose() {
      Minecraft client = Minecraft.getInstance();
      if (client.player == null || client.level == null) {
         this.targeting.clear();
      } else {
         // Any loaded entity can be followed: the path finder walks to it however far it is.
         this.targeting.pick(client.player, client.level.entitiesForRendering(), Double.POSITIVE_INFINITY, null);
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.targeting.clear();
   }

   @Override
   public String info() {
      return this.targetKind.get();
   }
}
