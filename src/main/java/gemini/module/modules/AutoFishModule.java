package gemini.module.modules;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

public final class AutoFishModule extends Module {
   private final NumberSetting recast = this.addSetting(new NumberSetting("Recast Delay", "Ticks to wait after reeling in before casting again", 14.0, 5.0, 60.0, 1.0));
   private final BooleanSetting autoCast = this.addSetting(new BooleanSetting("Auto Cast", "Cast the rod when no bobber is out", true));
   private final NumberSetting sensitivity = this.addSetting(new NumberSetting("Sensitivity", "How small a dip counts as a bite", 0.15, 0.05, 0.4, 0.01));
   private final NumberSetting timeout = this.addSetting(
      new NumberSetting("Timeout", "Seconds without a bite before reeling in and casting again", 45.0, 10.0, 120.0, 5.0)
   );
   private int cooldown;
   private int hookTicks;
   private double lastHookY = Double.NaN;
   private int stableTicks;
   private int catches;

   public AutoFishModule() {
      super("AutoFish", "Reel in bites and recast automatically.", Category.PLAYER);
   }

   @Override
   public void onEnable() {
      this.cooldown = 0;
      this.catches = 0;
      this.resetHook();
   }

   @Override
   public void onDisable() {
      this.cooldown = 0;
      this.resetHook();
   }

   @Override
   public String info() {
      return this.catches > 0 ? this.catches + " caught" : null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null || client.gui.screen() != null) {
         return;
      }

      InteractionHand hand = handWithRod(player);
      if (hand == null) {
         this.resetHook();
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      FishingHook hook = player.fishing;
      if (hook == null) {
         this.resetHook();
         if (this.autoCast.get()) {
            client.gameMode.useItem(player, hand);
            // Give the bobber a moment to appear before judging it, otherwise the next use reels it straight back in.
            this.cooldown = 10;
         }

         return;
      }

      this.hookTicks++;
      double y = hook.getY();
      boolean bite = !Double.isNaN(this.lastHookY) && this.lastHookY - y > this.sensitivity.get() && this.stableTicks > 8;
      boolean stuck = this.hookTicks > this.timeout.intValue() * 20 || hook.onGround() && this.hookTicks > 40;
      if (bite || stuck) {
         client.gameMode.useItem(player, hand);
         if (bite) {
            this.catches++;
         }

         this.cooldown = this.recast.intValue();
         this.resetHook();
         return;
      }

      if (!Double.isNaN(this.lastHookY) && Math.abs(this.lastHookY - y) < 0.03) {
         this.stableTicks++;
      } else {
         this.stableTicks = 0;
      }

      this.lastHookY = y;
   }

   private void resetHook() {
      this.hookTicks = 0;
      this.lastHookY = Double.NaN;
      this.stableTicks = 0;
   }

   private static InteractionHand handWithRod(LocalPlayer player) {
      if (player.getMainHandItem().is(Items.FISHING_ROD)) {
         return InteractionHand.MAIN_HAND;
      } else {
         return player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
      }
   }
}
