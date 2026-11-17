package gemini.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The jars in the mods folder and what state each is in, the way Fabric will see the folder on the next start: it
 * loads every {@code *.jar} directly in {@code mods} (not hidden, not in sub folders), so {@code .jar.disabled}
 * files and the {@code .gemini-trash} folder are ignored. A jar's name and version come from the running game when
 * it is loaded, otherwise from its own {@code fabric.mod.json}.
 */
public final class InstalledMods {
   public static final String DISABLED_SUFFIX = ".disabled";
   public static final String TRASH = ".gemini-trash";
   private static final long MAX_MOD_JSON = 1L << 20;

   private InstalledMods() {
   }

   public enum State {
      /** Running now. */
      LOADED("Loaded"),
      /** In the folder but not running: it starts with the game next time. */
      NEXT_START("Loads next start"),
      /** Disabled, and was not running this session either. */
      DISABLED("Disabled"),
      /** Running now, but disabled: it is gone after the next start. */
      OFF_NEXT_START("Off next start");

      public final String label;

      State(String label) {
         this.label = label;
      }
   }

   /** A mod the running game loaded, with the files it came from. */
   public record Loaded(String modId, String name, String version, List<Path> paths) {
   }

   /** One file in the mods folder. */
   public record Entry(Path path, String fileName, String modId, String name, String version, State state, boolean essential) {
      public boolean disabled() {
         return this.state == State.DISABLED || this.state == State.OFF_NEXT_START;
      }
   }

   /** Id, name and version from a jar's {@code fabric.mod.json}; empty strings when absent. */
   public record ModInfo(String id, String name, String version) {
      public static final ModInfo NONE = new ModInfo("", "", "");
   }

   /**
    * Lists {@code modsDir}. {@code essential} mod ids (Gemini itself, Fabric API, the loader) and {@code ownJar} are
    * flagged so the UI can refuse to disable or remove them.
    */
   public static List<Entry> scan(Path modsDir, List<Loaded> loaded, Set<String> essential, Path ownJar) throws IOException {
      List<Entry> entries = new ArrayList<>();
      if (!Files.isDirectory(modsDir)) {
         return entries;
      }

      Map<Path, Loaded> byPath = new HashMap<>();
      for (Loaded mod : loaded) {
         for (Path path : mod.paths()) {
            byPath.put(normal(path), mod);
         }
      }

      Path own = ownJar == null ? null : normal(ownJar);
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
         for (Path path : stream) {
            String file = path.getFileName().toString();
            String lower = file.toLowerCase(Locale.ROOT);
            boolean disabled = lower.endsWith(".jar" + DISABLED_SUFFIX);
            if (file.startsWith(".") || !Files.isRegularFile(path) || !(disabled || lower.endsWith(".jar"))) {
               continue;
            }

            Path jarPath = disabled ? path.resolveSibling(file.substring(0, file.length() - DISABLED_SUFFIX.length())) : path;
            Loaded running = byPath.get(normal(jarPath));
            ModInfo info = running != null ? new ModInfo(running.modId(), running.name(), running.version()) : readModInfo(path);
            State state;
            if (disabled) {
               state = running != null ? State.OFF_NEXT_START : State.DISABLED;
            } else {
               state = running != null ? State.LOADED : State.NEXT_START;
            }

            String name = info.name().isEmpty() ? (info.id().isEmpty() ? file : info.id()) : info.name();
            boolean isEssential = essential.contains(info.id()) || own != null && own.equals(normal(jarPath));
            entries.add(new Entry(path, file, info.id(), name, info.version(), state, isEssential));
         }
      }

      entries.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::fileName));
      return entries;
   }

   /** Reads {@code fabric.mod.json} from a jar; {@link ModInfo#NONE} when the jar has none or cannot be read. */
   public static ModInfo readModInfo(Path jar) {
      try (ZipFile zip = new ZipFile(jar.toFile())) {
         ZipEntry entry = zip.getEntry("fabric.mod.json");
         if (entry == null || entry.getSize() > MAX_MOD_JSON) {
            return ModInfo.NONE;
         }

         try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readNBytes((int)MAX_MOD_JSON);
            JsonElement element = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
               return ModInfo.NONE;
            }

            JsonObject o = element.getAsJsonObject();
            return new ModInfo(string(o, "id"), string(o, "name"), string(o, "version"));
         }
      } catch (IOException | RuntimeException e) {
         return ModInfo.NONE;
      }
   }

   private static String string(JsonObject o, String key) {
      JsonElement element = o.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
   }

   private static Path normal(Path path) {
      return path.toAbsolutePath().normalize();
   }
}
