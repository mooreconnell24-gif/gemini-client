package gemini.module.setting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public final class ModeSetting extends Setting<String> {
   private final List<String> modes;
   /** Retired mode names (lower case) and the current mode each one now means. */
   private Map<String, String> aliases;

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
      } else if (value != null && this.aliases != null) {
         String current = this.aliases.get(value.toLowerCase(java.util.Locale.ROOT));
         if (current != null) {
            super.set(current);
         }
      }
   }

   /**
    * Keeps a retired mode name working: setting {@code retired} (any case, e.g. from an old config file) selects
    * {@code current} instead of being ignored.
    */
   public ModeSetting alias(String retired, String current) {
      if (!this.modes.contains(current)) {
         throw new IllegalArgumentException(current);
      }

      if (this.aliases == null) {
         this.aliases = new HashMap<>();
      }

      this.aliases.put(retired.toLowerCase(java.util.Locale.ROOT), current);
      return this;
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
