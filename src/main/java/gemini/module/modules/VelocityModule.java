package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;

public final class VelocityModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Packet scales the knockback, Jump hops on hit, Reverse sends it back", "Jump", "Packet", "Jump", "Reverse")
   );
   private final NumberSetting horizontal = this.addSetting(
      new NumberSetting("Horizontal", "Horizontal knockback kept (%). Reverse uses this as how hard you fly back", 90.0, 0.0, 100.0, 1.0)
   );
   private final NumberSetting vertical = this.addSetting(new NumberSetting("Vertical", "Vertical knockback kept (%)", 100.0, 0.0, 100.0, 1.0));
   private final NumberSetting chance = this.addSetting(new NumberSetting("Chance", "Chance to apply the reduction (%)", 80.0, 0.0, 100.0, 5.0));

   public VelocityModule() {
      super("Velocity", "Reduce knockback from hits and explosions.", Category.COMBAT);
   }

   public double horizontalPercent() {
      return this.horizontal.get() / 100.0;
   }

   public double verticalPercent() {
      return this.vertical.get() / 100.0;
   }

   public boolean jumpOnHit() {
      return this.mode.is("Jump");
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
