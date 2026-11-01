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
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Only react to arrows this close", 24.0, 6.0, 48.0, 1.0));
      private final NumberSetting lookahead = this.addSetting(new NumberSetting("Lookahead", "Ticks of arrow flight to predict", 14.0, 4.0, 40.0, 1.0));
      private final NumberSetting strength = this.addSetting(new NumberSetting("Dodge Speed", "Sideways speed of the dodge", 0.55, 0.2, 1.5, 0.05));
      private final BooleanSetting inAir = this.addSetting(new BooleanSetting("While Airborne", "Also dodge while jumping or falling", true));

      ArrowDodge() {
         super("Arrow Dodge", "Sidestep arrows that are flying towards you.", Category.COMBAT);
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
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Distance to throw fishing rod at enemies", 6.5, 3.0, 10.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between rod casts", 10.0, 4.0, 30.0, 1.0));
      private int wait;

      AutoRod() {
         super("AutoRod", "Automatically cast a fishing rod at approaching enemies for combo knockback.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.range.get(), false);
            if (enemy != null && player.distanceTo(enemy) > 3.2) {
               int slot = Inventories.findHotbar(player, s -> s.is(Items.FISHING_ROD));
               if (slot >= 0) {
                  float[] rot = Rotations.face(player, enemy, 2.0);
                  if (RotationManager.request(player, rot, 90.0F, true, 5)) {
                     CombatExtras.withSlot(player, s -> s.is(Items.FISHING_ROD), () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
                     this.wait = this.delay.intValue();
                  }
               }
            }
         }
      }
   }

   static final class BowSpam extends Module {
      private final NumberSetting charge = this.addSetting(
         new NumberSetting("Charge", "Ticks to draw before each release (20 = full power)", 5.0, 3.0, 20.0, 1.0)
      );

      BowSpam() {
         super("Bow Spam", "Fire a bow as fast as it can be drawn.", Category.COMBAT);
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
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance", 42.0, 10.0, 80.0, 2.0));
      private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets only", true));
      private final BooleanSetting predict = this.addSetting(new BooleanSetting("Predict Motion", "Lead moving targets", true));

      ProjectileAimbot() {
         super("ProjectileAimbot", "Calculate ballistic gravity arcs to aim Bows, Crossbows, and Tridents at moving targets.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            boolean holdingRanged = player.isUsingItem() && (player.getUseItem().getItem() instanceof BowItem || player.getUseItem().is(Items.TRIDENT))
               || player.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(player.getMainHandItem());
            if (holdingRanged) {
               LivingEntity target = CombatExtras.nearestEnemy(client, player, this.range.get(), true);
               if (target != null) {
                  double dist = player.distanceTo(target);
                  double ticks = this.predict.get() ? dist / 2.8 : 0.0;
                  float[] base = Rotations.face(player, target, ticks);
                  float gravityComp = (float)(dist * 0.14);
                  RotationManager.request(player, base[0], Mth.clamp(base[1] - gravityComp, -90.0F, 90.0F), 90.0F, this.silent.get(), 10, 3.0F);
               }
            }
         }
      }
   }

   static final class Quiver extends Module {
      private final NumberSetting charge = this.addSetting(new NumberSetting("Charge Ticks", "Ticks to pull bow upward", 4.0, 3.0, 10.0, 1.0));

      Quiver() {
         super("Quiver", "Shoot beneficial tipped arrows straight up to buff yourself.", Category.COMBAT);
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
