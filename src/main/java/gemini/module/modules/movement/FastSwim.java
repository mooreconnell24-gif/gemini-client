package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class FastSwim extends Module {
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Water acceleration mode", "Dolphin", "Dolphin", "Stream", "Pulse"));
   private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Horizontal swim multiplier", 1.55, 1.1, 3.0, 0.05));
   private int tick;

   public FastSwim() {
      super("FastSwim", "Swim faster underwater and on the surface.", Category.MOVEMENT);
   }

   @Override
   public String info() {
      return this.mode.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (MovementHelpers.ready(client) && (client.player.isInWater() || client.player.isInLava()) && MoveUtil.hasInput(client.player)) {
         LocalPlayer player = client.player;
         this.tick++;
         double mult = this.mode.is("Pulse") && this.tick % 4 != 0 ? 1.05 : this.speed.get();
         MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * 0.72 * mult);
      }
   }
}
