package gemini.mods;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Chooses which version of a project to install: it must list the loader (fabric for mods, minecraft for resource
 * packs) and the running game version exactly, and have a file. Among those a release beats a beta beats an alpha,
 * and within the same channel the newest wins. The API is already asked for this filter; this re-checks it so a
 * loose server answer never installs a jar built for another loader or game version.
 */
public final class VersionPicker {
   private VersionPicker() {
   }

   public static boolean compatible(Modrinth.Version version, String loader, String gameVersion) {
      if (version == null || version.primaryFile() == null) {
         return false;
      }

      boolean loaderOk = false;
      for (String l : version.loaders()) {
         if (l.equalsIgnoreCase(loader)) {
            loaderOk = true;
            break;
         }
      }

      return loaderOk && version.gameVersions().contains(gameVersion);
   }

   public static Optional<Modrinth.Version> best(List<Modrinth.Version> versions, String loader, String gameVersion) {
      Modrinth.Version best = null;
      for (Modrinth.Version version : versions) {
         if (compatible(version, loader, gameVersion) && (best == null || better(version, best))) {
            best = version;
         }
      }

      return Optional.ofNullable(best);
   }

   private static boolean better(Modrinth.Version a, Modrinth.Version b) {
      int ra = rank(a.versionType());
      int rb = rank(b.versionType());
      if (ra != rb) {
         return ra > rb;
      }

      // ISO-8601 timestamps in the same format sort by time as plain strings
      return a.datePublished().compareTo(b.datePublished()) > 0;
   }

   private static int rank(String type) {
      return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
         case "release" -> 3;
         case "beta" -> 2;
         case "alpha" -> 1;
         default -> 0;
      };
   }
}
