package gemini.mods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyPlanTest {
   private static final String GAME = "26.3";

   /** A Modrinth stand-in backed by recorded responses plus hand-made projects. */
   private static final class FakeLookup implements DependencyPlan.Lookup {
      final Map<String, Modrinth.Project> projects = new HashMap<>();
      final Map<String, Modrinth.Version> versionsById = new HashMap<>();
      final Map<String, List<Modrinth.Version>> versionsByProject = new HashMap<>();

      FakeLookup() {
         for (Modrinth.Project project : Modrinth.parseProjects(Fixtures.modrinth("projects_deps.json"))) {
            this.projects.put(project.id(), project);
         }

         Modrinth.Project sodium = Modrinth.parseProject(Fixtures.modrinth("project_sodium.json"));
         this.projects.put(sodium.id(), sodium);
         this.addVersions(Modrinth.parseVersions(Fixtures.modrinth("versions_fabric_api.json")));
         this.addVersions(Modrinth.parseVersions(Fixtures.modrinth("versions_placeholder_api.json")));
         Modrinth.Version pinnedSodium = Modrinth.parseVersion(Fixtures.modrinth("version_sodium_bAZQdGpg.json"));
         this.versionsById.put(pinnedSodium.id(), pinnedSodium);
      }

      void addVersions(List<Modrinth.Version> versions) {
         for (Modrinth.Version version : versions) {
            this.versionsById.put(version.id(), version);
            this.versionsByProject.computeIfAbsent(version.projectId(), k -> new ArrayList<>()).add(version);
         }
      }

      void project(String id, String slug, String title) {
         this.projects.put(id, new Modrinth.Project(id, slug, title, "", "", "", "mod", 0, List.of(), List.of(), List.of()));
      }

      @Override
      public Modrinth.Project project(String projectId) {
         return this.projects.get(projectId);
      }

      @Override
      public Modrinth.Version version(String versionId) {
         return this.versionsById.get(versionId);
      }

      @Override
      public List<Modrinth.Version> versions(String projectId) {
         return this.versionsByProject.getOrDefault(projectId, List.of());
      }
   }

   private static Modrinth.Version version(String id, String projectId, String deps) {
      return Modrinth.parseVersion(
         "{\"id\":\"" + id + "\",\"project_id\":\"" + projectId + "\",\"version_type\":\"release\",\"loaders\":[\"fabric\"],\"game_versions\":[\"26.3\"],"
            + "\"files\":[{\"url\":\"https://cdn.modrinth.com/" + id + ".jar\",\"filename\":\"" + id + ".jar\",\"primary\":true}],"
            + "\"dependencies\":[" + deps + "]}"
      );
   }

   private static String dep(String projectId, String type) {
      return "{\"project_id\":\"" + projectId + "\",\"dependency_type\":\"" + type + "\"}";
   }

   @Test
   void requiredDependenciesAreAddedAndInstalledOnesSkipped() throws Exception {
      FakeLookup lookup = new FakeLookup();
      Modrinth.Version modmenu = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json")).get(0);
      // Fabric API is running (mod id fabric-api), Text Placeholder API is not
      DependencyPlan.Installed installed = new DependencyPlan.Installed(Set.of(), Set.of("fabric-api", "gemini", "minecraft"));
      DependencyPlan plan = DependencyPlan.resolve(modmenu, "Mod Menu", ProjectType.MOD, GAME, lookup, installed);
      assertTrue(plan.ok(), plan.problems().toString());
      assertEquals(List.of("Mod Menu", "Text Placeholder API"), plan.installs().stream().map(DependencyPlan.Step::title).toList());
      assertEquals(List.of("Text Placeholder API"), plan.addedNames());
      assertEquals(List.of("Fabric API"), plan.alreadyInstalled());
      assertEquals("3.2.0+26.3", plan.installs().get(1).version().versionNumber());
   }

   @Test
   void dependencyFoundByFileHashCountsAsInstalled() throws Exception {
      FakeLookup lookup = new FakeLookup();
      Modrinth.Version modmenu = Modrinth.parseVersions(Fixtures.modrinth("versions_modmenu.json")).get(0);
      DependencyPlan.Installed installed = new DependencyPlan.Installed(Set.of("P7dR8mSH", "eXts2L7r"), Set.of());
      DependencyPlan plan = DependencyPlan.resolve(modmenu, "Mod Menu", ProjectType.MOD, GAME, lookup, installed);
      assertEquals(1, plan.installs().size());
      assertEquals(List.of("Fabric API", "Text Placeholder API"), plan.alreadyInstalled());
   }

   @Test
   void versionPinnedDependencyUsesThatVersion() throws Exception {
      FakeLookup lookup = new FakeLookup();
      Modrinth.Version iris = Modrinth.parseVersions(Fixtures.modrinth("versions_iris.json")).get(0);
      DependencyPlan plan = DependencyPlan.resolve(iris, "Iris", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertTrue(plan.ok(), plan.problems().toString());
      assertEquals(2, plan.installs().size());
      assertEquals("bAZQdGpg", plan.installs().get(1).version().id());
      assertEquals("Sodium", plan.installs().get(1).title());
   }

   @Test
   void optionalDependenciesAreListedNotInstalled() throws Exception {
      FakeLookup lookup = new FakeLookup();
      lookup.project("OPT", "extra", "Extra Thing");
      Modrinth.Version root = version("root", "ROOT", dep("OPT", "optional"));
      DependencyPlan plan = DependencyPlan.resolve(root, "Root", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertEquals(1, plan.installs().size());
      assertEquals(List.of("Extra Thing"), plan.optional());
      assertTrue(plan.ok());
   }

   @Test
   void cyclesEnd() throws Exception {
      FakeLookup lookup = new FakeLookup();
      lookup.project("A", "a", "A");
      lookup.project("B", "b", "B");
      lookup.project("C", "c", "C");
      Modrinth.Version a = version("va", "A", dep("B", "required"));
      Modrinth.Version b = version("vb", "B", dep("C", "required") + "," + dep("A", "required"));
      Modrinth.Version c = version("vc", "C", dep("B", "required") + "," + dep("A", "required"));
      lookup.addVersions(List.of(a, b, c));
      DependencyPlan plan = DependencyPlan.resolve(a, "A", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertTrue(plan.ok());
      assertEquals(List.of("A", "B", "C"), plan.installs().stream().map(DependencyPlan.Step::title).toList());
   }

   @Test
   void missingVersionForTheGameIsAProblem() throws Exception {
      FakeLookup lookup = new FakeLookup();
      lookup.project("OLD", "old-lib", "Old Library");
      Modrinth.Version root = version("root", "ROOT", dep("OLD", "required"));
      DependencyPlan plan = DependencyPlan.resolve(root, "Root", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertFalse(plan.ok());
      assertEquals(List.of("Needs Old Library, which has no fabric version for 26.3"), plan.problems());
   }

   @Test
   void incompatibleWithInstalledOrPlannedIsAProblem() throws Exception {
      FakeLookup lookup = new FakeLookup();
      lookup.project("OF", "optifabric", "OptiFabric");
      lookup.project("DEP", "dep", "Dep");
      Modrinth.Version root = version("root", "ROOT", dep("OF", "incompatible"));
      DependencyPlan clash = DependencyPlan.resolve(root, "Root", ProjectType.MOD, GAME, lookup, new DependencyPlan.Installed(Set.of(), Set.of("optifabric")));
      assertEquals(List.of("Root does not work together with OptiFabric"), clash.problems());
      DependencyPlan fine = DependencyPlan.resolve(root, "Root", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertTrue(fine.ok());
      // a dependency that is itself incompatible with the root
      Modrinth.Version needsDep = version("root2", "ROOT2", dep("DEP", "required"));
      Modrinth.Version depVersion = version("vdep", "DEP", dep("ROOT2", "incompatible"));
      lookup.addVersions(List.of(depVersion));
      DependencyPlan planned = DependencyPlan.resolve(needsDep, "Root2", ProjectType.MOD, GAME, lookup, DependencyPlan.Installed.NONE);
      assertEquals(List.of("Dep does not work together with ROOT2"), planned.problems());
   }

   @Test
   void rootForAnotherGameVersionIsRefused() throws Exception {
      List<Modrinth.Version> cloth = Modrinth.parseVersions(Fixtures.modrinth("versions_cloth_all.json"));
      Modrinth.Version for262 = cloth.stream().filter(v -> v.id().equals("Nv3xnWXd")).findFirst().orElseThrow();
      DependencyPlan plan = DependencyPlan.resolve(for262, "Cloth Config", ProjectType.MOD, GAME, new FakeLookup(), DependencyPlan.Installed.NONE);
      assertFalse(plan.ok());
      assertTrue(plan.installs().isEmpty());
   }
}
