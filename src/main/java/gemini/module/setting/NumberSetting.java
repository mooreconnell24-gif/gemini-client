package gemini.module.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;

public final class NumberSetting extends Setting<Double> {
   private final double min;
   private final double max;
   private final double step;

   public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
      super(name, description, defaultValue);
      this.min = min;
      this.max = max;
      this.step = step;
   }

   public double min() {
      return this.min;
   }

   public double max() {
      return this.max;
   }

   public double step() {
      return this.step;
   }

   public void set(Double value) {
      if (value != null && !value.isNaN() && !value.isInfinite()) {
         double clamped = Math.max(this.min, Math.min(this.max, value));
         double stepped = this.step > 0.0 ? this.min + Math.round((clamped - this.min) / this.step) * this.step : clamped;
         super.set(this.tidy(Math.max(this.min, Math.min(this.max, stepped))));
      }
   }

   /** Decimal places that are meaningful for this setting (from its step and minimum). */
   public int decimals() {
      if (this.step <= 0.0) {
         return 3;
      }

      int stepScale = Math.max(0, BigDecimal.valueOf(this.step).stripTrailingZeros().scale());
      int minScale = Math.max(0, BigDecimal.valueOf(this.min).stripTrailingZeros().scale());
      return Math.min(6, Math.max(stepScale, minScale));
   }

   /** The value formatted with exactly as many decimals as the step allows, e.g. "0.3" instead of "0.30000000000000004". */
   public String display() {
      return BigDecimal.valueOf(this.get()).setScale(this.decimals(), RoundingMode.HALF_UP).toPlainString();
   }

   // Repeated "min + n * step" arithmetic leaves binary noise (0.1 + 2 * 0.1 = 0.30000000000000004), which
   // showed up in the menu and made isDefault() miss. Round to the setting's precision instead.
   private double tidy(double value) {
      return BigDecimal.valueOf(value).setScale(this.decimals(), RoundingMode.HALF_UP).doubleValue();
   }

   public void setFromPercent(double percent) {
      double clamped = Math.max(0.0, Math.min(1.0, percent));
      this.set(this.min + (this.max - this.min) * clamped);
   }

   public void nudge(int steps) {
      this.set(this.get() + steps * (this.step > 0.0 ? this.step : (this.max - this.min) / 100.0));
   }

   public double percent() {
      return this.max == this.min ? 0.0 : (this.get() - this.min) / (this.max - this.min);
   }

   public float floatValue() {
      return this.get().floatValue();
   }

   public int intValue() {
      return (int)Math.round(this.get());
   }

   public NumberSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }

   public NumberSetting onChange(Runnable listener) {
      super.onChange(listener);
      return this;
   }
}
