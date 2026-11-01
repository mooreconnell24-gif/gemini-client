package gemini.util;

import gemini.module.modules.misc.MiscExtras;
import gemini.module.modules.misc.MiscModules;
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
         MiscModules.AntiBot antiBot = MiscModules.AntiBot.instance;
         if (antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) {
            return false;
         }
         MiscExtras.Teams teams = MiscExtras.Teams.instance;
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
}
