package gemini.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.Color;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.util.Util;

/**
 * A colour. The setting's value is the hue in degrees (0..360), which is all older configs stored; saturation,
 * brightness, alpha and the sync mode are kept beside it and saved only when they differ from the defaults, so a
 * config written by an older release still loads and a colour that was never customised still saves as one number.
 *
 * <p>{@link #argb()} is the colour to draw with right now: with sync {@code Rainbow} the hue cycles, with
 * {@code Pulse} the brightness breathes, and {@code Static} is the picked colour as is.
 */
public final class ColorSetting extends Setting<Integer> {
   public static final String STATIC = "Static";
   public static final String RAINBOW = "Rainbow";
   public static final String PULSE = "Pulse";
   private static final long RAINBOW_PERIOD_MS = 6000L;
   private static final long PULSE_PERIOD_MS = 1800L;
   private final float defaultSaturation;
   private final float defaultBrightness;
   private float saturation;
   private float brightness;
   private float alpha = 1.0F;
   private String sync = STATIC;

   public ColorSetting(String name, String description, int defaultHue) {
      this(name, description, defaultHue, 0.72F, 1.0F);
   }

   public ColorSetting(String name, String description, int defaultHue, float saturation, float brightness) {
      super(name, description, Math.floorMod(defaultHue, 361));
      this.defaultSaturation = clamp01(saturation);
      this.defaultBrightness = clamp01(brightness);
      this.saturation = this.defaultSaturation;
      this.brightness = this.defaultBrightness;
   }

   public void set(Integer value) {
      if (value != null) {
         super.set(Math.max(0, Math.min(360, value)));
      }
   }

   public void setFromPercent(double percent) {
      this.set((int)Math.round(Math.max(0.0, Math.min(1.0, percent)) * 360.0));
   }

   public double percent() {
      return this.get().intValue() / 360.0;
   }

   /** Hue 0..1. */
   public float hue() {
      return this.get().intValue() / 360.0F;
   }

   public float saturation() {
      return this.saturation;
   }

   public float brightness() {
      return this.brightness;
   }

   /** Opacity 0..1. */
   public float alpha() {
      return this.alpha;
   }

   public String sync() {
      return this.sync;
   }

   public void setSaturation(float value) {
      float clamped = clamp01(value);
      if (clamped != this.saturation) {
         this.saturation = clamped;
         this.changed();
      }
   }

   public void setBrightness(float value) {
      float clamped = clamp01(value);
      if (clamped != this.brightness) {
         this.brightness = clamped;
         this.changed();
      }
   }

   public void setAlpha(float value) {
      float clamped = clamp01(value);
      if (clamped != this.alpha) {
         this.alpha = clamped;
         this.changed();
      }
   }

   /** {@link #STATIC}, {@link #RAINBOW} or {@link #PULSE}; anything else is read as Static. */
   public void setSync(String mode) {
      String next = RAINBOW.equalsIgnoreCase(mode) ? RAINBOW : (PULSE.equalsIgnoreCase(mode) ? PULSE : STATIC);
      if (!next.equals(this.sync)) {
         this.sync = next;
         this.changed();
      }
   }

   /** Sets hue, saturation and brightness from an RGB colour, and the alpha from its top byte. */
   public void setArgb(int argb) {
      float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
      this.saturation = hsb[1];
      this.brightness = hsb[2];
      this.alpha = (argb >>> 24) / 255.0F;
      int hue = Math.round(hsb[0] * 360.0F);
      if (hue == this.get()) {
         this.changed();
      } else {
         this.set(hue);
      }
   }

   /** The picked colour, ignoring the sync mode (what the picker shows and what hex/copy use). */
   public int baseArgb() {
      return Math.round(this.alpha * 255.0F) << 24 | Color.HSBtoRGB(this.hue(), this.saturation, this.brightness) & 0xFFFFFF;
   }

   /** The colour to draw with right now, including the picked alpha and the sync mode. */
   public int argb() {
      float hue = this.hue();
      float bright = this.brightness;
      if (RAINBOW.equals(this.sync)) {
         hue = (Util.getMillis() % RAINBOW_PERIOD_MS) / (float)RAINBOW_PERIOD_MS;
      } else if (PULSE.equals(this.sync)) {
         double phase = (Util.getMillis() % PULSE_PERIOD_MS) / (double)PULSE_PERIOD_MS;
         bright *= 0.6F + 0.4F * (float)(0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
      }

      return Math.round(this.alpha * 255.0F) << 24 | Color.HSBtoRGB(hue, this.saturation, bright) & 0xFFFFFF;
   }

   /** {@link #argb()} with the alpha forced to opaque, for callers that apply their own opacity. */
   public int rgb() {
      return 0xFF000000 | this.argb();
   }

   /** "#RRGGBB", or "#AARRGGBB" when the colour is translucent. */
   public String hex() {
      int argb = this.baseArgb();
      return this.alpha >= 0.999F
         ? String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF)
         : String.format(Locale.ROOT, "#%08X", argb);
   }

   /** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (the '#' is optional). Returns false and changes nothing on bad input. */
   public boolean setHex(String text) {
      if (text == null) {
         return false;
      }

      String digits = text.trim();
      if (digits.startsWith("#")) {
         digits = digits.substring(1);
      }

      if (digits.length() == 3) {
         digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1) + digits.charAt(2) + digits.charAt(2);
      }

      if (digits.length() != 6 && digits.length() != 8) {
         return false;
      }

      try {
         long parsed = Long.parseLong(digits, 16);
         this.setArgb(digits.length() == 6 ? 0xFF000000 | (int)parsed : (int)parsed);
         return true;
      } catch (NumberFormatException e) {
         return false;
      }
   }

   @Override
   public void reset() {
      this.saturation = this.defaultSaturation;
      this.brightness = this.defaultBrightness;
      this.alpha = 1.0F;
      this.sync = STATIC;
      if (this.get().equals(this.defaultValue())) {
         this.changed();
      } else {
         super.reset();
      }
   }

   @Override
   public boolean isDefault() {
      return super.isDefault() && !this.customised();
   }

   private boolean customised() {
      return this.saturation != this.defaultSaturation || this.brightness != this.defaultBrightness || this.alpha != 1.0F || !STATIC.equals(this.sync);
   }

   /** The saved form: the bare hue when nothing else was customised, otherwise an object with every component. */
   public JsonElement toJson() {
      if (!this.customised()) {
         return new JsonPrimitive(this.get());
      }

      JsonObject object = new JsonObject();
      object.addProperty("hue", this.get());
      object.addProperty("saturation", this.saturation);
      object.addProperty("brightness", this.brightness);
      object.addProperty("alpha", this.alpha);
      object.addProperty("sync", this.sync);
      return object;
   }

   /** Reads either saved form. Missing components keep their defaults; throws on a value that is neither form. */
   public void fromJson(JsonElement element) {
      if (element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         this.saturation = object.has("saturation") ? clamp01(object.get("saturation").getAsFloat()) : this.defaultSaturation;
         this.brightness = object.has("brightness") ? clamp01(object.get("brightness").getAsFloat()) : this.defaultBrightness;
         this.alpha = object.has("alpha") ? clamp01(object.get("alpha").getAsFloat()) : 1.0F;
         String mode = object.has("sync") ? object.get("sync").getAsString() : STATIC;
         this.sync = RAINBOW.equalsIgnoreCase(mode) ? RAINBOW : (PULSE.equalsIgnoreCase(mode) ? PULSE : STATIC);
         int hue = object.has("hue") ? object.get("hue").getAsInt() : this.defaultValue();
         if (Math.max(0, Math.min(360, hue)) == this.get()) {
            this.changed();
         } else {
            this.set(hue);
         }
      } else {
         this.set(element.getAsInt());
      }
   }

   public static int hueColor(float hue, float saturation, float brightness) {
      return 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness) & 16777215;
   }

   private static float clamp01(float value) {
      return Float.isNaN(value) ? 0.0F : Math.max(0.0F, Math.min(1.0F, value));
   }

   public ColorSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }

   public ColorSetting onChange(Runnable listener) {
      super.onChange(listener);
      return this;
   }
}
