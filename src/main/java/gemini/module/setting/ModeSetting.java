package gemini.module.setting;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ModeSetting extends Setting<String> {
   private final List<String> modes;

   public ModeSetting(String name, String description, String defaultValue, String... modes) {
      super(name, description, defaultValue);
      this.modes = List.copyOf(Arrays.asList(modes));
      if (!this.modes.contains(defaultValue)) {
         throw new IllegalArgumentException(defaultValue);
      }
   }

   public List<String> modes() {
      return this.modes;
   }

   public void set(String value) {
      if (value != null && this.modes.contains(value)) {
         super.set(value);
      }
   }

   public void cycle() {
      int index = this.modes.indexOf(this.get());
      this.set(this.modes.get((index + 1) % this.modes.size()));
   }

   public void cycleBack() {
      int index = this.modes.indexOf(this.get());
      this.set(this.modes.get((index - 1 + this.modes.size()) % this.modes.size()));
   }

   public boolean is(String mode) {
      return this.get().equalsIgnoreCase(mode);
   }

   public ModeSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }

   public ModeSetting onChange(Runnable listener) {
      super.onChange(listener);
      return this;
   }
}
