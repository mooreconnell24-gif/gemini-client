package gemini.module.setting;

import java.util.function.BooleanSupplier;

public final class ActionSetting extends Setting<String> {
   private final Runnable action;

   public ActionSetting(String name, String description, Runnable action) {
      super(name, description, name);
      this.action = action;
   }

   public void run() {
      this.action.run();
   }

   @Override
   public void reset() {
   }

   public ActionSetting showIf(BooleanSupplier supplier) {
      this.setVisibleWhen(supplier);
      return this;
   }
}
