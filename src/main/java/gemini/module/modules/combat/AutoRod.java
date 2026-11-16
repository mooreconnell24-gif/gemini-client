package gemini.module.modules.combat;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.Targeting;
import java.util.function.Predicate;
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
   private final Targeting targeting = Targeting.builder(this).build();
   /** A rod only pays off past melee reach. */
   private static final Predicate<LivingEntity> PAST_MELEE = living -> {
      LocalPlayer self = Minecraft.getInstance().player;
      return self != null && self.distanceTo(living) > 3.1;
   };
   private int wait;
   private int retractTicks;
   private int previousSlot = -1;

   public AutoRod() {
      super("AutoRod", "Cast a fishing rod at enemies for combo knockback.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.restore(Minecraft.getInstance().player);
      this.targeting.clear();
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   /** Everything remembered here belongs to the world that was just left: no slot to restore there. */
   @Override
   public void onWorldChange() {
      this.previousSlot = -1;
      this.retractTicks = 0;
      this.wait = 0;
      this.targeting.clear();
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
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         // Picked every tick so the box follows the current choice while a cast or the delay is running.
         LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), PAST_MELEE);
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

         if (enemy != null) {
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
         this.targeting.clear();
      }
   }
}
