package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class AutoGG extends Module {
   private final ListSetting phrase = this.addSetting(new ListSetting("Message", "Text to send in chat; only the first line is used", ListSetting.Kind.TEXT, "GG"));
   private Entity lastAttacked;
   private int timeout;

   public AutoGG() {
      super("AutoGG", "Says GG in chat when a player you hit in the last five seconds dies.", Category.CLIENT);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onAttack(Entity target) {
      if (target instanceof Player) {
         this.lastAttacked = target;
         this.timeout = 100;
      }
   }

   @Override
   public void onTick() {
      if (this.timeout > 0) {
         this.timeout--;
         Entity target = this.lastAttacked;
         if (target instanceof LivingEntity living && living.isDeadOrDying()) {
            String msg = this.phrase.isEmpty() ? "GG" : this.phrase.get().getFirst();
            CommandManager.sendRaw(msg);
            this.lastAttacked = null;
            this.timeout = 0;
         } else if (target != null && target.isRemoved()) {
            // Gone without dying first (walked out of range, logged off, changed dimension): no GG for that.
            this.lastAttacked = null;
            this.timeout = 0;
         }
      } else {
         this.lastAttacked = null;
      }
   }

   @Override
   public void onDisable() {
      this.lastAttacked = null;
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.lastAttacked = null;
   }
}
