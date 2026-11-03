package gemini.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

public final class ConfigManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final ModuleManager moduleManager;
   private final Path configPath;
   private final Path profileDir;
   private final Map<String, ConfigManager.PanelState> panels = new LinkedHashMap<>();
   private static final long SAVE_QUIET_MS = 750L;
   private static final long SAVE_MAX_DELAY_MS = 5000L;
   private boolean suppressAutosave;
   private boolean savingPaused;
   private boolean dirty;
   private long dirtySince;
   private long lastChange;

   public ConfigManager(ModuleManager moduleManager) {
      this.moduleManager = moduleManager;
      Path dir = FabricLoader.getInstance().getConfigDir();
      this.configPath = dir.resolve("gemini_config.json");
      this.profileDir = dir.resolve("gemini_profiles");
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
      if (!Files.exists(this.configPath)) {
         this.save();
      } else {
         List<String> upgrades = List.of();
         this.suppressAutosave = true;

         try {
            JsonObject root = readJson(this.configPath);
            if (root == null) {
               return;
            }

            int fileVersion = ConfigMigrations.versionOf(root);
            if (fileVersion > 5) {
               this.backupNewer(fileVersion);
            }

            upgrades = ConfigMigrations.migrate(root);
            this.readMenuKey(root);
            this.readPanels(root);
            this.applyModules(root);
            Gemini.LOGGER.info("Loaded module and UI config from {}", this.configPath);
         } catch (Exception var7) {
            Gemini.LOGGER.error("Failed to load gemini_config.json, keeping a backup and using defaults", var7);
            this.backupCorrupt();
            this.panels.clear();
            this.suppressAutosave = false;
            this.save();
            return;
         } finally {
            this.suppressAutosave = false;
         }

         if (!upgrades.isEmpty()) {
            Gemini.LOGGER.info("Upgraded gemini_config.json to version {}: {}", 5, String.join("; ", upgrades));
            this.save();
         }
      }
   }

   private void backupNewer(int fileVersion) {
      Path backup = this.configPath.resolveSibling("gemini_config.json.v" + fileVersion + ".bak");

      try {
         Files.copy(this.configPath, backup, StandardCopyOption.REPLACE_EXISTING);
         Gemini.LOGGER
            .warn(
               "gemini_config.json was written by a newer Gemini (config version {}, this build writes {}). A copy is in {}.",
               new Object[]{fileVersion, 5, backup}
            );
      } catch (IOException var4) {
         Gemini.LOGGER.warn("Could not back up the newer config before loading it", var4);
      }
   }

   private static JsonObject readJson(Path path) throws IOException {
      JsonObject var2;
      try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
         var2 = (JsonObject)GSON.fromJson(reader, JsonObject.class);
      }

      return var2;
   }

   private void backupCorrupt() {
      try {
         Files.move(this.configPath, this.configPath.resolveSibling("gemini_config.json.broken"), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var2) {
         Gemini.LOGGER.warn("Could not back up the broken config");
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
               GeminiKeys.OPEN_GUI.setKey(Type.KEYSYM.getOrCreate(code));
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

   private void applyModules(JsonObject root) {
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

                  module.readExtra(moduleObject);
                  int keyx = moduleObject.has("keybind") ? sanitizeKeycode(moduleObject.get("keybind").getAsInt()) : module.getKeybind();
                  boolean enabled = moduleObject.has("enabled") && moduleObject.get("enabled").getAsBoolean();
                  module.loadState(enabled, keyx);
               } catch (RuntimeException var9) {
                  Gemini.LOGGER.warn("Skipping unreadable config for module {}", module.getName(), var9);
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
      if (this.dirty) {
         long now = System.currentTimeMillis();
         if (now - this.lastChange >= SAVE_QUIET_MS || now - this.dirtySince >= SAVE_MAX_DELAY_MS || now < this.lastChange) {
            this.flush();
         }
      }
   }

   /** Writes pending changes to disk right away. */
   public void flush() {
      if (this.dirty && !this.suppressAutosave && !this.savingPaused) {
         this.dirty = false;
         JsonObject root = new JsonObject();
         root.addProperty("version", 5);
         root.addProperty("menuKey", GeminiKeys.OPEN_GUI.saveString());
         root.add("modules", this.buildModules(true));
         root.add("panels", this.buildPanels());
         writeAtomically(this.configPath, root);
      }
   }

   private static void writeAtomically(Path target, JsonObject root) {
      try {
         Files.createDirectories(target.getParent());
         Path temp = target.resolveSibling(target.getFileName() + ".tmp");
         Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);

         try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var4) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException var5) {
         Gemini.LOGGER.error("Failed to write {}", target, var5);
      }
   }

   private static String sanitize(String name) {
      String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
      return clean.isEmpty() ? null : clean;
   }

   public boolean saveProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return false;
      } else {
         JsonObject root = new JsonObject();
         root.addProperty("version", 5);
         root.addProperty("presetName", clean);
         root.addProperty("menuKey", GeminiKeys.OPEN_GUI.saveString());
         root.add("modules", this.buildModules(true));
         root.add("panels", this.buildPanels());
         writeAtomically(this.profileDir.resolve(clean + ".json"), root);
         return true;
      }
   }

   public boolean loadProfile(String name) {
      String clean = sanitize(name);
      if (clean == null) {
         return false;
      } else {
         Path path = this.profileDir.resolve(clean + ".json");
         if (!Files.exists(path)) {
            return false;
         } else {
            try {
               JsonObject root = readJson(path);
               if (root == null) {
                  return false;
               } else {
                  ConfigMigrations.migrate(root);
                  this.suppressAutosave = true;

                  try {
                     this.readMenuKey(root);
                     this.readPanels(root);
                     this.applyModules(root);
                  } finally {
                     this.suppressAutosave = false;
                  }

                  this.save();
                  return true;
               }
            } catch (Exception var9) {
               Gemini.LOGGER.error("Failed to load profile {}", clean, var9);
               return false;
            }
         }
      }
   }

   public List<String> profiles() {
      List<String> names = new ArrayList<>();
      if (!Files.isDirectory(this.profileDir)) {
         return names;
      } else {
         try (Stream<Path> files = Files.list(this.profileDir)) {
            files.<String>map(path -> path.getFileName().toString())
               .filter(file -> file.endsWith(".json"))
               .map(file -> file.substring(0, file.length() - 5))
               .sorted()
               .forEach(names::add);
         } catch (IOException var7) {
            Gemini.LOGGER.warn("Could not list profiles");
         }

         return names;
      }
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
