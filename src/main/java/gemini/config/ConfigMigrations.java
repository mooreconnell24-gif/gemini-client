package gemini.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;

public final class ConfigMigrations {
   public static final int CURRENT_VERSION = 5;
   static final Set<String> REMOVED_IN_V5 = Set.of(
      "Disabler", "PingSpoof", "AntiBot", "AntiStaff", "FakeLag", "ResetVL", "Spammer", "AutoLeave", "AntiAFK", "Blink"
   );

   private ConfigMigrations() {
   }

   public static int versionOf(JsonObject root) {
      if (root != null && root.has("version") && root.get("version").isJsonPrimitive()) {
         try {
            return root.get("version").getAsInt();
         } catch (RuntimeException var2) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static List<String> migrate(JsonObject root) {
      List<String> notes = new ArrayList<>();
      int from = versionOf(root);
      if (root != null && from < 5) {
         JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : null;
         if (modules != null && from < 5) {
            toV5(modules, notes);
         }

         root.addProperty("version", 5);
         return notes;
      } else {
         return notes;
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
