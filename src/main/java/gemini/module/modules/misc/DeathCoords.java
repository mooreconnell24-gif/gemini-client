package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class DeathCoords extends Module {
   private boolean announced;

   public DeathCoords() {
      super("Death Coords", "Write your death coordinates in client chat.", Category.CLIENT);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (!player.isDeadOrDying()) {
            this.announced = false;
         } else if (!this.announced) {
            this.announced = true;
            CommandManager.feedback(
               "§bDeath Coords §7" + player.blockPosition().getX() + " " + player.blockPosition().getY() + " " + player.blockPosition().getZ()
            );
         }
      }
   }
}
