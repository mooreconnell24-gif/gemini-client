package gemini.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Builds the Modrinth API addresses and request bodies. Pure string work so the exact queries are unit-tested:
 * every user-supplied part is URL-encoded and project or version ids are checked against Modrinth's id alphabet
 * before they are put into a path.
 */
public final class ModrinthUrls {
   public static final String API = "https://api.modrinth.com/v2";
   /** Results per search page. */
   public static final int PAGE_SIZE = 20;
   private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

   private ModrinthUrls() {
   }

   /** Search orders Modrinth offers that make sense here. */
   public enum Sort {
      RELEVANCE("Relevance", "relevance"),
      DOWNLOADS("Downloads", "downloads"),
      UPDATED("Recently updated", "updated");

      public final String label;
      public final String index;

      Sort(String label, String index) {
         this.label = label;
         this.index = index;
      }
   }

   /** Whether {@code id} is a plausible project id, slug or version id (and safe in a path). */
   public static boolean validId(String id) {
      return id != null && ID.matcher(id).matches() && !id.equals(".") && !id.equals("..");
   }

   /** The facets for {@code type} on {@code gameVersion}: one AND-ed group per condition. */
   public static String facets(ProjectType type, String gameVersion) {
      JsonArray facets = new JsonArray();
      facets.add(single("project_type:" + type.apiName));
      if (type.facet != null) {
         facets.add(single(type.facet));
      }

      facets.add(single("versions:" + gameVersion));
      return facets.toString();
   }

   public static URI search(String query, ProjectType type, Sort sort, int offset, String gameVersion) {
      StringBuilder url = new StringBuilder(API).append("/search?");
      String trimmed = query == null ? "" : query.strip();
      if (!trimmed.isEmpty()) {
         url.append("query=").append(enc(trimmed)).append('&');
      }

      url.append("facets=").append(enc(facets(type, gameVersion)));
      url.append("&index=").append(sort.index);
      url.append("&offset=").append(Math.max(0, offset));
      url.append("&limit=").append(PAGE_SIZE);
      return URI.create(url.toString());
   }

   public static URI project(String idOrSlug) {
      return URI.create(API + "/project/" + checked(idOrSlug));
   }

   /** Several projects at once, for naming dependencies. */
   public static URI projects(Collection<String> ids) {
      JsonArray array = new JsonArray();
      for (String id : ids) {
         array.add(checked(id));
      }

      return URI.create(API + "/projects?ids=" + enc(array.toString()));
   }

   /** The versions of a project that list {@code loader} and {@code gameVersion}. */
   public static URI versions(String idOrSlug, String loader, String gameVersion) {
      return URI.create(
         API + "/project/" + checked(idOrSlug) + "/version?loaders=" + enc(single(loader).toString()) + "&game_versions=" + enc(single(gameVersion).toString())
      );
   }

   public static URI version(String versionId) {
      return URI.create(API + "/version/" + checked(versionId));
   }

   /** POST: which versions files with these SHA-512 hashes belong to. */
   public static URI versionFiles() {
      return URI.create(API + "/version_files");
   }

   /** POST: the newest version for each hash on a loader and game version. */
   public static URI versionFilesUpdate() {
      return URI.create(API + "/version_files/update");
   }

   public static String versionFilesBody(Collection<String> sha512) {
      JsonObject body = new JsonObject();
      body.add("hashes", strings(sha512));
      body.addProperty("algorithm", "sha512");
      return body.toString();
   }

   public static String updateBody(Collection<String> sha512, String loader, String gameVersion) {
      JsonObject body = new JsonObject();
      body.add("hashes", strings(sha512));
      body.addProperty("algorithm", "sha512");
      body.add("loaders", single(loader));
      body.add("game_versions", single(gameVersion));
      return body.toString();
   }

   private static String checked(String id) {
      if (!validId(id)) {
         throw new IllegalArgumentException("Not a Modrinth id: " + id);
      }

      return id;
   }

   private static JsonArray single(String value) {
      JsonArray array = new JsonArray();
      array.add(value);
      return array;
   }

   private static JsonArray strings(Collection<String> values) {
      JsonArray array = new JsonArray();
      for (String value : values) {
         array.add(value);
      }

      return array;
   }

   private static String enc(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
   }
}
