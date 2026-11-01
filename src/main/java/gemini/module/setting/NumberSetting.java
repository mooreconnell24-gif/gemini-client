package gemini.module.setting;

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
      if (value != null && !value.isNaN()) {
         double clamped = Math.max(this.min, Math.min(this.max, value));
         double stepped = this.step > 0.0 ? this.min + Math.round((clamped - this.min) / this.step) * this.step : clamped;
         super.set(Math.max(this.min, Math.min(this.max, stepped)));
      }
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
