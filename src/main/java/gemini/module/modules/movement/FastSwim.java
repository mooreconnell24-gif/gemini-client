package gemini.module.modules.movement;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class FastSwim extends Module {
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Pulse boosts every fourth tick; Dolphin and Stream boost every tick", "Dolphin", "Dolphin", "Stream", "Pulse"));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Swim speed multiplier", 1.55, 1.1, 3.0, 0.05));
   private int tick;

   public FastSwim() {
      super("Fast Swim", "Makes you swim faster in water and lava.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && (client.player.isInWater() || client.player.isInLava()) && MoveUtil.hasInput(client.player)) {
         LocalPlayer player = client.player;
         this.tick++;
         double mult = this.mode.is("Pulse") && this.tick % 4 != 0 ? 1.05 : this.speed.get();
         MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * 0.72 * mult);
      }
   }
}
