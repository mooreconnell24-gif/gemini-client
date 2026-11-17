package gemini.mods;

/**
 * The kinds of Modrinth project the browser installs, with where each goes and what Modrinth calls its loader.
 * Mods load only when the game starts; resource packs can be applied straight away; shader packs need Iris.
 */
public enum ProjectType {
   MOD("Mods", "mod", "fabric", "categories:fabric", "mods", ".jar"),
   RESOURCE_PACK("Resource packs", "resourcepack", "minecraft", null, "resourcepacks", ".zip"),
   SHADER("Shaders", "shader", "iris", "categories:iris", "shaderpacks", ".zip");

   public final String label;
   /** Modrinth's {@code project_type}. */
   public final String apiName;
   /** The loader a version must list to be offered here. */
   public final String loader;
   /** An extra search facet, or null. */
   public final String facet;
   /** The folder under the game directory files go into. */
   public final String folder;
   /** The only file extension installed for this type. */
   public final String extension;

   ProjectType(String label, String apiName, String loader, String facet, String folder, String extension) {
      this.label = label;
      this.apiName = apiName;
      this.loader = loader;
      this.facet = facet;
      this.folder = folder;
      this.extension = extension;
   }

   /** Whether files of this type only take effect after the game restarts. */
   public boolean needsRestart() {
      return this == MOD;
   }
}
