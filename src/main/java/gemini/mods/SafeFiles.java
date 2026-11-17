package gemini.mods;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The checks every download goes through before it touches the game folder: the address must be Modrinth's CDN over
 * HTTPS, the file name is reduced to a plain name with the one allowed extension (no folders, no "..", no control
 * or formatting characters), and the content must match the SHA-512 hash the API published.
 */
public final class SafeFiles {
   /** The only host files are downloaded from. */
   public static final String CDN_HOST = "cdn.modrinth.com";
   private static final int MAX_NAME = 120;

   private SafeFiles() {
   }

   /** Whether {@code url} is an HTTPS address on Modrinth's CDN, with no user info and the default port. */
   public static boolean allowedDownload(String url) {
      if (url == null || url.isBlank()) {
         return false;
      }

      try {
         URI uri = new URI(url);
         return "https".equalsIgnoreCase(uri.getScheme())
            && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443)
            && CDN_HOST.equalsIgnoreCase(uri.getHost());
      } catch (Exception e) {
         return false;
      }
   }

   /**
    * A safe file name for {@code name} that ends in {@code extension}, or null when none can be made. Only the last
    * path segment is kept; anything but letters, digits, '.', '_', '+' and '-' becomes '_'; leading dots and dashes
    * are dropped so the result is never hidden, never "..", and never looks like an option.
    */
   public static String sanitizeFilename(String name, String extension) {
      if (name == null || extension == null) {
         return null;
      }

      String base = name;
      int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
      if (slash >= 0) {
         base = base.substring(slash + 1);
      }

      StringBuilder out = new StringBuilder();
      for (int i = 0; i < base.length(); i++) {
         char c = base.charAt(i);
         boolean plain = c < 128 && (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '+' || c == '-');
         if (plain) {
            out.append(c);
         } else if (out.length() == 0 || out.charAt(out.length() - 1) != '_') {
            out.append('_');
         }
      }

      String cleaned = out.toString();
      while (cleaned.startsWith(".") || cleaned.startsWith("-") || cleaned.startsWith("_")) {
         cleaned = cleaned.substring(1);
      }

      while (cleaned.contains("..")) {
         cleaned = cleaned.replace("..", ".");
      }

      String lower = cleaned.toLowerCase(Locale.ROOT);
      String ext = extension.toLowerCase(Locale.ROOT);
      if (!lower.endsWith(ext) || lower.length() <= ext.length()) {
         return null;
      }

      if (cleaned.length() > MAX_NAME) {
         cleaned = cleaned.substring(0, MAX_NAME - ext.length()) + cleaned.substring(cleaned.length() - ext.length());
      }

      // Fabric only loads names ending in exactly ".jar", so the extension is written in lower case
      String stem = cleaned.substring(0, cleaned.length() - ext.length());
      return stem.isEmpty() || stem.endsWith(".") ? null : stem + ext;
   }

   /** Whether {@code candidate} is directly inside {@code folder} (not in a sub folder, not outside it). */
   public static boolean directChild(Path folder, Path candidate) {
      Path dir = folder.toAbsolutePath().normalize();
      Path file = candidate.toAbsolutePath().normalize();
      return dir.equals(file.getParent());
   }

   public static String sha512(Path file) throws IOException {
      MessageDigest digest = digest("SHA-512");
      byte[] buffer = new byte[64 * 1024];
      try (InputStream in = Files.newInputStream(file)) {
         int read;
         while ((read = in.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
         }
      }

      return HexFormat.of().formatHex(digest.digest());
   }

   public static String sha512(byte[] data) {
      return HexFormat.of().formatHex(digest("SHA-512").digest(data));
   }

   /** Case-insensitive, constant-time comparison of two hex hashes; false when either is missing. */
   public static boolean sameHash(String expected, String actual) {
      if (expected == null || actual == null || expected.isEmpty() || expected.length() != actual.length()) {
         return false;
      }

      return MessageDigest.isEqual(expected.toLowerCase(Locale.ROOT).getBytes(), actual.toLowerCase(Locale.ROOT).getBytes());
   }

   private static MessageDigest digest(String algorithm) {
      try {
         return MessageDigest.getInstance(algorithm);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(algorithm + " is part of every Java runtime", e);
      }
   }
}
