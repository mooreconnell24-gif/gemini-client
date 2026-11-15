package gemini.config;

import gemini.input.Keys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

/**
 * Upgrades saved configs and profiles to the current layout.
 *
 * <p>Every change to what a config means gets one numbered {@link Step}. A file records the version it was written
 * at; {@link #migrate} runs each later step in order, so a config from any older release arrives at the current
 * layout through the same path, and a new step can never be skipped or run twice. Steps only ever see the
 * {@code modules} object and must tolerate anything in it: configs are hand-edited.
 */
public final class ConfigMigrations {
   public static final int CURRENT_VERSION = 8;
   static final Set<String> REMOVED_IN_V5 = Set.of(
      "Disabler", "PingSpoof", "AntiBot", "AntiStaff", "FakeLag", "ResetVL", "Spammer", "AutoLeave", "AntiAFK", "Blink"
   );
   /** In version order. The last entry's version is {@link #CURRENT_VERSION}. */
   private static final List<Step> STEPS = List.of(
      new Step(5, ConfigMigrations::toV5),
      new Step(6, ConfigMigrations::toV6),
      new Step(7, ConfigMigrations::toV7),
      new Step(8, ConfigMigrations::toV8)
   );

   private ConfigMigrations() {
   }

   /** One upgrade: brings the {@code modules} object to {@code version} and notes what it changed. */
   private record Step(int version, BiConsumer<JsonObject, List<String>> upgrade) {
   }

   public static int versionOf(JsonObject root) {
      if (root != null && root.has("version") && root.get("version").isJsonPrimitive()) {
         try {
            return root.get("version").getAsInt();
         } catch (RuntimeException e) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   /** Upgrades the config in place and returns a line per change made, for the log. Newer and current files are left alone. */
   public static List<String> migrate(JsonObject root) {
      List<String> notes = new ArrayList<>();
      int from = versionOf(root);
      if (root == null || from >= CURRENT_VERSION) {
         return notes;
      }

      JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : null;
      if (modules != null) {
         for (Step step : STEPS) {
            if (from < step.version()) {
               step.upgrade().accept(modules, notes);
            }
         }
      }

      root.addProperty("version", CURRENT_VERSION);
      return notes;
   }

   /**
    * ReverseStep became the "Ledges" mode of Fast Fall. Its switch, key bind and two settings move over; when both
    * modules were on, Fast Fall runs in "Both".
    */
   private static void toV8(JsonObject modules, List<String> notes) {
      String oldKey = findKey(modules, "ReverseStep");
      if (oldKey == null) {
         return;
      }

      JsonElement old = modules.remove(oldKey);
      if (old == null || !old.isJsonObject()) {
         return;
      }

      JsonObject from = old.getAsJsonObject();
      String newKey = findKey(modules, "Fast Fall");
      JsonObject to;
      if (newKey != null && modules.get(newKey).isJsonObject()) {
         to = modules.getAsJsonObject(newKey);
      } else {
         to = new JsonObject();
         modules.add("Fast Fall", to);
      }

      JsonObject settings;
      if (to.has("settings") && to.get("settings").isJsonObject()) {
         settings = to.getAsJsonObject("settings");
      } else {
         settings = new JsonObject();
         to.add("settings", settings);
      }

      JsonObject oldSettings = from.has("settings") && from.get("settings").isJsonObject() ? from.getAsJsonObject("settings") : new JsonObject();
      copySetting(oldSettings, "Height", settings, "Ledge Height");
      copySetting(oldSettings, "Speed", settings, "Ledge Speed");
      boolean reverseOn = isOn(from);
      boolean fastOn = isOn(to);
      if (reverseOn) {
         putSetting(settings, "When", new JsonPrimitive(fastOn ? "Both" : "Ledges"));
         to.addProperty("enabled", true);
      }

      if (!isBound(to) && from.has("keybind") && from.get("keybind").isJsonPrimitive()) {
         to.add("keybind", from.get("keybind"));
      }

      notes.add("ReverseStep was merged into Fast Fall" + (reverseOn ? " (mode " + (fastOn ? "Both" : "Ledges") + ")" : ""));
   }

   private static boolean isOn(JsonObject module) {
      try {
         return module.has("enabled") && module.get("enabled").isJsonPrimitive() && module.get("enabled").getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static boolean isBound(JsonObject module) {
      try {
         return module.has("keybind") && module.get("keybind").isJsonPrimitive() && module.get("keybind").getAsInt() > 0;
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static void copySetting(JsonObject from, String name, JsonObject to, String newName) {
      String key = findKey(from, name);
      if (key != null && from.get(key).isJsonPrimitive()) {
         putSetting(to, newName, from.get(key));
      }
   }

   /** AutoClutch was folded into Clutch: carry its on/off state and key bind over, then drop it. */
   private static void toV7(JsonObject modules, List<String> notes) {
      String oldKey = findKey(modules, "AutoClutch");
      if (oldKey == null) {
         return;
      }

      JsonElement old = modules.remove(oldKey);
      if (old != null && old.isJsonObject()) {
         JsonObject from = old.getAsJsonObject();
         String newKey = findKey(modules, "Clutch");
         JsonObject to;
         if (newKey != null && modules.get(newKey).isJsonObject()) {
            to = modules.getAsJsonObject(newKey);
         } else {
            to = new JsonObject();
            modules.add("Clutch", to);
         }

         if (isOn(from)) {
            to.addProperty("enabled", true);
         }

         if (!isBound(to) && from.has("keybind") && from.get("keybind").isJsonPrimitive()) {
            to.add("keybind", from.get("keybind"));
         }
      }

      notes.add("AutoClutch was merged into Clutch");
   }

   /** Minecraft 26.3 moved from GLFW key codes to SDL scancodes; convert saved module keybinds. */
   private static void toV6(JsonObject modules, List<String> notes) {
      int converted = 0;

      for (Entry<String, JsonElement> entry : modules.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            JsonObject module = entry.getValue().getAsJsonObject();
            if (module.has("keybind") && module.get("keybind").isJsonPrimitive()) {
               try {
                  int old = module.get("keybind").getAsInt();
                  if (old > 0) {
                     module.addProperty("keybind", Keys.fromGlfw(old));
                     converted++;
                  }
               } catch (RuntimeException ignored) {
               }
            }
         }
      }

      if (converted > 0) {
         notes.add("Converted " + converted + " keybinds to Minecraft 26.3 key codes");
      }
   }

   private static void toV5(JsonObject modules, List<String> notes) {
      for (String removed : REMOVED_IN_V5) {
         String key = findKey(modules, removed);
         if (key != null) {
            modules.remove(key);
            notes.add("Removed module " + removed + " (not available in single-player builds)");
         }
      }

      remapMode(modules, "Speed", "Mode", Set.of("Vulcan", "Verus", "GrimCollide"), "LowHop", notes);
      remapMode(modules, "Flight", "Mode", Set.of("VulcanGlide"), "Glide", notes);
      remapMode(modules, "Scaffold", "Tower", Set.of("Bypass"), "Motion", notes);
      remapMode(modules, "KillAura", "Version", Set.of("Auto"), "1.9+ Cooldown", notes);
      dropSetting(modules, "Flight", "Anti Kick", notes);
      dropSetting(modules, "Boat Fly", "Anti Kick", notes);
      dropSetting(modules, "Spider", "Mode", notes);
      dropSetting(modules, "KillAura", "Bypass Mode", notes);
      JsonObject velocity = settingsOf(modules, "Velocity");
      String velocityMode = velocity == null ? null : stringSetting(velocity, "Mode");
      if ("Grim".equals(velocityMode)) {
         putSetting(velocity, "Mode", new JsonPrimitive("Jump"));
         putSetting(velocity, "Horizontal", new JsonPrimitive(0.0));
         putSetting(velocity, "Vertical", new JsonPrimitive(100.0));
         notes.add("Velocity mode Grim -> Jump (0% horizontal, 100% vertical)");
      } else if ("Vulcan".equals(velocityMode)) {
         putSetting(velocity, "Mode", new JsonPrimitive("Packet"));
         putSetting(velocity, "Horizontal", new JsonPrimitive(45.0));
         putSetting(velocity, "Vertical", new JsonPrimitive(55.0));
         notes.add("Velocity mode Vulcan -> Packet (45% horizontal, 55% vertical)");
      }
   }

   private static void remapMode(JsonObject modules, String module, String setting, Set<String> oldValues, String replacement, List<String> notes) {
      JsonObject settings = settingsOf(modules, module);
      String current = settings == null ? null : stringSetting(settings, setting);
      if (current != null && oldValues.contains(current)) {
         putSetting(settings, setting, new JsonPrimitive(replacement));
         notes.add(module + " " + setting + " " + current + " -> " + replacement);
      }
   }

   private static void dropSetting(JsonObject modules, String module, String setting, List<String> notes) {
      JsonObject settings = settingsOf(modules, module);
      String key = settings == null ? null : findKey(settings, setting);
      if (key != null) {
         settings.remove(key);
         notes.add("Removed setting " + module + "." + setting);
      }
   }

   private static JsonObject settingsOf(JsonObject modules, String module) {
      String key = findKey(modules, module);
      if (key != null && modules.get(key).isJsonObject()) {
         JsonObject moduleObject = modules.getAsJsonObject(key);
         return moduleObject.has("settings") && moduleObject.get("settings").isJsonObject() ? moduleObject.getAsJsonObject("settings") : null;
      } else {
         return null;
      }
   }

   private static String stringSetting(JsonObject settings, String name) {
      String key = findKey(settings, name);
      if (key == null) {
         return null;
      } else {
         JsonElement value = settings.get(key);
         return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
      }
   }

   private static void putSetting(JsonObject settings, String name, JsonElement value) {
      String key = findKey(settings, name);
      settings.add(key == null ? name : key, value);
   }

   public static String normalizeId(String raw) {
      return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   static String findKey(JsonObject object, String name) {
      if (object.has(name)) {
         return name;
      } else {
         String wanted = normalizeId(name);

         for (Entry<String, JsonElement> entry : object.entrySet()) {
            if (normalizeId(entry.getKey()).equals(wanted)) {
               return entry.getKey();
            }
         }

         return null;
      }
   }
}
