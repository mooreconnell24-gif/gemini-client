package gemini.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The portable form of a profile: what {@code .config export} writes and {@code .config import} reads.
 *
 * <p>A bundle carries module switches, key binds and settings, plus a small header saying what wrote it. It leaves
 * out everything tied to one machine or one world: menu panel positions, the sandbox switch, and the per-module
 * extras such as waypoints, the home position and death coordinates. That makes a bundle safe to hand to someone
 * else and safe to load without losing your own pins.
 */
public final class ProfileBundle {
   public static final String FORMAT = "gemini-profile";
   public static final int FORMAT_VERSION = 1;
   /** Marks a profile whose modules carry no extras, so loading it must leave the current extras alone. */
   static final String PORTABLE = "portable";
   private static final String[] MODULE_KEYS = {"id", "enabled", "keybind", "settings"};
   /** Starts every clipboard export, so an import can tell packed text from JSON typed or pasted by hand. */
   public static final String CLIPBOARD_MARK = "GEMINI1:";
   /** Well under the 64 KB native stack the game's clipboard call copies its text onto. */
   static final int MAX_CLIPBOARD_CHARS = 40000;
   private static final int MAX_UNPACKED_BYTES = 8 * 1024 * 1024;

   private ProfileBundle() {
   }

   /** Builds a bundle from the {@code modules} object of a config, copying only the portable keys of each module. */
   public static JsonObject wrap(String name, String geminiVersion, String exportedAt, JsonObject modules) {
      JsonObject bundle = new JsonObject();
      bundle.addProperty("format", FORMAT);
      bundle.addProperty("formatVersion", FORMAT_VERSION);
      bundle.addProperty("name", name);
      bundle.addProperty("gemini", geminiVersion);
      bundle.addProperty("exportedAt", exportedAt);
      bundle.addProperty("version", ConfigMigrations.CURRENT_VERSION);
      bundle.add("modules", portableModules(modules));
      return bundle;
   }

   /**
    * Turns an imported document into a profile the config manager can store and load. Accepts a bundle, and for
    * convenience a plain profile or config file (anything with a {@code modules} object). The result is migrated
    * to the current config version and marked portable.
    */
   public static JsonObject unwrap(JsonObject document) throws FormatException {
      if (document == null) {
         throw new FormatException("the profile is empty");
      }

      if (document.has("format")) {
         String format = document.get("format").isJsonPrimitive() ? document.get("format").getAsString() : "";
         if (!FORMAT.equals(format)) {
            throw new FormatException("this is not a Gemini profile (format \"" + format + "\")");
         }

         int formatVersion = intOr(document, "formatVersion", 1);
         if (formatVersion > FORMAT_VERSION) {
            throw new FormatException("this profile was exported by a newer Gemini (profile format " + formatVersion + ")");
         }
      }

      if (!document.has("modules") || !document.get("modules").isJsonObject()) {
         throw new FormatException("the profile has no module settings");
      }

      if (ConfigMigrations.versionOf(document) > ConfigMigrations.CURRENT_VERSION) {
         throw new FormatException("this profile was saved by a newer Gemini (config version " + ConfigMigrations.versionOf(document) + ")");
      }

      JsonObject profile = new JsonObject();
      profile.addProperty("version", ConfigMigrations.versionOf(document));
      profile.add("modules", portableModules(document.getAsJsonObject("modules")));
      ConfigMigrations.migrate(profile);
      profile.addProperty(PORTABLE, true);
      return profile;
   }

   /**
    * A bundle as one line of text for the clipboard: the marker, then the gzipped JSON in base64. A full bundle is
    * a few hundred kilobytes of JSON, and the game's clipboard call copies its text onto a small fixed-size native
    * stack, so pasting the raw JSON there crashes the game; packed, the same bundle is around ten kilobytes.
    *
    * @throws FormatException when even the packed form is too long to hand to the clipboard safely
    */
   public static String toClipboardText(JsonObject bundle) throws FormatException {
      byte[] json = JsonFiles.COMPACT.toJson(bundle).getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream packed = new ByteArrayOutputStream(json.length / 4 + 64);
      try (GZIPOutputStream gzip = new GZIPOutputStream(packed)) {
         gzip.write(json);
      } catch (IOException e) {
         throw new FormatException("the profile could not be packed");
      }

      String text = CLIPBOARD_MARK + Base64.getEncoder().encodeToString(packed.toByteArray());
      if (text.length() > MAX_CLIPBOARD_CHARS) {
         throw new FormatException("the profile is too large for the clipboard; export it to a file instead");
      }

      return text;
   }

   /** Reads what {@link #toClipboardText} wrote, or plain JSON pasted by hand. */
   public static JsonObject fromClipboardText(String text) throws FormatException {
      String clean = text == null ? "" : text.trim();
      if (clean.isEmpty()) {
         throw new FormatException("the clipboard is empty");
      }

      try {
         if (!clean.startsWith(CLIPBOARD_MARK)) {
            return JsonFiles.parse(clean);
         }

         byte[] packed = Base64.getDecoder().decode(clean.substring(CLIPBOARD_MARK.length()).trim());
         try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            // A bundle is a few hundred kilobytes; anything far beyond that is not one, and is not unpacked further.
            byte[] json = gzip.readNBytes(MAX_UNPACKED_BYTES + 1);
            if (json.length > MAX_UNPACKED_BYTES) {
               throw new FormatException("the clipboard does not hold a Gemini profile");
            }

            return JsonFiles.parse(new String(json, StandardCharsets.UTF_8));
         }
      } catch (IOException | RuntimeException e) {
         throw new FormatException("the clipboard does not hold a Gemini profile");
      }
   }

   static boolean isPortable(JsonObject profile) {
      return profile.has(PORTABLE) && profile.get(PORTABLE).isJsonPrimitive() && profile.get(PORTABLE).getAsBoolean();
   }

   public static int moduleCount(JsonObject profile) {
      return profile.has("modules") && profile.get("modules").isJsonObject() ? profile.getAsJsonObject("modules").size() : 0;
   }

   private static JsonObject portableModules(JsonObject modules) {
      JsonObject copy = new JsonObject();
      for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
         if (!entry.getValue().isJsonObject()) {
            continue;
         }

         JsonObject from = entry.getValue().getAsJsonObject();
         JsonObject to = new JsonObject();
         for (String key : MODULE_KEYS) {
            if (from.has(key)) {
               to.add(key, from.get(key).deepCopy());
            }
         }

         copy.add(entry.getKey(), to);
      }

      return copy;
   }

   private static int intOr(JsonObject object, String key, int fallback) {
      try {
         return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
      } catch (RuntimeException e) {
         return fallback;
      }
   }

   /** The imported document is not something Gemini can load; the message is written for the player. */
   public static final class FormatException extends Exception {
      private static final long serialVersionUID = 1L;

      public FormatException(String message) {
         super(message);
      }
   }
}
