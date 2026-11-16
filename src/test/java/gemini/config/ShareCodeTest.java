package gemini.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ShareCodeTest {
   private static JsonObject bundle() {
      JsonObject modules = new JsonObject();
      for (int i = 0; i < 120; i++) {
         JsonObject module = new JsonObject();
         module.addProperty("id", "module" + i);
         module.addProperty("enabled", i % 3 == 0);
         module.addProperty("keybind", i % 7 == 0 ? 20 + i : -1);
         JsonObject settings = new JsonObject();
         settings.addProperty("Range", 4.5 + i);
         settings.addProperty("Mode", "Mode" + (i % 4));
         settings.addProperty("Rotate", i % 2 == 0);
         module.add("settings", settings);
         modules.add("Module " + i, module);
      }

      return ProfileBundle.wrap("pvp-main", "3.3.0", "2026-11-16T10:00:00Z", modules);
   }

   private static String raw(byte[] payload, boolean gzip) throws IOException {
      byte[] bytes = payload;
      if (gzip) {
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         try (GZIPOutputStream z = new GZIPOutputStream(out)) {
            z.write(payload);
         }

         bytes = out.toByteArray();
      }

      return ShareCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   }

   private static String message(String code) {
      return assertThrows(ProfileBundle.FormatException.class, () -> ShareCode.decodeProfile(code)).getMessage();
   }

   @Test
   void roundTripKeepsEveryModuleAndSetting() throws Exception {
      JsonObject bundle = bundle();
      String code = ShareCode.encode(bundle);
      assertTrue(code.startsWith("gemini:"));
      assertFalse(code.substring(ShareCode.PREFIX.length()).contains("+"), "URL-safe alphabet");
      assertFalse(code.contains("="), "no padding");
      assertEquals(bundle, ShareCode.decode(code), "the decoded document is the bundle");
      JsonObject profile = ShareCode.decodeProfile(code);
      assertEquals(ProfileBundle.unwrap(bundle), profile, "decoding validates exactly like a file import");
      assertEquals(120, ProfileBundle.moduleCount(profile));
      assertEquals("pvp-main", ShareCode.suggestedName(bundle));
   }

   @Test
   void codesAreCompact() {
      String json = JsonFiles.GSON.toJson(bundle());
      String code = ShareCode.encode(bundle());
      assertTrue(code.length() * 3 < json.length(), "gzip + base64 is much smaller than the JSON: " + code.length() + " vs " + json.length());
   }

   @Test
   void toleratesWrappingWhitespaceAndTheStandardAlphabet() throws Exception {
      String code = ShareCode.encode(bundle());
      String wrapped = "  " + code.substring(0, 30) + "\n" + code.substring(30, 90) + " \r\n " + code.substring(90) + "\n";
      assertEquals(ShareCode.decode(code), ShareCode.decode(wrapped));
      String standard = code.replace('-', '+').replace('_', '/');
      assertEquals(ShareCode.decode(code), ShareCode.decode(standard));
      assertEquals(ShareCode.decode(code), ShareCode.decode("GEMINI:" + code.substring(ShareCode.PREFIX.length())), "prefix is case-insensitive");
   }

   @Test
   void rejectsMalformedInputWithAReason() throws Exception {
      assertTrue(message("").contains("empty"));
      assertTrue(message("   ").contains("empty"));
      assertTrue(message("hello").contains("start with"));
      assertTrue(message("gemini:").contains("nothing after"));
      assertTrue(message("gemini:@@@not*base64!!").contains("base64"));
      assertTrue(message(raw("plain bytes, not gzip".getBytes(StandardCharsets.UTF_8), false)).contains("unpack"));
      assertTrue(message(raw("{not json".getBytes(StandardCharsets.UTF_8), true)).contains("readable profile"));
      assertTrue(message(raw("[1,2,3]".getBytes(StandardCharsets.UTF_8), true)).contains("does not contain a profile"));
      // Valid JSON, but not a profile: the bundle validation rejects it.
      assertTrue(message(raw("{\"hello\":1}".getBytes(StandardCharsets.UTF_8), true)).contains("no module settings"));
      assertTrue(message(raw("{\"format\":\"something-else\",\"modules\":{}}".getBytes(StandardCharsets.UTF_8), true)).contains("not a Gemini profile"));
      // A truncated code (cut off by a chat line limit) does not unpack.
      String code = ShareCode.encode(bundle());
      assertTrue(message(code.substring(0, code.length() / 2)).contains("damaged"));
   }

   @Test
   void rejectsCodesFromANewerGemini() throws Exception {
      JsonObject bundle = bundle();
      bundle.addProperty("formatVersion", ProfileBundle.FORMAT_VERSION + 1);
      assertTrue(message(ShareCode.encode(bundle)).contains("newer Gemini"));
   }

   @Test
   void rejectsOversizeCodes() {
      String huge = ShareCode.PREFIX + "A".repeat(ShareCode.MAX_CODE_CHARS + 10);
      assertTrue(message(huge).contains("too long"));
   }

   @Test
   void rejectsCodesThatInflateTooFar() throws Exception {
      // A few KB of code that would unpack to more than the limit (a "zip bomb").
      String filler = " ".repeat(ShareCode.MAX_JSON_BYTES + 1024);
      String code = raw(("{\"modules\":{},\"pad\":\"" + filler + "\"}").getBytes(StandardCharsets.UTF_8), true);
      assertTrue(code.length() < 64 * 1024, "the bomb itself is small");
      assertTrue(message(code).contains("MB"));
   }

   @Test
   void recognisesCodes() {
      assertTrue(ShareCode.looksLikeCode("  gemini:abc"));
      assertFalse(ShareCode.looksLikeCode("{\"modules\":{}}"));
      assertFalse(ShareCode.looksLikeCode(null));
      assertNull(ShareCode.suggestedName(new JsonObject()));
   }
}
