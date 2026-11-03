package gemini.module.modules.player;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Tops your health up with a server-side effect once it drops below a threshold. */
public final class AutoHeal extends Module {
   private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Heal once health (in half-hearts) drops to this", 8.0, 1.0, 19.0, 1.0));
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "How to heal", "Instant Health", "Instant Health", "Regeneration", "Full Heal")
   );
   private final BooleanSetting feed = this.addSetting(new BooleanSetting("Feed", "Also refill hunger with saturation", true));
   private final NumberSetting cooldown = this.addSetting(new NumberSetting("Cooldown", "Ticks between heals", 40.0, 5.0, 400.0, 5.0));
   private int wait;
   private int heals;

   public AutoHeal() {
      super("Auto Heal", "Heal yourself with /effect whenever your health gets low.", Category.PLAYER);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public void onEnable() {
      this.wait = 0;
      this.heals = 0;
   }

   @Override
   public void onTick() {
      if (this.wait > 0) {
         this.wait--;
         return;
      }

      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && player.isAlive() && player.getHealth() > 0.0F && player.getHealth() <= this.health.get()) {
         String command = switch (this.mode.get()) {
            case "Regeneration" -> "effect give @s minecraft:regeneration 6 2 true";
            case "Full Heal" -> "effect give @s minecraft:instant_health 1 4 true";
            default -> "effect give @s minecraft:instant_health 1 1 true";
         };
         if (SpCommands.run(command)) {
            if (this.feed.get()) {
               SpCommands.run("effect give @s minecraft:saturation 1 0 true");
            }

            this.heals++;
            this.wait = this.cooldown.intValue();
         }
      }
   }

   @Override
   public String info() {
      return this.mode.get() + (this.heals > 0 ? " · " + this.heals : "");
   }
}
