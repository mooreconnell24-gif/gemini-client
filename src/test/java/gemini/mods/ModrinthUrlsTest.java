package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModrinthUrlsTest {
   @Test
   void searchFacetsPerType() {
      assertEquals("[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:26.3\"]]", ModrinthUrls.facets(ProjectType.MOD, "26.3"));
      assertEquals("[[\"project_type:resourcepack\"],[\"versions:26.3\"]]", ModrinthUrls.facets(ProjectType.RESOURCE_PACK, "26.3"));
      assertEquals("[[\"project_type:shader\"],[\"categories:iris\"],[\"versions:26.3\"]]", ModrinthUrls.facets(ProjectType.SHADER, "26.3"));
   }

   @Test
   void searchQueryIsEncoded() {
      URI uri = ModrinthUrls.search("mod menu&index=x", ProjectType.MOD, ModrinthUrls.Sort.DOWNLOADS, 40, "26.3");
      String query = uri.getRawQuery();
      assertTrue(query.startsWith("query=mod%20menu%26index%3Dx&"), query);
      assertTrue(query.contains("&index=downloads&offset=40&limit=20"), query);
      String facets = URLDecoder.decode(query.substring(query.indexOf("facets=") + 7, query.indexOf("&index")), StandardCharsets.UTF_8);
      assertEquals(ModrinthUrls.facets(ProjectType.MOD, "26.3"), facets);
      assertFalse(ModrinthUrls.search("  ", ProjectType.MOD, ModrinthUrls.Sort.RELEVANCE, 0, "26.3").getRawQuery().contains("query="));
   }

   @Test
   void versionQueryNamesLoaderAndGameVersion() {
      URI uri = ModrinthUrls.versions("modmenu", "fabric", "26.3");
      assertEquals("/v2/project/modmenu/version", uri.getPath());
      assertEquals("loaders=[\"fabric\"]&game_versions=[\"26.3\"]", URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8));
   }

   @Test
   void idsCannotBreakOutOfThePath() {
      assertThrows(IllegalArgumentException.class, () -> ModrinthUrls.project("../user"));
      assertThrows(IllegalArgumentException.class, () -> ModrinthUrls.version("a/b"));
      assertThrows(IllegalArgumentException.class, () -> ModrinthUrls.projects(List.of("ok", "no way")));
      assertFalse(ModrinthUrls.validId(".."));
      assertTrue(ModrinthUrls.validId("P7dR8mSH"));
      assertTrue(ModrinthUrls.validId("fabric-api"));
   }

   @Test
   void requestBodies() {
      assertEquals("{\"hashes\":[\"aa\",\"bb\"],\"algorithm\":\"sha512\"}", ModrinthUrls.versionFilesBody(List.of("aa", "bb")));
      assertEquals(
         "{\"hashes\":[\"aa\"],\"algorithm\":\"sha512\",\"loaders\":[\"fabric\"],\"game_versions\":[\"26.3\"]}",
         ModrinthUrls.updateBody(List.of("aa"), "fabric", "26.3")
      );
   }

   @Test
   void rateLimitWait() {
      assertEquals(30_000L, ModrinthClient.retryAfterMs(Optional.of("30"), Optional.of("5")));
      assertEquals(5_000L, ModrinthClient.retryAfterMs(Optional.empty(), Optional.of("5")));
      assertEquals(1_000L, ModrinthClient.retryAfterMs(Optional.empty(), Optional.of("0")));
      assertEquals(5_000L, ModrinthClient.retryAfterMs(Optional.of("soon"), Optional.empty()));
   }
}
