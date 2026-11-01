package gemini.module.setting;

import java.util.function.BooleanSupplier;

public final class BooleanSetting extends Setting<Boolean> {
   public BooleanSetting(String name, String description, boolean defaultValue) {
      super(name, description, defaultValue);
   }

   public void toggle() {
      this.set(!this.get());
   }

   public BooleanSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }

   public BooleanSetting onChange(Runnable listener) {
      super.onChange(listener);
      return this;
   }
}
