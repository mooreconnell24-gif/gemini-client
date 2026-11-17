package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModrinthParseTest {
   @Test
   void parsesSearchHits() {
      Modrinth.SearchPage page = Modrinth.parseSearch(Fixtures.modrinth("search_mods.json"));
      assertEquals(4, page.hits().size());
      assertEquals(0, page.offset());
      assertTrue(page.totalHits() > 1000);
      assertTrue(page.hasMore());
      Modrinth.Hit first = page.hits().get(0);
      assertEquals("P7dR8mSH", first.projectId());
      assertEquals("fabric-api", first.slug());
      assertEquals("Fabric API", first.title());
      assertEquals("modmuss50", first.author());
      assertEquals("mod", first.projectType());
      assertTrue(first.downloads() > 100_000_000L);
      assertTrue(first.iconUrl().startsWith("https://cdn.modrinth.com/"));
      assertFalse(first.description().isBlank());
      assertTrue(first.categories().contains("fabric"));
   }

   @Test
   void parsesResourcePackSearch() {
      Modrinth.SearchPage page = Modrinth.parseSearch(Fixtures.modrinth("search_resourcepacks.json"));
      assertEquals(3, page.hits().size());
      for (Modrinth.Hit hit : page.hits()) {
         assertEquals("resourcepack", hit.projectType());
      }
   }

   @Test
   void lastPageHasNoMore() {
      String json = "{\"hits\":[{\"project_id\":\"a\",\"title\":\"A\"}],\"offset\":40,\"limit\":20,\"total_hits\":41}";
      Modrinth.SearchPage page = Modrinth.parseSearch(json);
      assertFalse(page.hasMore());
      assertEquals(0L, page.hits().get(0).downloads());
      assertEquals("", page.hits().get(0).iconUrl());
   }

   @Test
   void parsesVersionsWithFilesHashesAndDependencies() {
      List<Modrinth.Version> versions = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json"));
      assertEquals(3, versions.size());
      Modrinth.Version newest = versions.get(0);
      assertEquals("xckkBHD5", newest.id());
      assertEquals("mOgUt4GM", newest.projectId());
      assertEquals("21.0.1", newest.versionNumber());
      assertTrue(newest.loaders().contains("fabric"));
      assertTrue(newest.gameVersions().contains("26.3"));
      Modrinth.File file = newest.primaryFile();
      assertEquals("modmenu-21.0.1.jar", file.filename());
      assertEquals(128, file.sha512().length());
      assertEquals(862761L, file.size());
      assertTrue(file.url().startsWith("https://cdn.modrinth.com/data/mOgUt4GM/"));
      assertEquals(2, newest.dependencies().size());
      for (Modrinth.Dependency dependency : newest.dependencies()) {
         assertEquals(Modrinth.DependencyType.REQUIRED, dependency.type());
         assertNull(dependency.versionId());
      }
   }

   @Test
   void parsesVersionPinnedDependency() {
      Modrinth.Version iris = Modrinth.parseVersions(Fixtures.modrinth("versions_iris.json")).get(0);
      Modrinth.Dependency sodium = iris.dependencies().get(0);
      assertEquals("AANobbMI", sodium.projectId());
      assertEquals("bAZQdGpg", sodium.versionId());
   }

   @Test
   void parsesProjectAndProjects() {
      Modrinth.Project project = Modrinth.parseProject(Fixtures.modrinth("project_modmenu.json"));
      assertEquals("mOgUt4GM", project.id());
      assertEquals("Mod Menu", project.title());
      assertTrue(project.body().startsWith("# Mod Menu"));
      List<Modrinth.Project> projects = Modrinth.parseProjects(Fixtures.modrinth("projects_deps.json"));
      assertEquals(List.of("fabric-api", "placeholder-api"), projects.stream().map(Modrinth.Project::slug).toList());
   }

   @Test
   void parsesHashLookup() {
      Map<String, Modrinth.Version> byHash = Modrinth.parseVersionsByHash(Fixtures.modrinth("version_files.json"));
      assertEquals(2, byHash.size());
      Optional<Modrinth.Version> modmenu = byHash.values().stream().filter(v -> v.projectId().equals("mOgUt4GM")).findFirst();
      assertTrue(modmenu.isPresent());
      assertEquals("21.0.0", modmenu.get().versionNumber());
   }

   @Test
   void unknownDependencyTypeIsKeptApart() {
      String json = "{\"id\":\"v\",\"files\":[],\"dependencies\":[{\"project_id\":\"p\",\"dependency_type\":\"something-new\"}]}";
      assertEquals(Modrinth.DependencyType.UNKNOWN, Modrinth.parseVersion(json).dependencies().get(0).type());
      assertNull(Modrinth.parseVersion(json).primaryFile());
   }
}
