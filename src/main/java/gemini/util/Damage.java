package gemini.util;

import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

public final class Damage {
   public static final float CRYSTAL_RADIUS = 6.0F;

   private Damage() {
   }

   public static float explosion(LivingEntity entity, Vec3 center, float radius) {
      double doubleRadius = radius * 2.0;
      double distance = Math.sqrt(entity.distanceToSqr(center)) / doubleRadius;
      if (distance > 1.0) {
         return 0.0F;
      } else {
         double impact = (1.0 - distance) * ServerExplosion.getSeenPercent(center, entity);
         float damage = (float)((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0);
         if (entity instanceof Player) {
            Difficulty difficulty = entity.level().getDifficulty();
            if (difficulty == Difficulty.PEACEFUL) {
               return 0.0F;
            }

            if (difficulty == Difficulty.EASY) {
               damage = Math.min(damage / 2.0F + 1.0F, damage);
            } else if (difficulty == Difficulty.HARD) {
               damage = damage * 3.0F / 2.0F;
            }
         }

         damage = CombatRules.getDamageAfterAbsorb(
            entity, damage, entity.damageSources().generic(), entity.getArmorValue(), (float)entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
         );
         MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
         if (resistance != null) {
            damage *= Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
         }

         return Math.max(0.0F, damage);
      }
   }

   public static float crystal(LivingEntity entity, Vec3 center) {
      return explosion(entity, center, 6.0F);
   }

   public static float health(LivingEntity entity) {
      return entity.getHealth() + entity.getAbsorptionAmount();
   }
}
