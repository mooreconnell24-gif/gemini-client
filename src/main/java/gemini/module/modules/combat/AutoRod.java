package gemini.module.modules.combat;

import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Hands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public final class AutoRod extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max distance to rod enemies", 6.8, 3.0, 10.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between rod casts", 12.0, 4.0, 30.0, 1.0));
   private int wait;
   private int retractTicks;
   private int previousSlot = -1;

   public AutoRod() {
      super("AutoRod", "Cast a fishing rod at enemies for combo knockback.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.restore(Minecraft.getInstance().player);
   }

   private void restore(LocalPlayer player) {
      if (player != null && this.previousSlot >= 0 && Hands.claimMainHand()) {
         player.getInventory().setSelectedSlot(this.previousSlot);
      }
      this.previousSlot = -1;
      this.retractTicks = 0;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (this.retractTicks > 0) {
            this.retractTicks--;
            if (this.retractTicks == 0) {
               if (player.getMainHandItem().is(Items.FISHING_ROD) && player.fishing != null) {
                  client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
               }
               this.restore(player);
            }
            return;
         }

         if (this.wait > 0) {
            this.wait--;
            return;
         }

         LivingEntity enemy = CombatHelpers.nearestEnemy(client, player, this.range.get(), true);
         if (enemy != null && player.distanceTo(enemy) > 3.1) {
            int slot = Inventories.findHotbar(player, s -> s.is(Items.FISHING_ROD));
            if (slot >= 0 && Hands.claimMainHand()) {
               float[] rot = Rotations.face(player, enemy, 2.5);
               if (RotationManager.request(player, rot, 95.0F, true, ActionPriority.COMBAT)) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
                  player.getInventory().setSelectedSlot(slot);
                  RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
                  Swing.interact(player, InteractionHand.MAIN_HAND);
                  this.retractTicks = 3;
                  this.wait = this.delay.intValue();
               }
            }
         }
      } else {
         this.restore(client.player);
      }
   }
}
