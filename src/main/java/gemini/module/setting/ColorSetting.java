package gemini.module.setting;

import java.awt.Color;
import java.util.function.BooleanSupplier;

public final class ColorSetting extends Setting<Integer> {
   private final float saturation;
   private final float brightness;

   public ColorSetting(String name, String description, int defaultHue) {
      this(name, description, defaultHue, 0.72F, 1.0F);
   }

   public ColorSetting(String name, String description, int defaultHue, float saturation, float brightness) {
      super(name, description, Math.floorMod(defaultHue, 361));
      this.saturation = saturation;
      this.brightness = brightness;
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

   public int argb() {
      return 0xFF000000 | Color.HSBtoRGB(this.get().intValue() / 360.0F, this.saturation, this.brightness) & 16777215;
   }

   public static int hueColor(float hue, float saturation, float brightness) {
      return 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness) & 16777215;
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
