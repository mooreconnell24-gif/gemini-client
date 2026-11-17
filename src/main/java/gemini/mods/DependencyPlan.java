package gemini.mods;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What installing one version really means: the version itself plus every <em>required</em> dependency (and theirs)
 * that is not installed yet, each resolved to a version for the same loader and game version. Optional and embedded
 * dependencies are listed but never installed; an incompatible project that is installed or would be installed is a
 * problem that blocks the install. Each project is visited once, so dependency cycles end.
 *
 * <p>Runs off the client thread: {@link Lookup} answers synchronously (it is backed by the API client).
 */
public record DependencyPlan(List<Step> installs, List<String> alreadyInstalled, List<String> optional, List<String> problems) {
   /** One file to install: the version and the project's display name. */
   public record Step(Modrinth.Version version, String title) {
   }

   /** Read access to Modrinth for the resolver. Versions are already filtered by loader and game version. */
   public interface Lookup {
      Modrinth.Project project(String projectId) throws IOException;

      Modrinth.Version version(String versionId) throws IOException;

      List<Modrinth.Version> versions(String projectId) throws IOException;
   }

   /** What is installed: Modrinth project ids (from file hashes) and loaded or present Fabric mod ids. */
   public record Installed(Set<String> projectIds, Set<String> modIds) {
      public static final Installed NONE = new Installed(Set.of(), Set.of());

      public boolean has(Modrinth.Project project) {
         if (this.projectIds.contains(project.id())) {
            return true;
         }

         String slug = project.slug().toLowerCase(Locale.ROOT);
         return !slug.isEmpty() && (this.modIds.contains(slug) || this.modIds.contains(slug.replace('-', '_')));
      }

      public boolean hasProjectId(String projectId) {
         return this.projectIds.contains(projectId);
      }
   }

   /** Whether the plan can be carried out. */
   public boolean ok() {
      return this.problems.isEmpty() && !this.installs.isEmpty();
   }

   /** The dependencies that will be added (every step after the first). */
   public List<String> addedNames() {
      List<String> names = new ArrayList<>();
      for (int i = 1; i < this.installs.size(); i++) {
         names.add(this.installs.get(i).title());
      }

      return names;
   }

   public static DependencyPlan resolve(Modrinth.Version root, String rootTitle, ProjectType type, String gameVersion, Lookup lookup, Installed installed)
      throws IOException {
      List<Step> installs = new ArrayList<>();
      Set<String> already = new LinkedHashSet<>();
      Set<String> optional = new LinkedHashSet<>();
      Set<String> problems = new LinkedHashSet<>();
      Set<String> visited = new HashSet<>();
      Deque<Modrinth.Version> queue = new ArrayDeque<>();
      if (!VersionPicker.compatible(root, type.loader, gameVersion)) {
         problems.add(rootTitle + " has no " + type.loader + " file for " + gameVersion);
         return new DependencyPlan(List.of(), List.of(), List.of(), List.copyOf(problems));
      }

      visited.add(root.projectId());
      installs.add(new Step(root, rootTitle));
      queue.add(root);
      while (!queue.isEmpty()) {
         Modrinth.Version current = queue.poll();
         for (Modrinth.Dependency dependency : current.dependencies()) {
            Modrinth.DependencyType kind = dependency.type();
            if (kind == Modrinth.DependencyType.INCOMPATIBLE || kind == Modrinth.DependencyType.EMBEDDED || kind == Modrinth.DependencyType.UNKNOWN) {
               continue; // incompatibles are checked once the whole plan is known
            }

            Modrinth.Version pinned = null;
            String projectId = dependency.projectId();
            if (dependency.versionId() != null) {
               pinned = lookup.version(dependency.versionId());
               if (pinned != null && !pinned.projectId().isEmpty()) {
                  projectId = pinned.projectId();
               }
            }

            if (projectId == null || projectId.isEmpty()) {
               if (kind == Modrinth.DependencyType.REQUIRED) {
                  String file = dependency.fileName() == null ? "a file" : dependency.fileName();
                  problems.add("Needs " + file + ", which is not on Modrinth");
               }

               continue;
            }

            if (kind == Modrinth.DependencyType.OPTIONAL) {
               if (!visited.contains(projectId)) {
                  Modrinth.Project project = lookup.project(projectId);
                  if (project != null && !installed.has(project)) {
                     optional.add(project.title());
                  }
               }

               continue;
            }

            if (!visited.add(projectId)) {
               continue; // already planned, already checked, or a cycle back to an earlier step
            }

            Modrinth.Project project = lookup.project(projectId);
            String title = project == null || project.title().isEmpty() ? projectId : project.title();
            if (project != null ? installed.has(project) : installed.hasProjectId(projectId)) {
               already.add(title);
               continue;
            }

            Modrinth.Version chosen = pinned != null && VersionPicker.compatible(pinned, type.loader, gameVersion) ? pinned : null;
            if (chosen == null) {
               chosen = VersionPicker.best(lookup.versions(projectId), type.loader, gameVersion).orElse(null);
            }

            if (chosen == null) {
               problems.add("Needs " + title + ", which has no " + type.loader + " version for " + gameVersion);
               continue;
            }

            installs.add(new Step(chosen, title));
            queue.add(chosen);
         }
      }

      Set<String> planned = new HashSet<>();
      for (Step step : installs) {
         planned.add(step.version().projectId());
      }

      for (Step step : installs) {
         for (Modrinth.Dependency dependency : step.version().dependencies()) {
            if (dependency.type() != Modrinth.DependencyType.INCOMPATIBLE || dependency.projectId() == null) {
               continue;
            }

            String projectId = dependency.projectId();
            boolean clash = planned.contains(projectId) || installed.hasProjectId(projectId);
            Modrinth.Project project = null;
            if (!clash) {
               project = lookup.project(projectId);
               clash = project != null && installed.has(project);
            }

            if (clash) {
               if (project == null) {
                  project = lookup.project(projectId);
               }

               String name = project == null ? projectId : project.title();
               problems.add(step.title() + " does not work together with " + name);
            }
         }
      }

      return new DependencyPlan(List.copyOf(installs), List.copyOf(already), List.copyOf(optional), List.copyOf(problems));
   }
}
