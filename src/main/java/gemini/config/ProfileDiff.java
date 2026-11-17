package gemini.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What loading a profile would change, worked out before anything is applied: the preview shown for a share code
 * or clipboard import. Both sides are {@code modules} objects in the config shape (module name to
 * {@code {enabled, keybind, settings}}); module names are matched the same forgiving way the loader matches them.
 */
public record ProfileDiff(
   int modules,
   List<String> unknown,
   List<String> switchedOn,
   List<String> switchedOff,
   List<String> rebound,
   int settingsChanged,
   int modulesWithSettingChanges
) {
   /** Compares {@code current} (this game's modules) with {@code incoming} (the profile's modules). */
   public static ProfileDiff compute(JsonObject current, JsonObject incoming) {
      Map<String, String> byId = new HashMap<>();
      if (current != null) {
         for (String name : current.keySet()) {
            byId.put(ConfigMigrations.normalizeId(name), name);
         }
      }

      List<String> unknown = new ArrayList<>();
      List<String> on = new ArrayList<>();
      List<String> off = new ArrayList<>();
      List<String> rebound = new ArrayList<>();
      int settings = 0;
      int touched = 0;
      int count = 0;
      if (incoming != null) {
         for (Map.Entry<String, JsonElement> entry : incoming.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
               continue;
            }

            count++;
            String name = current != null && current.has(entry.getKey()) ? entry.getKey() : byId.get(ConfigMigrations.normalizeId(entry.getKey()));
            if (name == null || !current.get(name).isJsonObject()) {
               unknown.add(entry.getKey());
               continue;
            }

            JsonObject now = current.getAsJsonObject(name);
            JsonObject next = entry.getValue().getAsJsonObject();
            // The loader treats a missing "enabled" as off, so the preview does too.
            boolean wasOn = bool(now, "enabled");
            boolean willBeOn = bool(next, "enabled");
            if (willBeOn && !wasOn) {
               on.add(name);
            } else if (!willBeOn && wasOn) {
               off.add(name);
            }

            if (next.has("keybind") && key(next) != key(now)) {
               rebound.add(name);
            }

            int changed = changedSettings(object(now, "settings"), object(next, "settings"));
            if (changed > 0) {
               settings += changed;
               touched++;
            }
         }
      }

      return new ProfileDiff(count, List.copyOf(unknown), List.copyOf(on), List.copyOf(off), List.copyOf(rebound), settings, touched);
   }

   /** Nothing would change. */
   public boolean empty() {
      return this.switchedOn.isEmpty() && this.switchedOff.isEmpty() && this.rebound.isEmpty() && this.settingsChanged == 0;
   }

   /** One line for a toast or chat: "142 modules · 3 on, 1 off · 2 key binds · 17 settings in 5 modules". */
   public String summary() {
      if (this.empty()) {
         return this.modules + plural(this.modules, " module", " modules") + " · identical to your current settings";
      }

      List<String> parts = new ArrayList<>();
      parts.add(this.modules + plural(this.modules, " module", " modules"));
      if (!this.switchedOn.isEmpty() || !this.switchedOff.isEmpty()) {
         parts.add(this.switchedOn.size() + " on, " + this.switchedOff.size() + " off");
      }

      if (!this.rebound.isEmpty()) {
         parts.add(this.rebound.size() + plural(this.rebound.size(), " key bind", " key binds"));
      }

      if (this.settingsChanged > 0) {
         parts.add(this.settingsChanged + plural(this.settingsChanged, " setting", " settings") + " in " + this.modulesWithSettingChanges
            + plural(this.modulesWithSettingChanges, " module", " modules"));
      }

      return String.join(" · ", parts);
   }

   /** Up to {@code max} names joined with commas, then "+N more". */
   public static String names(List<String> names, int max) {
      if (names.isEmpty()) {
         return "none";
      }

      int shown = Math.min(Math.max(1, max), names.size());
      String head = String.join(", ", names.subList(0, shown));
      return shown < names.size() ? head + " +" + (names.size() - shown) + " more" : head;
   }

   private static int changedSettings(JsonObject now, JsonObject next) {
      if (next == null) {
         return 0;
      }

      Map<String, JsonElement> current = new HashMap<>();
      if (now != null) {
         for (Map.Entry<String, JsonElement> entry : now.entrySet()) {
            current.put(ConfigMigrations.normalizeId(entry.getKey()), entry.getValue());
         }
      }

      int changed = 0;
      for (Map.Entry<String, JsonElement> entry : next.entrySet()) {
         JsonElement was = now != null && now.has(entry.getKey()) ? now.get(entry.getKey()) : current.get(ConfigMigrations.normalizeId(entry.getKey()));
         // A setting this build does not have is ignored by the loader, so it changes nothing.
         if (was != null && !Objects.equals(was, entry.getValue())) {
            changed++;
         }
      }

      return changed;
   }

   private static boolean bool(JsonObject object, String key) {
      try {
         return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
      } catch (RuntimeException e) {
         return false;
      }
   }

   private static int key(JsonObject object) {
      try {
         int key = object.has("keybind") && object.get("keybind").isJsonPrimitive() ? object.get("keybind").getAsInt() : -1;
         return key <= 0 ? -1 : key;
      } catch (RuntimeException e) {
         return -1;
      }
   }

   private static JsonObject object(JsonObject parent, String key) {
      return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
   }

   private static String plural(int n, String one, String many) {
      return n == 1 ? one : many;
   }
}
