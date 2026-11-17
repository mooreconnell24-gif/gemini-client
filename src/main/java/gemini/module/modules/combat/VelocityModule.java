package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.InfoText;
import java.util.ArrayList;
import java.util.List;

/** The knockback itself is rewritten where it arrives, in the packet listener mixin; this holds the settings. */
public final class VelocityModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Reduce scales the knockback you take; Jump also jumps when you are hit; Reverse sends you towards the attacker", "Reduce", "Reduce", "Jump", "Reverse")
   );
   private final NumberSetting horizontal = this.addSetting(new NumberSetting("Horizontal", "Horizontal knockback kept (percent); 0 takes none", 0.0, 0.0, 100.0, 5.0));
   private final NumberSetting vertical = this.addSetting(new NumberSetting("Vertical", "Vertical knockback kept (percent); 0 takes none", 0.0, 0.0, 100.0, 5.0));
   private final BooleanSetting explosions = this.addSetting(new BooleanSetting("Explosions", "Also change the push from explosions", true));
   private final InfoText infoText = new InfoText();

   public VelocityModule() {
      super("Velocity", "Reduces the knockback you take from hits and explosions.", Category.COMBAT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Anti Knockback");
      names.add("AntiKB");
      return names;
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

   public boolean explosions() {
      return this.explosions.get();
   }

   @Override
   public String info() {
      return this.mode.is("Reduce") ? this.infoText.whole(this.horizontal.intValue(), "%") : this.mode.get();
   }
}
