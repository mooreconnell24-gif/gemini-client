package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public final class BlockHitModule extends Module {
   private static boolean holding;
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Instant raises as the hit lands. Lag waits a couple of ticks so the swing finishes first", "Lag", "Instant", "Lag")
   );
   private final NumberSetting chance = this.addSetting(new NumberSetting("Chance", "Percent of your hits that raise the shield", 80.0, 0.0, 100.0, 5.0));
   private final NumberSetting hold = this.addSetting(
      new NumberSetting("Hold Ticks", "How long to keep the shield up (a shield needs about 5 ticks before it blocks)", 8.0, 2.0, 15.0, 1.0)
   );
   private final NumberSetting lag = this.addSetting(
      new NumberSetting("Lag Ticks", "Ticks to wait after the hit before raising", 1.0, 1.0, 5.0, 1.0).showIf(() -> this.mode.is("Lag"))
   );
   private final Random random = new Random();
   private int delayLeft;
   private int holdLeft;
   private boolean raisedByUs;
   private boolean armedThisTick;

   public BlockHitModule() {
      super("BlockHit", "Raise your shield right after a hit so the counter lands on it.", Category.COMBAT);
   }

   public static boolean wantsShield() {
      return holding;
   }

   @Override
   public String info() {
      return this.holdLeft > 0 ? "Blocking" : this.mode.get();
   }

   @Override
   public void onDisable() {
      this.delayLeft = 0;
      this.holdLeft = 0;
      this.armedThisTick = false;
      holding = false;
      this.lower(Minecraft.getInstance());
   }

   @Override
   public void onAttack(Entity target) {
      if (target instanceof LivingEntity && !AutoBlockModule.wantsShield()) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (player != null
            && shieldHand(player) != null
            && (!player.isUsingItem() || player.getUseItem().is(Items.SHIELD))
            && (!(this.chance.get() < 100.0) || !(this.random.nextDouble() * 100.0 >= this.chance.get()))) {
            if (this.mode.is("Lag")) {
               this.delayLeft = this.lag.intValue();
            } else {
               this.delayLeft = 0;
               this.holdLeft = Math.max(this.holdLeft, this.hold.intValue());
            }

            this.armedThisTick = true;
            holding = this.delayLeft > 0 || this.holdLeft > 0;
         }
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.gameMode != null) {
         boolean fresh = this.armedThisTick;
         this.armedThisTick = false;
         if (!fresh && this.delayLeft > 0) {
            this.delayLeft--;
            if (this.delayLeft == 0) {
               this.holdLeft = Math.max(this.holdLeft, this.hold.intValue());
            }
         }

         if (AutoBlockModule.wantsShield()) {
            this.holdLeft = 0;
            this.delayLeft = 0;
            holding = false;
            this.raisedByUs = false;
         } else {
            holding = this.delayLeft > 0 || this.holdLeft > 0;
            if (this.holdLeft > 0) {
               this.raise(client, player);
               this.holdLeft--;
               holding = this.delayLeft > 0 || this.holdLeft > 0;
            } else if (!holding) {
               this.lower(client);
            }
         }
      } else {
         holding = false;
      }
   }

   private void raise(Minecraft client, LocalPlayer player) {
      InteractionHand hand = shieldHand(player);
      if (hand != null) {
         if (player.isUsingItem()) {
            if (player.getUseItem().is(Items.SHIELD)) {
               this.raisedByUs = true;
               if (client.gui.screen() == null) {
                  client.options.keyUse.setDown(true);
               }
            }
         } else {
            client.gameMode.useItem(player, hand);
            if (client.gui.screen() == null) {
               client.options.keyUse.setDown(true);
            }

            this.raisedByUs = true;
         }
      }
   }

   private void lower(Minecraft client) {
      LocalPlayer player = client.player;
      if (this.raisedByUs) {
         this.raisedByUs = false;
         if (player != null && !AutoBlockModule.wantsShield()) {
            if (player.isUsingItem() && player.getUseItem().is(Items.SHIELD)) {
               client.gameMode.releaseUsingItem(player);
            }

            client.options.keyUse.setDown(false);
         }
      }
   }

   private static InteractionHand shieldHand(LocalPlayer player) {
      if (player.getOffhandItem().is(Items.SHIELD)) {
         return InteractionHand.OFF_HAND;
      } else {
         return player.getMainHandItem().is(Items.SHIELD) ? InteractionHand.MAIN_HAND : null;
      }
   }
}
