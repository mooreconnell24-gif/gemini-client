package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;

public final class VelocityModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Knockback bypass mode", "Packet", "Packet", "Grim", "Vulcan", "Jump", "Reverse")
   );
   private final NumberSetting horizontal = this.addSetting(
      new NumberSetting("Horizontal", "Horizontal knockback %", 0.0, 0.0, 100.0, 1.0)
   );
   private final NumberSetting vertical = this.addSetting(new NumberSetting("Vertical", "Vertical knockback %", 0.0, 0.0, 100.0, 1.0));
   private final NumberSetting chance = this.addSetting(new NumberSetting("Chance", "Chance to apply reduction %", 100.0, 0.0, 100.0, 5.0));

   public VelocityModule() {
      super("Velocity", "Reduce knockback from hits and explosions.", Category.COMBAT);
   }

   public double horizontalPercent() {
      if (this.mode.is("Grim")) {
         return 0.0;
      } else if (this.mode.is("Vulcan")) {
         return 0.45;
      } else {
         return this.horizontal.get() / 100.0;
      }
   }

   public double verticalPercent() {
      if (this.mode.is("Grim")) {
         return 1.0;
      } else if (this.mode.is("Vulcan")) {
         return 0.55;
      } else {
         return this.vertical.get() / 100.0;
      }
   }

   public boolean isGrim() {
      return this.mode.is("Grim");
   }

   public boolean isVulcan() {
      return this.mode.is("Vulcan");
   }

   public boolean jumpOnHit() {
      return this.mode.is("Jump") || this.mode.is("Grim");
   }

   public boolean reverse() {
      return this.mode.is("Reverse");
   }

   public boolean roll() {
      return this.chance.get() >= 100.0 || Math.random() * 100.0 < this.chance.get();
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
