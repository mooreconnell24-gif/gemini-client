package gemini.module.modules.misc;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.ui.NotesScreen;
import gemini.world.NotesText;
import gemini.world.WorldNotes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * Opens the notes pad of the open world. Pressing its key (N by default) opens the pad and leaves the module off, like
 * a button. {@code .notes add|todo|done|list} edits the notes from chat; the Notes HUD widget shows them.
 */
public final class WorldNotesModule extends Module {
   /** SDL scancode of N, free in vanilla 26.3 and among Gemini's defaults. */
   private static final int KEY_N = 17;

   public WorldNotesModule() {
      super("World Notes", "Opens a notes pad saved inside the current world; lines starting with [ ] are to-dos.", Category.CLIENT, KEY_N);
      this.addSetting(new ActionSetting("Open Notes", "Open the notes of the current world", this::open));
   }

   @Override
   public List<String> aliases() {
      List<String> list = new ArrayList<>(super.aliases());
      list.add("Notes");
      list.add("Todo");
      return list;
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
   public boolean showInArrayList() {
      return false;
   }

   @Override
   public void onEnable() {
      this.open();
      this.setEnabled(false, false);
   }

   private void open() {
      Minecraft client = Minecraft.getInstance();
      IntegratedServer server = client.getSingleplayerServer();
      if (server == null) {
         CommandManager.feedback("§7Notes belong to a single-player world. Open one first.");
         return;
      }

      WorldNotes.ensureLoaded();
      client.gui.setScreen(new NotesScreen(client.gui.screen(), server.getWorldData().getLevelName()));
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         // ".notes" on its own opens the pad (after the chat screen has closed).
         Minecraft.getInstance().execute(this::open);
         return true;
      }

      String verb = args[0].toLowerCase(Locale.ROOT);
      String rest = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
      if (!verb.equals("add") && !verb.equals("todo") && !verb.equals("done") && !verb.equals("list")) {
         return false;
      }

      if (!WorldNotes.ensureLoaded()) {
         CommandManager.feedback(
            Minecraft.getInstance().getSingleplayerServer() == null ? "§7Notes belong to a single-player world." : "§7The notes are still loading; try again in a moment."
         );
         return true;
      }

      String text = WorldNotes.text();
      switch (verb) {
         case "add" -> {
            if (rest.isEmpty()) {
               CommandManager.feedback("§7Usage: §f.notes add <text>");
            } else {
               WorldNotes.set(NotesText.append(text, rest));
               CommandManager.feedback("§bWorld Notes §7added a line.");
            }
         }
         case "todo" -> {
            if (rest.isEmpty()) {
               CommandManager.feedback("§7Usage: §f.notes todo <text>");
            } else {
               WorldNotes.set(NotesText.append(text, "[ ] " + rest));
               CommandManager.feedback("§bWorld Notes §7added a to-do (" + NotesText.openTodos(WorldNotes.text()) + " open).");
            }
         }
         case "done" -> {
            int n;
            try {
               n = Integer.parseInt(rest);
            } catch (NumberFormatException e) {
               n = -1;
            }

            String changed = NotesText.markDone(text, n);
            if (changed == null) {
               CommandManager.feedback("§7Usage: §f.notes done <number>§7, counting the open to-dos from 1 (see §f.notes list§7).");
            } else {
               WorldNotes.set(changed);
               CommandManager.feedback("§bWorld Notes §7marked to-do " + n + " done.");
            }
         }
         default -> {
            List<String> todos = NotesText.hudLines(text, true, 50, 80);
            if (todos.isEmpty()) {
               CommandManager.feedback("§7No open to-dos. Add one with §f.notes todo <text>§7.");
            }

            for (int i = 0; i < todos.size(); i++) {
               String line = todos.get(i);
               CommandManager.feedback(line.startsWith("+") ? "§7" + line : "§f" + (i + 1) + ". §7" + line.substring(4));
            }
         }
      }

      return true;
   }
}
