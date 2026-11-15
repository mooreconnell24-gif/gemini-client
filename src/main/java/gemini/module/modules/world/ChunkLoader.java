package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Keeps chunks loaded while you are away, for farms and machines. This is the game's own force-loading, so a chunk
 * stays loaded (and saved that way in the world) until you unload it here.
 */
public final class ChunkLoader extends Module {
   private final ModeSetting onToggle = this.addSetting(
      new ModeSetting("On Toggle", "What switching the module on (or its key) does to the chunk you stand in", "Load", "Load", "Unload")
   );

   public ChunkLoader() {
      super("Chunk Loader", "Keep the chunk you stand in loaded while you are away.", Category.WORLD);
      this.addSetting(new ActionSetting("Load This Chunk", "Keep the chunk you are standing in loaded", () -> this.here(true)));
      this.addSetting(new ActionSetting("Unload This Chunk", "Stop keeping this chunk loaded", () -> this.here(false)));
      this.addSetting(new ActionSetting("Unload All", "Stop keeping any chunk loaded in this dimension", this::unloadAll));
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   @Override
   public void onEnable() {
      this.here(!this.onToggle.is("Unload"));
      this.setEnabled(false, false);
   }

   private void here(boolean load) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return;
      }

      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.run("forceload " + (load ? "add" : "remove") + " " + player.getBlockX() + " " + player.getBlockZ())) {
         CommandManager.feedback(
            "§bChunk Loader §7chunk §f" + (player.getBlockX() >> 4) + " " + (player.getBlockZ() >> 4) + " §7" + (load ? "will stay loaded." : "is no longer kept loaded.")
         );
      }
   }

   private void unloadAll() {
      if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.run("forceload remove all")) {
         CommandManager.feedback("§bChunk Loader §7no chunk in this dimension is kept loaded any more.");
      }
   }
}
