package gemini.module.modules;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

public final class SeedModule extends Module {
   public SeedModule() {
      super("Seed", "Show the world seed in chat.", Category.WORLD);
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server != null) {
         CommandManager.feedback("§bGemini §7seed §f" + server.overworld().getSeed());
      }

      this.setEnabled(false, false);
   }
}
