package gemini.config;

import gemini.input.Keys;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import gemini.Gemini;
import gemini.input.GeminiKeys;
import gemini.module.Module;
import gemini.module.ModuleManager;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.module.setting.Setting;
import gemini.util.ClientThread;
import gemini.util.SingleplayerGate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import gemini.command.CommandManager;

/**
 * Loads and saves {@code gemini_config.json} and the named profiles next to it.
 *
 * <p><b>Saving.</b> {@link #save()} only marks the config as changed. {@link #tick()} writes it once changes stop for
 * a moment. The JSON is built on the client thread and handed to {@link ConfigWriter}, which writes it atomically
 * on a background thread and keeps the previous file as {@code .bak}; a failed write is retried and reported
 * instead of being lost.
 *
 * <p><b>Loading.</b> An unreadable config falls back to its {@code .bak}; only when both are unusable does the
 * client start from defaults, and the unreadable file is kept as {@code .broken} either way.
 *
 * <p><b>Profiles.</b> {@code gemini_profiles/<name>.json} are full snapshots. {@code gemini_exports/<name>.json}
 * are portable {@link ProfileBundle}s for sharing; importing one stores it as a profile.
 */
public final class ConfigManager {
   private static final long SAVE_QUIET_MS = 750L;
   private static final long SAVE_MAX_DELAY_MS = 5000L;
   private static final long RETRY_MS = 5000L;
   private static final long SHUTDOWN_WAIT_MS = 5000L;
   private static final String SANDBOX_KEY = "sandboxMode";
   private final ModuleManager moduleManager;
   private final Path configPath;
   private final Path profileDir;
   private final Path exportDir;
   private final Map<String, ConfigManager.PanelState> panels = new LinkedHashMap<>();
   private final ConfigWriter writer = new ConfigWriter(this::writeFailed);
   private boolean suppressAutosave;
   private boolean savingPaused;
   private boolean dirty;
   private long dirtySince;
   private long lastChange;
   private long lastRetry;
   /** Set from the IO thread when a write fails; shown to the player once and cleared by the next good save. */
   private volatile String writeProblem;
   private volatile boolean writeProblemShown;
   /** Something the player should hear about the last load (restored from backup, reset to defaults). */
   private String loadNotice;

   public ConfigManager(ModuleManager moduleManager) {
      this(moduleManager, FabricLoader.getInstance().getConfigDir());
   }

   ConfigManager(ModuleManager moduleManager, Path dir) {
      this.moduleManager = moduleManager;
      this.configPath = dir.resolve("gemini_config.json");
      this.profileDir = dir.resolve("gemini_profiles");
      this.exportDir = dir.resolve("gemini_exports");
   }

   public Path exportDir() {
      return this.exportDir;
   }

   public static String normalizeId(String raw) {
      return ConfigMigrations.normalizeId(raw);
   }

   public ConfigManager.PanelState panel(String category) {
      return this.panels.computeIfAbsent(category, key -> new ConfigManager.PanelState());
   }

   public void resetPanels() {
      this.panels.clear();
      this.save();
   }

   public void load() {
      JsonFiles.Loaded loaded;
      try {
         loaded = JsonFiles.readWithBackup(this.configPath);
      } catch (IOException e) {
         Gemini.LOGGER.error("gemini_config.json and its backup are both unreadable; starting from defaults", e);
         this.quarantine();
         this.panels.clear();
         this.loadNotice = "§eGemini §7could not read its config or the backup, so settings were reset. The unreadable file was kept as §fgemini_config.json.broken§7.";
         this.save();
         return;
      }

      if (loaded == null) {
         // First run: write the defaults so the file exists.
         this.save();
         return;
      }

      if (loaded.fromBackup()) {
         Gemini.LOGGER.warn("gemini_config.json was unreadable ({}); restored the last good copy from the backup", String.valueOf(loaded.mainFailure()));
         this.quarantine();
         this.loadNotice = "§eGemini §7found a damaged config and restored your settings from the last good backup.";
      }

      JsonObject root = loaded.json();
      List<String> upgrades = List.of();
      this.suppressAutosave = true;

      try {
         int fileVersion = ConfigMigrations.versionOf(root);
         if (fileVersion > ConfigMigrations.CURRENT_VERSION) {
            this.backupNewer(fileVersion);
         }

         upgrades = ConfigMigrations.migrate(root);
         this.readSandbox(root);
         this.readMenuKey(root);
         this.readPanels(root);
         this.applyModules(root, true);
         Gemini.LOGGER.info("Loaded module and UI config from {}", this.configPath);
      } catch (RuntimeException e) {
         Gemini.LOGGER.error("gemini_config.json has an unexpected shape; keeping a copy and using defaults for the rest", e);
         this.quarantine();
         this.loadNotice = "§eGemini §7could not apply part of its config; the file was kept as §fgemini_config.json.broken§7.";
      } finally {
         this.suppressAutosave = false;
      }

      if (!upgrades.isEmpty()) {
         Gemini.LOGGER.info("Upgraded gemini_config.json to version {}: {}", ConfigMigrations.CURRENT_VERSION, String.join("; ", upgrades));
      }

      if (!upgrades.isEmpty() || loaded.fromBackup() || this.loadNotice != null) {
         this.save();
      }
   }

   private void backupNewer(int fileVersion) {
      Path backup = this.configPath.resolveSibling("gemini_config.json.v" + fileVersion + ".bak");

      try {
         Files.copy(this.configPath, backup, StandardCopyOption.REPLACE_EXISTING);
         Gemini.LOGGER
            .warn(
               "gemini_config.json was written by a newer Gemini (config version {}, this build writes {}). A copy is in {}.",
               new Object[]{fileVersion, ConfigMigrations.CURRENT_VERSION, backup}
            );
      } catch (IOException e) {
         Gemini.LOGGER.warn("Could not back up the newer config before loading it", e);
      }
   }

   /** Keeps an unreadable config as {@code .broken} (a copy, so the next save still has a file to back up). */
   private void quarantine() {
      if (Files.isRegularFile(this.configPath)) {
         try {
            Files.copy(this.configPath, this.configPath.resolveSibling("gemini_config.json.broken"), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            Gemini.LOGGER.warn("Could not keep a copy of the unreadable config", e);
         }
      }
   }

   private void readSandbox(JsonObject root) {
      if (root.has(SANDBOX_KEY) && root.get(SANDBOX_KEY).isJsonPrimitive()) {
         try {
            SingleplayerGate.loadSandboxMode(root.get(SANDBOX_KEY).getAsBoolean());
         } catch (RuntimeException e) {
            Gemini.LOGGER.warn("Ignoring invalid sandboxMode in config; sandbox mode stays on");
         }
      }
   }

   private void readMenuKey(JsonObject root) {
      try {
         if (root.has("menuKey") && root.get("menuKey").isJsonPrimitive()) {
            String keySpec = root.get("menuKey").getAsString();
            if (keySpec != null && !keySpec.isBlank()) {
               GeminiKeys.OPEN_GUI.setKey(InputConstants.getKey(keySpec));
               KeyMapping.resetMapping();
            }
         } else if (root.has("menuKeyCode") && root.get("menuKeyCode").isJsonPrimitive()) {
            int code = root.get("menuKeyCode").getAsInt();
            if (code > 0) {
               GeminiKeys.OPEN_GUI.setKey(Type.KEYBOARD.getOrCreate(Keys.fromGlfw(code)));
               KeyMapping.resetMapping();
            }
         }
      } catch (RuntimeException var3) {
         Gemini.LOGGER.warn("Skipping invalid menuKey in config");
      }
   }

   private void readPanels(JsonObject root) {
      if (root.has("panels") && root.get("panels").isJsonObject()) {
         JsonObject object = root.getAsJsonObject("panels");

         for (String key : object.keySet()) {
            try {
               JsonObject entry = object.getAsJsonObject(key);
               ConfigManager.PanelState state = this.panels.computeIfAbsent(key, k -> new ConfigManager.PanelState());
               state.x = intOr(entry, "x", 0);
               state.y = intOr(entry, "y", 0);
               state.width = intOr(entry, "w", 0);
               state.height = intOr(entry, "h", 0);
               state.collapsed = entry.has("collapsed") && entry.get("collapsed").getAsBoolean();
               state.placed = entry.has("x") && entry.has("y");
            } catch (RuntimeException var7) {
               Gemini.LOGGER.warn("Skipping bad panel entry {}", key);
            }
         }
      }
   }

   private static int intOr(JsonObject object, String key, int fallback) {
      return object.has(key) ? object.get(key).getAsInt() : fallback;
   }

   /** Applies module state from a config or profile. {@code readExtras} is false for portable profiles, which carry no waypoints, home or similar data. */
   private void applyModules(JsonObject root, boolean readExtras) {
      if (root.has("modules") && root.get("modules").isJsonObject()) {
         JsonObject modulesObject = root.getAsJsonObject("modules");
         Map<String, JsonObject> byNormalizedModule = new HashMap<>();

         for (String key : modulesObject.keySet()) {
            JsonElement el = modulesObject.get(key);
            if (el != null && el.isJsonObject()) {
               byNormalizedModule.put(normalizeId(key), el.getAsJsonObject());
            }
         }

         for (Module module : this.moduleManager.getModules()) {
            JsonObject moduleObject = modulesObject.has(module.getName()) && modulesObject.get(module.getName()).isJsonObject()
               ? modulesObject.getAsJsonObject(module.getName())
               : byNormalizedModule.get(normalizeId(module.getName()));
            if (moduleObject != null) {
               try {
                  if (moduleObject.has("settings") && moduleObject.get("settings").isJsonObject()) {
                     readSettings(module, moduleObject.getAsJsonObject("settings"));
                  }

                  if (readExtras) {
                     module.readExtra(moduleObject);
                  }

                  int keyx = moduleObject.has("keybind") ? sanitizeKeycode(moduleObject.get("keybind").getAsInt()) : module.getKeybind();
                  boolean enabled = moduleObject.has("enabled") && moduleObject.get("enabled").getAsBoolean();
                  module.loadState(enabled, keyx);
               } catch (RuntimeException e) {
                  Gemini.LOGGER.warn("Skipping unreadable config for module {}", module.getName(), e);
               }
            }
         }
      }
   }

   private static int sanitizeKeycode(int key) {
      return key <= 0 ? -1 : key;
   }

   private static void readSettings(Module module, JsonObject settings) {
      Map<String, JsonElement> normalizedMap = new HashMap<>();

      for (String key : settings.keySet()) {
         normalizedMap.put(normalizeId(key), settings.get(key));
      }

      for (Setting<?> setting : module.settings()) {
         JsonElement value = settings.has(setting.name()) ? settings.get(setting.name()) : normalizedMap.get(normalizeId(setting.name()));
         if (value != null) {
            try {
               if (setting instanceof BooleanSetting bool) {
                  bool.set(value.getAsBoolean());
               } else if (setting instanceof NumberSetting number) {
                  number.set(value.getAsDouble());
               } else if (setting instanceof ColorSetting color) {
                  color.set(value.getAsInt());
               } else if (setting instanceof ModeSetting mode) {
                  mode.set(value.getAsString());
               } else if (setting instanceof ListSetting list) {
                  List<String> entries = new ArrayList<>();

                  for (JsonElement entry : value.getAsJsonArray()) {
                     entries.add(entry.getAsString());
                  }

                  list.set(entries);
               }
            } catch (RuntimeException var141) {
               Gemini.LOGGER.warn("Ignoring bad value for {}.{}", module.getName(), setting.name());
            }
         }
      }
   }

   private JsonObject buildModules(boolean includeEnabledState) {
      JsonObject modulesObject = new JsonObject();

      for (Module module : this.moduleManager.getModules()) {
         JsonObject moduleObject = new JsonObject();
         moduleObject.addProperty("id", normalizeId(module.getName()));
         if (includeEnabledState) {
            moduleObject.addProperty("enabled", module.isSavedEnabled());
         }

         moduleObject.addProperty("keybind", sanitizeKeycode(module.getKeybind()));
         JsonObject settings = new JsonObject();

         for (Setting<?> setting : module.settings()) {
            if (setting instanceof BooleanSetting bool) {
               settings.addProperty(setting.name(), bool.get());
            } else if (setting instanceof NumberSetting number) {
               settings.addProperty(setting.name(), number.get());
            } else if (setting instanceof ColorSetting color) {
               settings.addProperty(setting.name(), color.get());
            } else if (setting instanceof ModeSetting mode) {
               settings.addProperty(setting.name(), mode.get());
            } else if (setting instanceof ListSetting list) {
               JsonArray entries = new JsonArray();
               list.get().forEach(entries::add);
               settings.add(setting.name(), entries);
            }
         }

         moduleObject.add("settings", settings);
         module.writeExtra(moduleObject);
         modulesObject.add(module.getName(), moduleObject);
      }

      return modulesObject;
   }

   private JsonObject buildPanels() {
      JsonObject panelsObject = new JsonObject();

      for (Entry<String, ConfigManager.PanelState> entry : this.panels.entrySet()) {
         ConfigManager.PanelState state = entry.getValue();
         JsonObject panel = new JsonObject();
         if (state.placed) {
            panel.addProperty("x", state.x);
            panel.addProperty("y", state.y);
         }

         panel.addProperty("w", state.width);
         panel.addProperty("h", state.height);
         panel.addProperty("collapsed", state.collapsed);
         panelsObject.add(entry.getKey(), panel);
      }

      return panelsObject;
   }

   public void setSavingPaused(boolean paused) {
      this.savingPaused = paused;
   }

   /**
    * Marks the config as changed. The file is written by {@link #tick()} once changes stop for a moment,
    * so dragging a slider or a HUD widget no longer rewrites the whole config on every mouse event.
    */
   public void save() {
      if (!this.suppressAutosave && !this.savingPaused) {
         long now = System.currentTimeMillis();
         if (!this.dirty) {
            this.dirtySince = now;
         }

         this.dirty = true;
         this.lastChange = now;
      }
   }

   /** Called every client tick: writes pending changes after a short quiet period (and at least every few seconds). */
   public void tick() {
      long now = System.currentTimeMillis();
      if (this.dirty && (now - this.lastChange >= SAVE_QUIET_MS || now - this.dirtySince >= SAVE_MAX_DELAY_MS || now < this.lastChange)) {
         this.flush();
      }

      if (this.writer.hasFailures()) {
         if (now - this.lastRetry >= RETRY_MS || now < this.lastRetry) {
            this.lastRetry = now;
            this.writer.retryFailed();
         }
      } else if (this.writeProblem != null && this.writer.awaitIdle(0L)) {
         // The retry went through: forget the problem so a later failure is reported again.
         this.writeProblem = null;
      }

      if (Minecraft.getInstance().player != null) {
         if (this.loadNotice != null) {
            CommandManager.feedback(this.loadNotice);
            this.loadNotice = null;
         }

         String problem = this.writeProblem;
         if (problem != null && !this.writeProblemShown) {
            this.writeProblemShown = true;
            CommandManager.feedback("§cGemini §7could not save its config (" + problem + "). It will keep trying; your settings are safe in memory.");
         }
      }
   }

   /** Hands pending changes to the background writer right away. */
   public void flush() {
      if (this.dirty && !this.suppressAutosave && !this.savingPaused) {
         this.dirty = false;
         this.writer.submit(this.configPath, JsonFiles.GSON.toJson(this.buildRoot()), true);
      }
   }

   /** Writes everything that is pending and waits for it to reach the disk; used when the game closes. */
   public void shutdown() {
      this.savingPaused = false;
      this.suppressAutosave = false;
      this.dirty = true;
      this.flush();
      this.writer.retryFailed();
      if (!this.writer.awaitIdle(SHUTDOWN_WAIT_MS)) {
         Gemini.LOGGER.warn("Timed out waiting for gemini_config.json to be written");
      }
   }

   private JsonObject buildRoot() {
      JsonObject root = new JsonObject();
      root.addProperty("version", ConfigMigrations.CURRENT_VERSION);
      root.addProperty(SANDBOX_KEY, SingleplayerGate.sandboxMode());
      root.addProperty("menuKey", GeminiKeys.OPEN_GUI.saveString());
      root.add("modules", this.buildModules(true));
      root.add("panels", this.buildPanels());
      return root;
   }

   /** Runs on the IO thread. */
   private void writeFailed(Path target, IOException error) {
      Gemini.LOGGER.error("Failed to write {}", target, error);
      if (target.equals(this.configPath)) {
         String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
         if (this.writeProblem == null) {
            this.writeProblemShown = false;
         }

         this.writeProblem = reason;
      } else {
         ClientThread.run(() -> CommandManager.feedback("§cGemini §7could not write §f" + target.getFileName() + "§7: " + error.getMessage()));
      }
   }

   private static String sanitize(String name) {
      if (name == null) {
         return null;
      }

      String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
      if (clean.length() > 48) {
         clean = clean.substring(0, 48);
      }

      return clean.isEmpty() ? null : clean;
   }

   private Path profilePath(String clean) {
      return this.profileDir.resolve(clean + ".json");
   }

   public boolean saveProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return false;
      }

      JsonObject root = new JsonObject();
      root.addProperty("version", ConfigMigrations.CURRENT_VERSION);
      root.addProperty("presetName", clean);
      root.addProperty("menuKey", GeminiKeys.OPEN_GUI.saveString());
      root.add("modules", this.buildModules(true));
      root.add("panels", this.buildPanels());
      this.writer.submit(this.profilePath(clean), JsonFiles.GSON.toJson(root), false);
      return true;
   }

   public boolean loadProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return false;
      }

      // A profile saved a moment ago may still be on its way to the disk.
      this.writer.awaitIdle(2000L);
      Path path = this.profilePath(clean);
      if (!Files.isRegularFile(path)) {
         return false;
      }

      try {
         JsonObject root = JsonFiles.read(path);
         ConfigMigrations.migrate(root);
         boolean portable = ProfileBundle.isPortable(root);
         this.suppressAutosave = true;

         try {
            if (!portable) {
               this.readMenuKey(root);
               this.readPanels(root);
            }

            this.applyModules(root, !portable);
         } finally {
            this.suppressAutosave = false;
         }

         this.save();
         return true;
      } catch (IOException | RuntimeException e) {
         Gemini.LOGGER.error("Failed to load profile {}", clean, e);
         return false;
      }
   }

   public boolean deleteProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return false;
      }

      this.writer.awaitIdle(2000L);
      try {
         return Files.deleteIfExists(this.profilePath(clean));
      } catch (IOException e) {
         Gemini.LOGGER.warn("Could not delete profile {}", clean, e);
         return false;
      }
   }

   /** The current settings as a portable bundle (no waypoints, home, panel layout or sandbox switch). */
   public JsonObject exportBundle(String name) {
      String clean = sanitize(name);
      return ProfileBundle.wrap(
         clean == null ? "profile" : clean, Gemini.VERSION, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), this.buildModules(true)
      );
   }

   /** Writes the current settings to {@code gemini_exports/<name>.json} and returns the file, or null for a bad name. */
   public Path exportProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return null;
      }

      Path target = this.exportDir.resolve(clean + ".json");
      this.writer.submit(target, JsonFiles.GSON.toJson(this.exportBundle(clean)), false);
      return target;
   }

   /**
    * Stores an exported bundle (or any profile-shaped JSON) as the profile {@code name}. Nothing is applied until
    * the profile is loaded. Returns the number of modules it carries.
    */
   public int importProfile(String name, JsonObject document) throws ProfileBundle.FormatException {
      String clean = sanitize(name);
      if (clean == null) {
         throw new ProfileBundle.FormatException("the profile name needs letters or digits");
      }

      JsonObject profile = ProfileBundle.unwrap(document);
      profile.addProperty("presetName", clean);
      this.writer.submit(this.profilePath(clean), JsonFiles.GSON.toJson(profile), false);
      return ProfileBundle.moduleCount(profile);
   }

   /** Imports {@code gemini_exports/<name>.json}. */
   public int importProfileFile(String name) throws ProfileBundle.FormatException {
      String clean = sanitize(name);
      if (clean == null) {
         throw new ProfileBundle.FormatException("the profile name needs letters or digits");
      }

      this.writer.awaitIdle(2000L);
      Path source = this.exportDir.resolve(clean + ".json");
      if (!Files.isRegularFile(source)) {
         throw new ProfileBundle.FormatException("there is no " + this.exportDir.getFileName() + "/" + clean + ".json");
      }

      try {
         return this.importProfile(clean, JsonFiles.read(source));
      } catch (IOException | RuntimeException e) {
         throw new ProfileBundle.FormatException("the file could not be read as JSON");
      }
   }

   public List<String> exports() {
      return listJson(this.exportDir);
   }

   public List<String> profiles() {
      return listJson(this.profileDir);
   }

   private static List<String> listJson(Path dir) {
      List<String> names = new ArrayList<>();
      if (!Files.isDirectory(dir)) {
         return names;
      }

      try (Stream<Path> files = Files.list(dir)) {
         files.map(path -> path.getFileName().toString())
            .filter(file -> file.endsWith(".json"))
            .map(file -> file.substring(0, file.length() - 5))
            .sorted()
            .forEach(names::add);
      } catch (IOException e) {
         Gemini.LOGGER.warn("Could not list {}", dir, e);
      }

      return names;
   }

   public void resetAllSettings() {
      this.suppressAutosave = true;

      try {
         for (Module module : this.moduleManager.getModules()) {
            for (Setting<?> setting : module.settings()) {
               if (!(setting instanceof ActionSetting)) {
                  setting.reset();
               }
            }
         }
      } finally {
         this.suppressAutosave = false;
      }

      this.save();
   }

   public static final class PanelState {
      public int x;
      public int y;
      public int width;
      public int height;
      public boolean collapsed;
      public boolean placed;
   }
}
