package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class AutoGG extends Module {
   private final ListSetting phrase = this.addSetting(new ListSetting("Message", "GG text to send", ListSetting.Kind.TEXT, "GG"));
   private Entity lastAttacked;
   private int timeout;

   public AutoGG() {
      super("AutoGG", "Auto-say GG when a player you recently fought dies.", Category.CLIENT);
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
         if (this.lastAttacked != null && (!this.lastAttacked.isAlive() || this.lastAttacked.isRemoved())) {
            String msg = this.phrase.isEmpty() ? "GG" : this.phrase.get().getFirst();
            CommandManager.sendRaw(msg);
            this.lastAttacked = null;
            this.timeout = 0;
         }
      }
   }
}
