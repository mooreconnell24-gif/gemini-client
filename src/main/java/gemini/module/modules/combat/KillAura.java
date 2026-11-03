package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KillAura extends Module {
   private final ModeSetting targetMode = this.addSetting(
      new ModeSetting("Target Mode", "Single locks one target; Switch cycles targets", "Switch", "Single", "Switch")
   );
   private final ModeSetting priority = this.addSetting(
      new ModeSetting("Priority", "Target sorting priority", "Distance", "Distance", "Health", "Angle", "Armor")
   );
   private final ModeSetting versionMode = this.addSetting(
      new ModeSetting("Version", "Wait for the attack cooldown, or click at a fixed rate", "1.9+ Cooldown", "1.9+ Cooldown", "1.8 Legacy CPS")
   );
   private final NumberSetting legacyCps = this.addSetting(
      new NumberSetting("Legacy CPS", "Clicks per second in 1.8 Legacy CPS mode", 14.0, 4.0, 20.0, 1.0).showIf(() -> this.versionMode.is("1.8 Legacy CPS"))
   );
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Maximum attack reach", 4.2, 2.5, 6.0, 0.1));
   private final NumberSetting wallRange = this.addSetting(new NumberSetting("Wall Range", "Maximum reach through walls", 3.0, 0.0, 6.0, 0.1));
   private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view scan cone", 180.0, 30.0, 360.0, 5.0));
   private final NumberSetting turnSpeed = this.addSetting(
      new NumberSetting("Turn Speed", "Rotation speed in degrees per tick", 85.0, 15.0, 180.0, 5.0)
   );
   private final BooleanSetting silentRotate = this.addSetting(new BooleanSetting("Silent Aim", "Rotate in packets without moving camera", true));
   private final BooleanSetting raytrace = this.addSetting(new BooleanSetting("Raytrace Check", "Require clear line-of-sight to target", false));
   private final BooleanSetting autoCrit = this.addSetting(new BooleanSetting("Wait Crit", "Wait for falling window when airborne for crits", true));
   private final BooleanSetting keepSprint = this.addSetting(new BooleanSetting("Keep Sprint", "Maintain sprint state while attacking", true));
   private final BooleanSetting smartSwitch = this.addSetting(
      new BooleanSetting("Smart Switch", "Auto-select best sword or shield-breaking axe", true)
   );
   private final BooleanSetting lagComp = this.addSetting(
      new BooleanSetting("Lag Comp", "Lead moving targets by ping and velocity", true)
   );
   private final NumberSetting predictTicks = this.addSetting(
      new NumberSetting("Predict Ticks", "Base movement ticks to lead target", 2.0, 0.0, 8.0, 0.5).showIf(this.lagComp::get)
   );
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Attack players", true));
   private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Attack hostile mobs", true));
   private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Attack passive animals", false));
   private final BooleanSetting renderTarget = this.addSetting(new BooleanSetting("Highlight", "Render 3D box on active target", true));
   private LivingEntity currentTarget;
   private int switchIndex;
   private int switchTimer;
   private long lastLegacyAttackMs;

   public KillAura() {
      super("KillAura", "Automatically attack nearby mobs and players with smooth aim.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.currentTarget = null;
   }

   @Override
   public String info() {
      return this.currentTarget != null ? this.currentTarget.getName().getString() : this.priority.get();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (!player.isUsingItem()) {
            List<LivingEntity> candidates = this.collectTargets(client, player);
            if (candidates.isEmpty()) {
               this.currentTarget = null;
            } else {
               if (!this.targetMode.is("Single") || this.currentTarget == null || !candidates.contains(this.currentTarget)) {
                  if (this.targetMode.is("Switch")) {
                     if (++this.switchTimer >= 6) {
                        this.switchTimer = 0;
                        this.switchIndex = (this.switchIndex + 1) % candidates.size();
                     }

                     this.currentTarget = candidates.get(this.switchIndex % candidates.size());
                  } else {
                     this.currentTarget = candidates.get(0);
                  }
               }

               double pred = this.effectivePredictionTicks(client, player);
               float[] rotation = Rotations.face(player, this.currentTarget, pred);
               boolean aimed = RotationManager.request(player, rotation[0], rotation[1], this.turnSpeed.floatValue(), this.silentRotate.get(), 10, 8.0F);
               if (aimed) {
                  boolean useLegacy = this.versionMode.is("1.8 Legacy CPS");
                  float cooldownRequired = 0.9F;
                  long nowMs = System.currentTimeMillis();
                  long minDelayMs = (long)Math.max(45.0, 1000.0 / Math.max(1.0, this.legacyCps.get()));
                  boolean readyToHit = useLegacy ? (nowMs - this.lastLegacyAttackMs >= minDelayMs) : (player.getAttackStrengthScale(0.0F) >= cooldownRequired);
                  if (readyToHit) {
                     if (this.autoCrit.get()
                        && !player.onGround()
                        && !player.isInWater()
                        && !player.onClimbable()
                        && player.getDeltaMovement().y > -0.03
                        && player.fallDistance < 1.2F) {
                        return;
                     }

                     this.lastLegacyAttackMs = nowMs;

                     int prevSlot = player.getInventory().getSelectedSlot();
                     if (this.smartSwitch.get()) {
                        int weaponSlot = this.pickSmartWeapon(player, this.currentTarget);
                        if (weaponSlot >= 0 && weaponSlot != prevSlot) {
                           player.getInventory().setSelectedSlot(weaponSlot);
                        }
                     }

                     boolean wasSprinting = player.isSprinting();
                     client.gameMode.attack(player, this.currentTarget);
                     player.swing(InteractionHand.MAIN_HAND);
                     if (this.keepSprint.get() && wasSprinting && MoveUtil.hasInput(player)) {
                        player.setSprinting(true);
                     }
                  }
               }
            }
         }
      } else {
         this.currentTarget = null;
      }
   }

   private double effectivePredictionTicks(Minecraft client, LocalPlayer player) {
      if (!this.lagComp.get()) {
         return 0.0;
      } else {
         double base = this.predictTicks.get();
         if (client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
            if (info != null && info.getLatency() > 0) {
               base += Mth.clamp(info.getLatency() / 50.0, 0.0, 6.0);
            }
         }

         return base;
      }
   }

   private List<LivingEntity> collectTargets(Minecraft client, LocalPlayer player) {
      List<LivingEntity> list = new ArrayList<>();
      double maxRange = this.range.get();
      double maxWall = this.wallRange.get();
      double pred = this.effectivePredictionTicks(client, player);

      for (Entity entity : client.level.entitiesForRendering()) {
         if (Targets.isCandidate(player, entity, this.players.get(), this.hostile.get(), this.passive.get(), true)
            && Targets.inFov(player, entity, this.fov.get())) {
            LivingEntity living = (LivingEntity)entity;
            Vec3 offset = living.getDeltaMovement().scale(pred);
            AABB box = living.getBoundingBox().move(offset);
            double dist = Math.sqrt(box.distanceToSqr(player.getEyePosition()));
            boolean visible = Targets.visible(player, living);
            if ((!this.raytrace.get() || visible) && dist <= (visible ? maxRange : maxWall)) {
               list.add(living);
            }
         }
      }

      String prio = this.priority.get();

      Comparator<LivingEntity> cmp = switch (prio) {
         case "Health" -> Comparator.comparingDouble(Damage::health);
         case "Angle" -> Comparator.comparingDouble(e -> Rotations.yawDiff(player.getYRot(), Rotations.face(player, e)[0]));
         case "Armor" -> Comparator.comparingInt(LivingEntity::getArmorValue);
         default -> Comparator.comparingDouble(player::distanceToSqr);
      };
      list.sort(cmp);
      return list;
   }

   private int pickSmartWeapon(LocalPlayer player, LivingEntity target) {
      if (target instanceof Player enemy && enemy.isBlocking()) {
         int axe = Inventories.findHotbar(player, stackx -> stackx.is(ItemTags.AXES));
         if (axe >= 0) {
            return axe;
         }
      }

      int best = -1;
      double bestScore = 0.0;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         if (AutoWeapon.isWeapon(stack)) {
            double score = AutoWeapon.dps(stack);
            if (score > bestScore) {
               bestScore = score;
               best = slot;
            }
         }
      }

      return best;
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.renderTarget.get() && this.currentTarget != null && this.currentTarget.isAlive()) {
         AABB box = Render3D.interpolatedBox(this.currentTarget, partialTick);
         Render3D.box(box, -6467875, true, true, 1.8F, 45, true);
      }
   }
}
