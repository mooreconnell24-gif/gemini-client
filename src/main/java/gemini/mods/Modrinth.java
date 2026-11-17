package gemini.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The parts of the Modrinth v2 API Gemini reads, as plain records, and the parsers that build them from the JSON
 * the API returns. Unknown fields are ignored and missing ones become empty values, so a change on Modrinth's side
 * degrades to "less information shown" rather than an exception. Pure Java: no game classes, unit-tested against
 * recorded responses.
 */
public final class Modrinth {
   private Modrinth() {
   }

   /** One project in a search result. */
   public record Hit(
      String projectId, String slug, String title, String author, String description, long downloads, String iconUrl, String projectType,
      List<String> categories, String dateModified
   ) {
   }

   /** One page of search results. */
   public record SearchPage(List<Hit> hits, int offset, int limit, int totalHits) {
      /** Whether the server has results after this page. */
      public boolean hasMore() {
         return this.offset + this.hits.size() < this.totalHits && !this.hits.isEmpty();
      }
   }

   /** A project's details. {@code body} is the long description in Markdown. */
   public record Project(
      String id, String slug, String title, String description, String body, String iconUrl, String projectType, long downloads,
      List<String> categories, List<String> loaders, List<String> gameVersions
   ) {
   }

   /** One downloadable file of a version. */
   public record File(String url, String filename, String sha512, String sha1, long size, boolean primary) {
   }

   /** How a version relates to another project or version. */
   public enum DependencyType {
      REQUIRED,
      OPTIONAL,
      INCOMPATIBLE,
      EMBEDDED,
      UNKNOWN;

      static DependencyType of(String value) {
         if (value == null) {
            return UNKNOWN;
         }

         return switch (value.toLowerCase(Locale.ROOT)) {
            case "required" -> REQUIRED;
            case "optional" -> OPTIONAL;
            case "incompatible" -> INCOMPATIBLE;
            case "embedded" -> EMBEDDED;
            default -> UNKNOWN;
         };
      }
   }

   /** A dependency entry: a specific version, a whole project, or both. Either id may be null. */
   public record Dependency(String versionId, String projectId, String fileName, DependencyType type) {
   }

   /** One version of a project. {@code versionType} is release, beta or alpha. */
   public record Version(
      String id, String projectId, String name, String versionNumber, String versionType, List<String> loaders, List<String> gameVersions,
      String datePublished, List<File> files, List<Dependency> dependencies
   ) {
      /** The primary file, or the first one when none is marked primary; null when the version has no files. */
      public File primaryFile() {
         for (File file : this.files) {
            if (file.primary()) {
               return file;
            }
         }

         return this.files.isEmpty() ? null : this.files.get(0);
      }
   }

   // ---- parsing ----------------------------------------------------------------------------------------------

   public static SearchPage parseSearch(String json) {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      List<Hit> hits = new ArrayList<>();
      for (JsonElement element : array(root, "hits")) {
         if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            hits.add(new Hit(
               str(o, "project_id"), str(o, "slug"), str(o, "title"), str(o, "author"), str(o, "description"), num(o, "downloads"),
               str(o, "icon_url"), str(o, "project_type"), strings(o, "categories"), str(o, "date_modified")
            ));
         }
      }

      return new SearchPage(List.copyOf(hits), (int)num(root, "offset"), (int)num(root, "limit"), (int)num(root, "total_hits"));
   }

   public static Project parseProject(String json) {
      return project(JsonParser.parseString(json).getAsJsonObject());
   }

   /** The {@code /v2/projects?ids=[...]} response. */
   public static List<Project> parseProjects(String json) {
      List<Project> out = new ArrayList<>();
      for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
         if (element.isJsonObject()) {
            out.add(project(element.getAsJsonObject()));
         }
      }

      return List.copyOf(out);
   }

   public static Version parseVersion(String json) {
      return version(JsonParser.parseString(json).getAsJsonObject());
   }

   /** A list of versions ({@code /v2/project/{id}/version}). */
   public static List<Version> parseVersions(String json) {
      List<Version> out = new ArrayList<>();
      for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
         if (element.isJsonObject()) {
            out.add(version(element.getAsJsonObject()));
         }
      }

      return List.copyOf(out);
   }

   /** The hash-keyed map {@code /v2/version_files} and {@code /v2/version_files/update} return. Keys are lower case. */
   public static Map<String, Version> parseVersionsByHash(String json) {
      Map<String, Version> out = new LinkedHashMap<>();
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            out.put(entry.getKey().toLowerCase(Locale.ROOT), version(entry.getValue().getAsJsonObject()));
         }
      }

      return out;
   }

   private static Project project(JsonObject o) {
      return new Project(
         str(o, "id"), str(o, "slug"), str(o, "title"), str(o, "description"), str(o, "body"), str(o, "icon_url"), str(o, "project_type"),
         num(o, "downloads"), strings(o, "categories"), strings(o, "loaders"), strings(o, "game_versions")
      );
   }

   private static Version version(JsonObject o) {
      List<File> files = new ArrayList<>();
      for (JsonElement element : array(o, "files")) {
         if (element.isJsonObject()) {
            JsonObject f = element.getAsJsonObject();
            JsonObject hashes = f.has("hashes") && f.get("hashes").isJsonObject() ? f.getAsJsonObject("hashes") : new JsonObject();
            files.add(new File(
               str(f, "url"), str(f, "filename"), str(hashes, "sha512").toLowerCase(Locale.ROOT), str(hashes, "sha1").toLowerCase(Locale.ROOT),
               num(f, "size"), f.has("primary") && f.get("primary").isJsonPrimitive() && f.get("primary").getAsBoolean()
            ));
         }
      }

      List<Dependency> dependencies = new ArrayList<>();
      for (JsonElement element : array(o, "dependencies")) {
         if (element.isJsonObject()) {
            JsonObject d = element.getAsJsonObject();
            dependencies.add(new Dependency(
               nullable(d, "version_id"), nullable(d, "project_id"), nullable(d, "file_name"), DependencyType.of(nullable(d, "dependency_type"))
            ));
         }
      }

      return new Version(
         str(o, "id"), str(o, "project_id"), str(o, "name"), str(o, "version_number"), str(o, "version_type"), strings(o, "loaders"),
         strings(o, "game_versions"), str(o, "date_published"), List.copyOf(files), List.copyOf(dependencies)
      );
   }

   private static JsonArray array(JsonObject o, String key) {
      JsonElement element = o.get(key);
      return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
   }

   private static String nullable(JsonObject o, String key) {
      JsonElement element = o.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   private static String str(JsonObject o, String key) {
      String value = nullable(o, key);
      return value == null ? "" : value;
   }

   private static long num(JsonObject o, String key) {
      JsonElement element = o.get(key);
      if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
         return element.getAsLong();
      }

      return 0L;
   }

   private static List<String> strings(JsonObject o, String key) {
      List<String> out = new ArrayList<>();
      for (JsonElement element : array(o, key)) {
         if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
         }
      }

      return List.copyOf(out);
   }
}
