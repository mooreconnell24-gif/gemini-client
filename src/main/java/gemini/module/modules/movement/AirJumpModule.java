package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AirJumpModule extends Module {
   private final NumberSetting maxJumps = this.addSetting(
      new NumberSetting("Max Jumps", "Extra jumps before touching the ground (10 = unlimited)", 2.0, 1.0, 10.0, 1.0)
   );
   private boolean wasJump;
   private int used;

   public AirJumpModule() {
      super("AirJump", "Jump again while in the air.", Category.MOVEMENT);
   }

   @Override
   public void onEnable() {
      this.used = 0;
      Minecraft client = Minecraft.getInstance();
      this.wasJump = client.options != null && client.options.keyJump.isDown();
   }

   @Override
   public String info() {
      return this.maxJumps.intValue() >= 10 ? "∞" : Integer.toString(this.maxJumps.intValue());
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gui.screen() == null) {
         boolean jump = client.options.keyJump.isDown();
         if (!player.onGround() && !player.isInWater() && !player.onClimbable()) {
            if (jump && !this.wasJump && (this.maxJumps.intValue() >= 10 || this.used < this.maxJumps.intValue())) {
               player.jumpFromGround();
               this.used++;
            }
         } else {
            this.used = 0;
         }

         this.wasJump = jump;
      }
   }
}
