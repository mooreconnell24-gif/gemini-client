package gemini.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * How a setting is written to and read from the config file. The config manager and the self-test both go through
 * here, so the self-test's round-trip check exercises exactly the code that saves and loads a profile.
 */
public final class SettingCodec {
   private SettingCodec() {
   }

   /** The saved form of the setting's current value, or null for a setting that is not saved (an action button). */
   public static JsonElement write(Setting<?> setting) {
      if (setting instanceof BooleanSetting bool) {
         return new JsonPrimitive(bool.get());
      } else if (setting instanceof NumberSetting number) {
         return new JsonPrimitive(number.get());
      } else if (setting instanceof ColorSetting color) {
         return color.toJson();
      } else if (setting instanceof ModeSetting mode) {
         return new JsonPrimitive(mode.get());
      } else if (setting instanceof ListSetting list) {
         JsonArray entries = new JsonArray();
         list.get().forEach(entries::add);
         return entries;
      }

      return null;
   }

   /** Applies a saved value. Throws a {@link RuntimeException} when the value has the wrong shape. */
   public static void read(Setting<?> setting, JsonElement value) {
      if (setting instanceof BooleanSetting bool) {
         bool.set(value.getAsBoolean());
      } else if (setting instanceof NumberSetting number) {
         number.set(value.getAsDouble());
      } else if (setting instanceof ColorSetting color) {
         color.fromJson(value);
      } else if (setting instanceof ModeSetting mode) {
         mode.set(value.getAsString());
      } else if (setting instanceof ListSetting list) {
         List<String> entries = new ArrayList<>();
         for (JsonElement entry : value.getAsJsonArray()) {
            entries.add(entry.getAsString());
         }

         list.set(entries);
      }
   }

   /**
    * Writes the setting, reads that back, writes again and compares, then puts the original value back. Returns
    * null when the value survives the trip, or a short description of what changed.
    */
   public static String roundTrip(Setting<?> setting) {
      JsonElement original = write(setting);
      if (original == null) {
         return null;
      }

      try {
         read(setting, original.deepCopy());
         JsonElement again = write(setting);
         return original.equals(again) ? null : "saved " + original + " but read back " + again;
      } finally {
         read(setting, original);
      }
   }

   /**
    * The colour round trip with every component customised, so the object form (saturation, brightness, alpha,
    * sync) is exercised even when the colour itself is still at its default and would save as a bare hue.
    */
   public static String colorObjectRoundTrip(ColorSetting color) {
      JsonElement original = color.toJson();
      try {
         color.setSaturation(0.37F);
         color.setBrightness(0.61F);
         color.setAlpha(0.5F);
         color.setSync(ColorSetting.PULSE);
         JsonElement saved = color.toJson();
         if (!saved.isJsonObject()) {
            return "a customised colour saved as " + saved + " instead of an object";
         }

         color.reset();
         color.fromJson(saved);
         JsonElement again = color.toJson();
         return saved.equals(again) ? null : "saved " + saved + " but read back " + again;
      } finally {
         color.reset();
         color.fromJson(original);
      }
   }
}
