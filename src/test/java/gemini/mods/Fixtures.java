package gemini.mods;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Reads the Modrinth responses recorded under {@code src/test/resources}. */
final class Fixtures {
   private Fixtures() {
   }

   static byte[] bytes(String path) {
      try (InputStream in = Fixtures.class.getResourceAsStream("/" + path)) {
         if (in == null) {
            throw new IllegalArgumentException("missing fixture " + path);
         }

         return in.readAllBytes();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   static String modrinth(String name) {
      return new String(bytes("modrinth/" + name), StandardCharsets.UTF_8);
   }
}
