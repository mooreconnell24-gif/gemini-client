package gemini.module.setting;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
   private final String name;
   private final String description;
   private final T defaultValue;
   private T value;
   private BooleanSupplier visibleWhen = () -> true;
   private Runnable changeListener;

   protected Setting(String name, String description, T value) {
      this.name = name;
      this.description = description;
      this.defaultValue = value;
      this.value = value;
   }

   public String name() {
      return this.name;
   }

   public String description() {
      return this.description;
   }

   public T get() {
      return this.value;
   }

   public void set(T value) {
      if (!Objects.equals(this.value, value)) {
         this.value = value;
         this.changed();
      }
   }

   public T defaultValue() {
      return this.defaultValue;
   }

   public void reset() {
      this.set(this.defaultValue);
   }

   public boolean isDefault() {
      return Objects.equals(this.value, this.defaultValue);
   }

   public Setting<T> onChange(Runnable listener) {
      this.changeListener = listener;
      return this;
   }

   protected final void changed() {
      if (this.changeListener != null) {
         this.changeListener.run();
      }
   }

   protected void setVisibleWhen(BooleanSupplier supplier) {
      this.visibleWhen = supplier == null ? () -> true : supplier;
   }

   public boolean visible() {
      return this.visibleWhen.getAsBoolean();
   }
}
