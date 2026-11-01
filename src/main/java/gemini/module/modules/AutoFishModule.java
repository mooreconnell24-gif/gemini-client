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
   private final NumberSetting recast = this.addSetting(new NumberSetting("Recast Delay", "Ticks to wait before casting again", 14.0, 5.0, 60.0, 1.0));
   private final BooleanSetting autoCast = this.addSetting(new BooleanSetting("Auto Cast", "Cast the rod when no bobber is out", true));
   private final NumberSetting sensitivity = this.addSetting(new NumberSetting("Sensitivity", "How small a dip counts as a bite", 0.15, 0.05, 0.4, 0.01));
   private int recastDelay;
   private double lastHookY = Double.NaN;
   private int stableTicks;

   public AutoFishModule() {
      super("AutoFish", "Reel in bites and recast automatically.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.recastDelay = 0;
      this.lastHookY = Double.NaN;
      this.stableTicks = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null && client.gui.screen() == null) {
         InteractionHand hand = handWithRod(player);
         if (hand != null) {
            if (this.recastDelay > 0) {
               this.recastDelay--;
               if (this.recastDelay == 0 && this.autoCast.get()) {
                  client.gameMode.useItem(player, hand);
                  this.lastHookY = Double.NaN;
                  this.stableTicks = 0;
               }
            } else {
               FishingHook hook = player.fishing;
               if (hook == null) {
                  if (this.autoCast.get()) {
                     client.gameMode.useItem(player, hand);
                     this.recastDelay = this.recast.intValue();
                  }
               } else {
                  double y = hook.getY();
                  if (!Double.isNaN(this.lastHookY) && this.lastHookY - y > this.sensitivity.get() && this.stableTicks > 8) {
                     client.gameMode.useItem(player, hand);
                     this.recastDelay = this.recast.intValue();
                     this.lastHookY = Double.NaN;
                     this.stableTicks = 0;
                  } else {
                     if (!Double.isNaN(this.lastHookY) && Math.abs(this.lastHookY - y) < 0.03) {
                        this.stableTicks++;
                     } else {
                        this.stableTicks = 0;
                     }

                     this.lastHookY = y;
                  }
               }
            }
         }
      }
   }

   private static InteractionHand handWithRod(LocalPlayer player) {
      if (player.getMainHandItem().is(Items.FISHING_ROD)) {
         return InteractionHand.MAIN_HAND;
      } else {
         return player.getOffhandItem().is(Items.FISHING_ROD) ? InteractionHand.OFF_HAND : null;
      }
   }
}
