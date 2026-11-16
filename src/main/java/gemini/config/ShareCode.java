package gemini.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A profile as one line of text: {@code gemini:} followed by URL-safe base64 of the gzip-compressed portable
 * {@link ProfileBundle} JSON. It is what the "Copy share code" button and {@code .config share} produce, and what
 * {@code .config import code} and "Paste share code" read.
 *
 * <p>Decoding is defensive: the text and the decompressed JSON both have hard size limits (a "zip bomb" cannot
 * inflate past {@link #MAX_JSON_BYTES}), and every failure is a {@link ProfileBundle.FormatException} with a
 * message written for the player. The decoded document still goes through {@link ProfileBundle#unwrap} before
 * anything is stored or applied.
 */
public final class ShareCode {
   public static final String PREFIX = "gemini:";
   /** Longest code accepted, in characters. A full 3.3.0 profile compresses to roughly a tenth of this. */
   public static final int MAX_CODE_CHARS = 256 * 1024;
   /** Most bytes the code may inflate to. */
   public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;

   private ShareCode() {
   }

   /** The share code of a bundle (normally {@link ConfigManager#exportBundle}). */
   public static String encode(JsonObject bundle) {
      byte[] json = JsonFiles.GSON.toJson(bundle).getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, json.length / 4));
      try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
         gzip.write(json);
      } catch (IOException e) {
         // In-memory streams do not fail; keep the compiler and any future stream honest.
         throw new IllegalStateException("could not compress the profile", e);
      }

      return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
   }

   /** Whether {@code text} looks like a share code (it may still fail to decode). */
   public static boolean looksLikeCode(String text) {
      return text != null && text.strip().toLowerCase(Locale.ROOT).startsWith(PREFIX);
   }

   /**
    * The JSON document inside a share code. Whitespace and line breaks anywhere in the code are ignored (codes
    * pasted from chat or a forum often get wrapped). The result is not yet validated as a profile.
    */
   public static JsonObject decode(String code) throws ProfileBundle.FormatException {
      if (code == null || code.isBlank()) {
         throw new ProfileBundle.FormatException("the share code is empty");
      }

      if (code.length() > MAX_CODE_CHARS + 1024) {
         throw new ProfileBundle.FormatException("the share code is too long (over " + MAX_CODE_CHARS / 1024 + " KB)");
      }

      String compact = code.replaceAll("\\s+", "");
      if (!compact.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
         throw new ProfileBundle.FormatException("share codes start with \"" + PREFIX + "\"");
      }

      String payload = compact.substring(PREFIX.length());
      if (payload.isEmpty()) {
         throw new ProfileBundle.FormatException("the share code has nothing after \"" + PREFIX + "\"");
      }

      if (payload.length() > MAX_CODE_CHARS) {
         throw new ProfileBundle.FormatException("the share code is too long (over " + MAX_CODE_CHARS / 1024 + " KB)");
      }

      byte[] compressed;
      try {
         // Accept both alphabets so a code that went through a "standard base64" tool still works.
         compressed = Base64.getUrlDecoder().decode(payload.replace('+', '-').replace('/', '_').replace("=", ""));
      } catch (IllegalArgumentException e) {
         throw new ProfileBundle.FormatException("the share code is damaged (not valid base64); copy it again in full");
      }

      byte[] json = inflate(compressed);
      JsonElement parsed;
      try {
         parsed = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
      } catch (RuntimeException e) {
         throw new ProfileBundle.FormatException("the share code does not contain a readable profile");
      }

      if (parsed == null || !parsed.isJsonObject()) {
         throw new ProfileBundle.FormatException("the share code does not contain a profile");
      }

      return parsed.getAsJsonObject();
   }

   /** Decodes and validates in one go: the result is a migrated, portable profile ready to store or apply. */
   public static JsonObject decodeProfile(String code) throws ProfileBundle.FormatException {
      return ProfileBundle.unwrap(decode(code));
   }

   /** The name the sender gave the profile, sanitised, or null when the code carries none. */
   public static String suggestedName(JsonObject document) {
      if (document == null || !document.has("name") || !document.get("name").isJsonPrimitive()) {
         return null;
      }

      return ConfigManager.profileId(document.get("name").getAsString());
   }

   private static byte[] inflate(byte[] compressed) throws ProfileBundle.FormatException {
      try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
         ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(MAX_JSON_BYTES, Math.max(256, compressed.length * 8)));
         byte[] buffer = new byte[8192];
         int total = 0;
         int read;
         while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > MAX_JSON_BYTES) {
               throw new ProfileBundle.FormatException("the share code unpacks to more than " + MAX_JSON_BYTES / (1024 * 1024) + " MB, which no profile needs");
            }

            out.write(buffer, 0, read);
         }

         return out.toByteArray();
      } catch (IOException e) {
         throw new ProfileBundle.FormatException("the share code is damaged (it does not unpack); copy it again in full");
      }
   }
}
