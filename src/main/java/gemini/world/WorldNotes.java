package gemini.world;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.util.ClientThread;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The notes of the open single-player world, kept in {@code gemini_notes.txt} inside the world folder so they travel
 * with the world and its backups. Read and written on a background thread; the text lives here for the screen, the
 * HUD widget and the command. State is only changed on the client thread.
 */
public final class WorldNotes {
   public static final String FILE = "gemini_notes.txt";
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Gemini-Notes-IO");
      thread.setDaemon(true);
      return thread;
   });
   private static Path loadedFor;
   private static String text = "";
   private static boolean loaded;
   private static int version;

   private WorldNotes() {
   }

   /** The notes file of the open single-player world, or null outside one. */
   public static Path currentFile() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve(FILE).toAbsolutePath().normalize();
   }

   /** Starts loading the open world's notes if they are not loaded yet; true once they are ready. Client thread. */
   public static boolean ensureLoaded() {
      Path file = currentFile();
      if (file == null) {
         if (loadedFor != null) {
            loadedFor = null;
            text = "";
            loaded = false;
            version++;
         }

         return false;
      }

      if (!file.equals(loadedFor)) {
         loadedFor = file;
         loaded = false;
         text = "";
         version++;
         IO.execute(() -> {
            String read;
            try {
               read = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            } catch (IOException | RuntimeException e) {
               Gemini.LOGGER.warn("Could not read world notes {}", file, e);
               read = "";
            }

            String result = read.length() > NotesText.MAX_CHARS ? read.substring(0, NotesText.MAX_CHARS) : read;
            ClientThread.run(() -> {
               if (file.equals(loadedFor)) {
                  text = result;
                  loaded = true;
                  version++;
               }
            });
         });
      }

      return loaded;
   }

   public static String text() {
      return text;
   }

   /** Changes whenever the text changes or another world's notes are loaded; lets the HUD rebuild only then. */
   public static int version() {
      return version;
   }

   /** Replaces the notes of the open world and writes them in the background. Ignored until they have loaded. */
   public static boolean set(String newText) {
      if (!loaded || loadedFor == null) {
         return false;
      }

      String value = newText == null ? "" : newText.length() > NotesText.MAX_CHARS ? newText.substring(0, NotesText.MAX_CHARS) : newText;
      if (value.equals(text)) {
         return true;
      }

      text = value;
      version++;
      Path file = loadedFor;
      IO.execute(() -> write(file, value));
      return true;
   }

   private static void write(Path file, String value) {
      try {
         if (value.isBlank()) {
            Files.deleteIfExists(file);
            return;
         }

         Path temp = file.resolveSibling(FILE + ".tmp");
         Files.writeString(temp, value, StandardCharsets.UTF_8);
         Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException | RuntimeException e) {
         Gemini.LOGGER.error("Could not save world notes {}", file, e);
         ClientThread.run(() -> CommandManager.feedback("§cWorld Notes §7could not be saved: " + e.getClass().getSimpleName()));
      }
   }
}
