package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import gemini.util.AttributeBoost;
import gemini.util.Scheduler;
import gemini.util.SpCommands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;

public final class MaceKill extends Module {
   private final NumberSetting height = this.addSetting(new NumberSetting("Spoof Height", "Height of the fall each hit counts as, in blocks", 6.0, 2.0, 22.0, 1.0));

   public MaceKill() {
      super("Mace Kill", "Makes each mace hit count as a fall from the set height, so it deals smash damage.", Category.COMBAT);
   }

   @Override
   public void onAttack(Entity target) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && player.getMainHandItem().is(Items.MACE)) {
         double h = this.height.get();
         // The server runs this task before the attack packet (tasks run between ticks, packets at the start of
         // the next one). Faking the fall with extra position packets got the player disconnected in 26.x.
         if (SpCommands.onServer(server -> server.fallDistance = Math.max(server.fallDistance, h))) {
            // A hit that did not smash leaves the fall distance behind; clear it once the attack has been handled.
            Scheduler.later(2, () -> AttributeBoost.resetFall(Minecraft.getInstance().player));
         }
      }
   }
}
