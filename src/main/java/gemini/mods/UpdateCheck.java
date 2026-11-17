package gemini.mods;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Works out which installed files have a newer version, from the two hash lookups: {@code /v2/version_files}
 * (which version each file is) and {@code /v2/version_files/update} (the newest version for the same loader and
 * game version). A file is out of date when the newest version is a different version and none of its files is the
 * installed file.
 */
public final class UpdateCheck {
   private UpdateCheck() {
   }

   /** An update for the file with hash {@code sha512}; {@code current} is null when Modrinth did not say. */
   public record Update(String sha512, Modrinth.Version current, Modrinth.Version latest) {
   }

   public static Map<String, Update> find(Map<String, Modrinth.Version> current, Map<String, Modrinth.Version> latest) {
      Map<String, Update> updates = new LinkedHashMap<>();
      for (Map.Entry<String, Modrinth.Version> entry : latest.entrySet()) {
         String hash = entry.getKey().toLowerCase(Locale.ROOT);
         Modrinth.Version newest = entry.getValue();
         Modrinth.Version installed = current.get(hash);
         if (newest == null || newest.primaryFile() == null || installed != null && installed.id().equals(newest.id())) {
            continue;
         }

         boolean same = false;
         for (Modrinth.File file : newest.files()) {
            if (SafeFiles.sameHash(hash, file.sha512())) {
               same = true;
               break;
            }
         }

         if (!same) {
            updates.put(hash, new Update(hash, installed, newest));
         }
      }

      return updates;
   }
}
