package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.BackupsScreen;
import gemini.util.InfoText;
import gemini.world.WorldBackups;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Backs up the open single-player world as a zip in the background and restores backups as new worlds. Switched on,
 * it also backs up on a timer. The work itself is in {@link WorldBackups}; the list and restore are in
 * {@link BackupsScreen}.
 */
public final class WorldBackup extends Module {
   private final NumberSetting keep = this.addSetting(
      new NumberSetting("Keep", "How many backups to keep for each world; the oldest are deleted after a new one is made", 5.0, 1.0, 30.0, 1.0)
   );
   private final NumberSetting interval = this.addSetting(
      new NumberSetting("Interval", "Minutes between automatic backups while this module is on", 30.0, 5.0, 240.0, 5.0).onChange(this::restart)
   );
   private final BooleanSetting progress = this.addSetting(
      new BooleanSetting("Show Progress", "Show notifications while a backup is copied; finished and failed backups are always reported", true)
   );
   private final InfoText infoText = new InfoText();
   private final InfoText percentText = new InfoText();
   private int ticks;

   public WorldBackup() {
      super("World Backup", "Saves a zip copy of your world in the background and restores old copies as new worlds.", Category.CLIENT);
      this.addSetting(new ActionSetting("Back Up Now", "Save the open world and copy it into a new backup", this::backUpNow));
      this.addSetting(new ActionSetting("Open Backups", "List your backups and restore one as a new world", this::openScreen));
   }

   @Override
   public List<String> aliases() {
      List<String> list = new ArrayList<>(super.aliases());
      list.add("Backup");
      list.add("Backups");
      return list;
   }

   @Override
   public void onEnable() {
      this.restart();
   }

   private void restart() {
      this.ticks = this.interval.intValue() * 1200;
   }

   @Override
   public void onTick() {
      if (--this.ticks <= 0) {
         this.restart();
         if (!WorldBackups.busy()) {
            WorldBackups.backUp(this.keep.intValue(), this.progress.get());
         }
      }
   }

   private void backUpNow() {
      WorldBackups.backUp(this.keep.intValue(), this.progress.get());
      // A manual backup restarts the timer so the next automatic one is a full interval away.
      this.restart();
   }

   private void openScreen() {
      Minecraft client = Minecraft.getInstance();
      // Queued: from a chat command the chat screen closes after this runs and would replace the new screen.
      client.schedule(() -> client.gui.setScreen(new BackupsScreen(client.gui.screen(), this.keep::intValue)));
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         return false;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "now", "make", "create" -> this.backUpNow();
         case "list", "restore", "open", "screen" -> this.openScreen();
         case "folder" -> WorldBackups.openFolder(WorldBackups.openWorldFolder());
         default -> {
            return false;
         }
      }

      return true;
   }

   @Override
   public String info() {
      WorldBackups.Job job = WorldBackups.current();
      if (job != null && !job.finished()) {
         return this.percentText.whole(Math.round(job.progress.fraction() * 100.0F), "%");
      }

      return this.infoText.clock(Math.max(0, this.ticks) / 20);
   }
}
