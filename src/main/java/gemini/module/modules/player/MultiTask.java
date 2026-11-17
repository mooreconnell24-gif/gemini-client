package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;

/**
 * Lets the main hand keep working while an item is being used (eating, drinking, blocking, drawing a bow in the
 * offhand). Vanilla stops both in {@code Minecraft}: {@code handleKeybinds} throws attack clicks away while
 * {@code player.isUsingItem()}, and {@code continueAttack} refuses to keep mining. The two redirects in
 * {@code MinecraftMixin} ask this module before applying either rule; the module itself does nothing per tick.
 */
public final class MultiTask extends Module {
   private static MultiTask instance;
   private final BooleanSetting attack = this.addSetting(
      new BooleanSetting("Attack While Using", "Left clicks still attack and start breaking blocks while you use an item", true)
   );
   private final BooleanSetting mine = this.addSetting(
      new BooleanSetting("Mine While Using", "Holding attack keeps breaking the block while you use an item", true)
   );

   public MultiTask() {
      super("Multi Task", "Lets you keep eating or using an item while you mine and attack.", Category.PLAYER);
      instance = this;
   }

   /** Whether attack clicks should go through although the player is using an item. */
   public static boolean attackWhileUsing() {
      MultiTask module = instance;
      return module != null && module.isEnabled() && module.attack.get();
   }

   /** Whether block breaking should continue although the player is using an item. */
   public static boolean mineWhileUsing() {
      MultiTask module = instance;
      return module != null && module.isEnabled() && module.mine.get();
   }

   @Override
   public String info() {
      if (this.attack.get() && this.mine.get()) {
         return "Both";
      } else if (this.attack.get()) {
         return "Attack";
      } else {
         return this.mine.get() ? "Mine" : "Off";
      }
   }
}
