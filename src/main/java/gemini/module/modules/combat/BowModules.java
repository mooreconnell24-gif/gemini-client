package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class BowModules {
   private BowModules() {
   }

   static void register(Consumer<Module> sink) {
      sink.accept(new BowModules.BowSpam());
      sink.accept(new BowModules.ArrowDodge());
      sink.accept(new BowModules.ProjectileAimbot());
      sink.accept(new BowModules.Quiver());
      sink.accept(new BowModules.AutoRod());
   }

   static final class ArrowDodge extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max arrow detection range", 24.0, 6.0, 48.0, 1.0));
      private final NumberSetting lookahead = this.addSetting(new NumberSetting("Lookahead", "Ticks of arrow flight to predict", 14.0, 4.0, 40.0, 1.0));
      private final NumberSetting strength = this.addSetting(new NumberSetting("Dodge Speed", "Horizontal dodge velocity", 0.55, 0.2, 1.5, 0.05));
      private final BooleanSetting inAir = this.addSetting(new BooleanSetting("While Airborne", "Also dodge while jumping or falling", true));

      ArrowDodge() {
         super("Arrow Dodge", "Sidestep incoming arrows automatically.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (CombatModules.ready(client) && (this.inAir.get() || player.onGround())) {
            AABB hitbox = player.getBoundingBox().inflate(0.35);
            double limit = this.range.get() * this.range.get();
            Vec3 away = null;

            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity instanceof AbstractArrow arrow && arrow.getOwner() != player && !(arrow.distanceToSqr(player) > limit)) {
                  Vec3 motion = arrow.getDeltaMovement();
                  if (!(motion.lengthSqr() < 0.1) && this.willHit(client, arrow, hitbox)) {
                     double length = Math.hypot(motion.x, motion.z);
                     if (!(length < 0.001)) {
                        Vec3 side = new Vec3(-motion.z / length, 0.0, motion.x / length);
                        Vec3 offset = player.position().subtract(arrow.position());
                        away = offset.dot(side) >= 0.0 ? side : side.scale(-1.0);
                        break;
                     }
                  }
               }
            }

            if (away != null) {
               Vec3 current = player.getDeltaMovement();
               player.setDeltaMovement(away.x * this.strength.get(), current.y, away.z * this.strength.get());
            }
         }
      }

      private boolean willHit(Minecraft client, AbstractArrow arrow, AABB hitbox) {
         Vec3 position = arrow.position();
         Vec3 motion = arrow.getDeltaMovement();

         for (int tick = 0; tick < this.lookahead.intValue(); tick++) {
            Vec3 next = position.add(motion);
            if (hitbox.contains(next) || hitbox.clip(position, next).isPresent()) {
               return true;
            }

            if (client.level.clip(new ClipContext(position, next, Block.COLLIDER, Fluid.NONE, arrow)).getType() != Type.MISS) {
               return false;
            }

            position = next;
            motion = motion.scale(0.99).add(0.0, -0.05, 0.0);
         }

         return false;
      }
   }

   static final class AutoRod extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max distance to rod enemies", 6.8, 3.0, 10.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between rod casts", 12.0, 4.0, 30.0, 1.0));
      private int wait;
      private int retractTicks;
      private int previousSlot = -1;

      AutoRod() {
         super("AutoRod", "Cast a fishing rod at enemies for combo knockback.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         this.restore(Minecraft.getInstance().player);
      }

      private void restore(LocalPlayer player) {
         if (player != null && this.previousSlot >= 0) {
            player.getInventory().setSelectedSlot(this.previousSlot);
         }
         this.previousSlot = -1;
         this.retractTicks = 0;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
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

            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.range.get(), true);
            if (enemy != null && player.distanceTo(enemy) > 3.1) {
               int slot = Inventories.findHotbar(player, s -> s.is(Items.FISHING_ROD));
               if (slot >= 0) {
                  float[] rot = Rotations.face(player, enemy, 2.5);
                  if (RotationManager.request(player, rot, 95.0F, true, 5)) {
                     this.previousSlot = player.getInventory().getSelectedSlot();
                     player.getInventory().setSelectedSlot(slot);
                     RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
                     player.swing(InteractionHand.MAIN_HAND);
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

   static final class BowSpam extends Module {
      private final NumberSetting charge = this.addSetting(
         new NumberSetting("Charge", "Ticks to draw before firing (20 = full draw)", 5.0, 3.0, 20.0, 1.0)
      );

      BowSpam() {
         super("Bow Spam", "Rapidly release and redraw your bow.", Category.COMBAT);
      }

      @Override
      public String info() {
         return this.charge.intValue() + "t";
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (CombatModules.ready(client)
            && client.gui.screen() == null
            && player.isUsingItem()
            && player.getUseItem().getItem() instanceof BowItem
            && player.getTicksUsingItem() >= this.charge.intValue()) {
            InteractionHand hand = player.getUsedItemHand();
            client.gameMode.releaseUsingItem(player);
            client.gameMode.useItem(player, hand);
         }
      }
   }

   static final class ProjectileAimbot extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance", 45.0, 10.0, 80.0, 2.0));
      private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets only", true));
      private final BooleanSetting predict = this.addSetting(new BooleanSetting("Predict Motion", "Lead moving targets by flight time", true));

      ProjectileAimbot() {
         super("ProjectileAimbot", "Ballistic arc aimbot for Bows, Crossbows, and Tridents.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            boolean usingBow = player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem;
            boolean usingTrident = player.isUsingItem() && player.getUseItem().is(Items.TRIDENT);
            boolean holdingCrossbow = player.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getMainHandItem());
            if (usingBow || usingTrident || holdingCrossbow) {
               LivingEntity target = CombatExtras.nearestEnemy(client, player, this.range.get(), true);
               if (target != null) {
                  float velocity = usingBow
                     ? Math.max(0.6F, BowItem.getPowerForTime(player.getTicksUsingItem()) * 3.0F)
                     : (usingTrident ? 2.5F : 3.15F);
                  double dist = player.distanceTo(target);
                  double flightTicks = this.predict.get() ? dist / Math.max(0.5, velocity) : 0.0;
                  Vec3 motion = target.getDeltaMovement().scale(flightTicks);
                  Vec3 eye = player.getEyePosition();
                  Vec3 aimPoint = new Vec3(
                     target.getX() + motion.x,
                     target.getY() + target.getBbHeight() * 0.72 + motion.y,
                     target.getZ() + motion.z
                  );
                  double dx = aimPoint.x - eye.x;
                  double dy = aimPoint.y - eye.y;
                  double dz = aimPoint.z - eye.z;
                  double horizDist = Math.sqrt(dx * dx + dz * dz);
                  float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                  float g = 0.05F;
                  double v2 = velocity * velocity;
                  double v4 = v2 * v2;
                  double root = v4 - g * (g * horizDist * horizDist + 2.0 * dy * v2);
                  float pitch;
                  if (root >= 0.0 && horizDist > 1.0E-3) {
                     pitch = (float)(-Math.toDegrees(Math.atan((v2 - Math.sqrt(root)) / (g * horizDist))));
                  } else {
                     pitch = (float)(-(Mth.atan2(dy, horizDist) * (180.0 / Math.PI)));
                  }
                  RotationManager.request(player, Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F), 95.0F, this.silent.get(), 10, 2.5F);
               }
            }
         }
      }
   }

   static final class Quiver extends Module {
      private final NumberSetting charge = this.addSetting(new NumberSetting("Charge Ticks", "Ticks to draw bow upward", 4.0, 3.0, 10.0, 1.0));

      Quiver() {
         super("Quiver", "Shoot beneficial tipped arrows upward to buff yourself.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (CombatModules.ready(client) && client.gui.screen() == null && player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem) {
            RotationManager.request(player, player.getYRot(), -90.0F, 180.0F, true, 10, 2.0F);
            if (player.getTicksUsingItem() >= this.charge.intValue()) {
               client.gameMode.releaseUsingItem(player);
               this.setEnabled(false);
            }
         }
      }
   }
}
