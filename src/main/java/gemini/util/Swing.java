package gemini.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Arm-swing helpers for Minecraft 26.3, where a swing takes the held item's {@link SwingAnimation}
 * and LivingEntity no longer exposes the swinging / swingTime fields.
 */
public final class Swing {
   private Swing() {
   }

   /** Swing as for an attack or block break, using the held item's attack animation. */
   public static void attack(LivingEntity entity, InteractionHand hand) {
      entity.swing(hand, entity.getItemInHand(hand).getAttackAnimation(), false);
   }

   /** Swing as for using an item or interacting with a block or entity, using the held item's interact animation. */
   public static void interact(LivingEntity entity, InteractionHand hand) {
      entity.swing(hand, entity.getItemInHand(hand).getInteractAnimation(), false);
   }

   /** Whether the entity is mid-swing (replaces the old {@code swinging} field). */
   public static boolean isSwinging(LivingEntity entity) {
      return entity.isSwinging();
   }

   /**
    * Ticks since the current swing started (replaces the old {@code swingTime} field), derived from the swing's
    * progress and duration. Returns {@link Integer#MAX_VALUE} when the entity is not swinging.
    */
   public static int ticks(LivingEntity entity) {
      LivingEntity.SwingDescription swing = entity.getCurrentSwing();
      if (!entity.isSwinging() || swing == null) {
         return Integer.MAX_VALUE;
      }

      return Math.round(entity.getSwingAnimation(1.0F) * swing.durationTicks());
   }
}
