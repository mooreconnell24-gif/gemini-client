package gemini.util;

import gemini.module.modules.combat.Teams;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class Targets {
   private Targets() {
   }

   public static boolean isHostile(LivingEntity living) {
      return living instanceof Enemy || living.getType().getCategory() == MobCategory.MONSTER;
   }

   public static boolean isPassive(LivingEntity living) {
      return living instanceof Mob && !isHostile(living) && !(living instanceof AbstractVillager);
   }

   public static boolean isCandidate(LocalPlayer self, Entity entity, boolean players, boolean hostile, boolean passive, boolean invisible) {
      if (!(entity instanceof LivingEntity living && entity != self && living.isAlive() && !living.isRemoved())) {
         return false;
      } else if (!invisible && living.isInvisible()) {
         return false;
      } else if (living instanceof Player player) {
         if (!players || player.isSpectator()) {
            return false;
         }
         Teams teams = Teams.instance;
         if (teams != null && teams.isEnabled() && teams.isTeammate(self, player)) {
            return false;
         }
         return true;
      } else if (living instanceof TamableAnimal pet && pet.isOwnedBy(self)) {
         return false;
      } else {
         return isHostile(living) ? hostile : passive && isPassive(living);
      }
   }

   public static double reach(LocalPlayer self, Entity entity) {
      AABB box = entity.getBoundingBox();
      return Math.sqrt(box.distanceToSqr(self.getEyePosition()));
   }

   public static boolean inFov(LocalPlayer self, Entity entity, double fov) {
      if (fov >= 360.0) {
         return true;
      } else {
         float[] rotation = Rotations.face(self, entity);
         return Rotations.yawDiff(self.getYRot(), rotation[0]) <= fov / 2.0;
      }
   }

   public static boolean visible(LocalPlayer self, Entity entity) {
      return self.hasLineOfSight(entity);
   }

   /**
    * The entity nearest to the player that passes the filter and lies within {@code range} blocks, or null. One
    * pass over the loaded entities, no list and no sort.
    */
   public static <T extends Entity> T nearest(LocalPlayer self, Iterable<Entity> entities, Class<T> type, double range, Predicate<? super T> filter) {
      T best = null;
      double bestDistance = range * range;
      for (Entity entity : entities) {
         if (entity != self && type.isInstance(entity)) {
            double distance = self.distanceToSqr(entity);
            if (distance < bestDistance) {
               T candidate = type.cast(entity);
               if (filter.test(candidate)) {
                  bestDistance = distance;
                  best = candidate;
               }
            }
         }
      }

      return best;
   }

   /** The nearest player or (optionally) hostile mob in range: the shared target pick of the combat modules. */
   public static LivingEntity nearestEnemy(LocalPlayer self, Iterable<Entity> entities, double range, boolean mobs) {
      return nearest(self, entities, LivingEntity.class, range, living -> isCandidate(self, living, true, mobs, false, true));
   }
}
