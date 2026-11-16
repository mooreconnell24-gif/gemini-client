package gemini.module.modules.player;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.util.Hands;
import gemini.util.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class AutoToolModule extends Module {
   private final BooleanSetting weapons = this.addSetting(new BooleanSetting("Weapons", "Swap to your best weapon when attacking mobs", true));
   private final BooleanSetting restore = this.addSetting(new BooleanSetting("Restore Slot", "Go back to the previous slot when you stop", true));
   private final BooleanSetting saveTools = this.addSetting(new BooleanSetting("Save Durability", "Don't use tools that are almost broken", true));
   private int previousSlot = -1;
   /** Attack damage per hotbar slot, NaN for a non-weapon; reused every tick. */
   private final double[] damage = new double[9];

   public AutoToolModule() {
      super("AutoTool", "Swap to the best tool for what you are hitting.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.restoreSlot();
   }

   /** The slot to go back to belonged to the player that just left. */
   @Override
   public void onWorldChange() {
      this.previousSlot = -1;
   }

   private static double attackDamage(ItemStack stack) {
      double total = 0.0;
      for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
         if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
            total += entry.modifier().amount();
         }
      }

      return total;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && client.level != null && client.gui.screen() == null) {
         if (client.options.keyAttack.isDown() && client.hitResult != null) {
            int best = -1;
            if (client.hitResult.getType() == Type.BLOCK) {
               BlockPos pos = ((BlockHitResult)client.hitResult).getBlockPos();
               BlockState state = client.level.getBlockState(pos);
               int slot = Tools.bestSlot(player, state, this.saveTools.get());
               best = slot == player.getInventory().getSelectedSlot() ? -1 : slot;
            } else if (this.weapons.get()
               && client.hitResult.getType() == Type.ENTITY
               && ((EntityHitResult)client.hitResult).getEntity() instanceof LivingEntity) {
               // Measured against the weapon already held, so a better held weapon is never swapped for a worse one.
               ItemStack held = player.getMainHandItem();
               double current = isWeapon(held) ? attackDamage(held) : 0.0;
               for (int i = 0; i < 9; i++) {
                  ItemStack stack = player.getInventory().getItem(i);
                  this.damage[i] = isWeapon(stack) ? attackDamage(stack) : Double.NaN;
               }

               best = PlayerMath.bestAbove(this.damage, current, 0.0);
            }

            if (best >= 0 && best != player.getInventory().getSelectedSlot() && Hands.claimMainHand()) {
               if (this.previousSlot < 0) {
                  this.previousSlot = player.getInventory().getSelectedSlot();
               }

               player.getInventory().setSelectedSlot(best);
            }
         } else {
            this.restoreSlot();
         }
      }
   }

   private static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.TRIDENT) || stack.is(Items.MACE);
   }

   private void restoreSlot() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (this.restore.get() && player != null && this.previousSlot >= 0) {
         if (!Hands.claimMainHand()) {
            return; // someone more important holds the hand this tick; restore next tick
         }

         player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.previousSlot = -1;
   }
}
