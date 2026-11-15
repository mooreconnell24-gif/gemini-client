package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import gemini.util.AttackAssist;

public final class MaceKill extends Module {
   private final NumberSetting height = this.addSetting(new NumberSetting("Spoof Height", "Fall height in blocks credited to each mace hit", 6.0, 2.0, 22.0, 1.0));

   public MaceKill() {
      super("MaceKill", "Give every mace hit the smash damage of a long fall.", Category.COMBAT);
   }

   // The fall used to be faked with two position packets per hit; Minecraft 26.3 disconnects for that, so the
   // height is now credited by AttackAssist at the moment the hit is calculated.
   @Override
   public void onTick() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         AttackAssist.maceFall(player.getUUID(), this.height.get());
      }
   }

   @Override
   public void onDisable() {
      AttackAssist.maceFall(null, 0.0);
   }
}
