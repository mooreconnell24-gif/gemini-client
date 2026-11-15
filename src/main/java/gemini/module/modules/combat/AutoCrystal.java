package gemini.module.modules.combat;

import gemini.util.Targets;
import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.VersionBridge;
import gemini.util.Hands;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class AutoCrystal extends Module {
   private final NumberSetting targetRange = this.addSetting(new NumberSetting("Target Range", "Enemy scan distance", 10.0, 4.0, 16.0, 0.5));
   private final NumberSetting placeRange = this.addSetting(new NumberSetting("Place Range", "Max crystal placement reach", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting breakRange = this.addSetting(new NumberSetting("Break Range", "Max crystal attack reach", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting wallRange = this.addSetting(new NumberSetting("Wall Range", "Max reach through walls", 3.2, 0.0, 6.0, 0.1));
   private final NumberSetting minDamage = this.addSetting(new NumberSetting("Min Damage", "Minimum damage dealt to enemy", 6.0, 1.0, 20.0, 0.5));
   private final NumberSetting maxSelf = this.addSetting(new NumberSetting("Max Self Damage", "Maximum self-damage allowed", 8.0, 1.0, 20.0, 0.5));
   private final BooleanSetting antiSuicide = this.addSetting(
      new BooleanSetting("Anti Suicide", "Avoid crystals that could pop or kill you", true)
   );
   private final NumberSetting placeDelay = this.addSetting(new NumberSetting("Place Delay", "Ticks between crystal placements", 1.0, 0.0, 10.0, 1.0));
   private final NumberSetting breakDelay = this.addSetting(new NumberSetting("Break Delay", "Ticks between crystal attacks", 1.0, 0.0, 10.0, 1.0));
   private final BooleanSetting predict = this.addSetting(new BooleanSetting("Predictive Raycast", "Predict enemy movement ticks ahead", true));
   private final NumberSetting predictTicks = this.addSetting(
      new NumberSetting("Predict Ticks", "Movement ticks to project target ahead", 3.0, 1.0, 8.0, 1.0).showIf(this.predict::get)
   );
   private final BooleanSetting supportPlace = this.addSetting(
      new BooleanSetting("Support Obsidian", "Place obsidian base if none is available", false)
   );
   private final ModeSetting autoSwitch = this.addSetting(
      new ModeSetting("Switch", "Hotbar crystal selection mode", "Silent", "None", "Normal", "Silent")
   );
   private final BooleanSetting silentRotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim in packets without moving camera", true));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Highlight active crystal base and damage", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight color", 280).showIf(this.render::get));
   private int placeTimer;
   private int breakTimer;
   private BlockPos renderPos;
   private float renderDamage;

   public AutoCrystal() {
      super("AutoCrystal", "Predictive End Crystal placement and detonation.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.renderPos = null;
      this.renderDamage = 0.0F;
   }

   @Override
   public String info() {
      return this.renderPos != null ? String.format(Locale.ROOT, "%.1f", this.renderDamage) : null;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         LivingEntity target = Targets.nearestEnemy(player, client.level.entitiesForRendering(), this.targetRange.get(), true);
         if (target == null) {
            this.renderPos = null;
         } else {
            if (this.breakTimer > 0) {
               this.breakTimer--;
            } else {
               Entity bestCrystal = this.findBestCrystal(level, player, target);
               if (bestCrystal != null) {
                  float[] rot = RotationManager.toward(player, bestCrystal);
                  if (RotationManager.request(player, rot, 95.0F, this.silentRotate.get(), ActionPriority.COMBAT)) {
                     client.gameMode.attack(player, bestCrystal);
                     Swing.attack(player, InteractionHand.MAIN_HAND);
                     this.breakTimer = this.breakDelay.intValue();
                  }
               }
            }

            if (this.placeTimer > 0) {
               this.placeTimer--;
            } else {
               BlockPos bestBase = this.findBestBase(level, player, target, false);
               if (bestBase == null && this.supportPlace.get()) {
                  BlockPos support = this.findBestBase(level, player, target, true);
                  if (support != null && BlockPlacer.place(client, support, stack -> stack.is(Items.OBSIDIAN), true, this.silentRotate.get(), true)) {
                     bestBase = support;
                  }
               }

               this.renderPos = bestBase;
               if (bestBase != null) {
                  InteractionHand hand = player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                  int crystalSlot = Inventories.findHotbar(player, stack -> stack.is(Items.END_CRYSTAL));
                  if (hand == InteractionHand.MAIN_HAND
                     && !player.getMainHandItem().is(Items.END_CRYSTAL)
                     && (this.autoSwitch.is("None") || crystalSlot < 0 || !Hands.claimMainHand())) {
                     return;
                  }

                  Vec3 hitVec = Vec3.atCenterOf(bestBase).add(0.0, 0.5, 0.0);
                  float[] rot = RotationManager.toward(player, hitVec);
                  if (RotationManager.request(player, rot, 95.0F, this.silentRotate.get(), ActionPriority.COMBAT)) {
                     int prev = player.getInventory().getSelectedSlot();
                     boolean swapped = false;
                     if (hand == InteractionHand.MAIN_HAND && !player.getMainHandItem().is(Items.END_CRYSTAL)) {
                        player.getInventory().setSelectedSlot(crystalSlot);
                        swapped = true;
                     }

                     client.gameMode.useItemOn(player, hand, new BlockHitResult(hitVec, Direction.UP, bestBase, false));
                     Swing.interact(player, hand);
                     if (swapped && this.autoSwitch.is("Silent")) {
                        player.getInventory().setSelectedSlot(prev);
                     }

                     this.placeTimer = this.placeDelay.intValue();
                  }
               }
            }
         }
      } else {
         this.renderPos = null;
      }
   }

   private Entity findBestCrystal(ClientLevel level, LocalPlayer player, LivingEntity target) {
      Entity best = null;
      float bestDmg = this.minDamage.floatValue();
      double maxR = this.breakRange.get();
      double wallR = this.wallRange.get();
      float selfHealth = Damage.health(player);

      for (Entity entity : level.entitiesForRendering()) {
         if (entity.getType() == EntityTypes.END_CRYSTAL && entity.isAlive()) {
            double dist = Math.sqrt(entity.distanceToSqr(player.getEyePosition()));
            boolean visible = player.hasLineOfSight(entity);
            if (dist <= (visible ? maxR : wallR)) {
               Vec3 explosionCenter = entity.position();
               float selfDmg = Damage.crystal(player, explosionCenter);
               if (selfDmg <= this.maxSelf.floatValue() && (!this.antiSuicide.get() || selfDmg + 1.5F < selfHealth)) {
                  float targetDmg = this.predictedCrystalDamage(target, explosionCenter);
                  if (targetDmg >= bestDmg) {
                     bestDmg = targetDmg;
                     best = entity;
                  }
               }
            }
         }
      }

      return best;
   }

   private BlockPos findBestBase(Level level, LocalPlayer player, LivingEntity target, boolean allowReplaceableBase) {
      int r = (int)Math.ceil(this.placeRange.get());
      BlockPos eyeBlock = BlockPos.containing(player.getEyePosition());
      MutableBlockPos cursor = new MutableBlockPos();
      BlockPos best = null;
      float bestDmg = this.minDamage.floatValue();
      float selfHealth = Damage.health(player);
      double maxR2 = this.placeRange.get() * this.placeRange.get();
      double wallR2 = this.wallRange.get() * this.wallRange.get();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               cursor.set(eyeBlock.getX() + dx, eyeBlock.getY() + dy, eyeBlock.getZ() + dz);
               if (canPlaceCrystal(level, cursor, allowReplaceableBase)) {
                  Vec3 topCenter = new Vec3(cursor.getX() + 0.5, cursor.getY() + 1.0, cursor.getZ() + 0.5);
                  double d2 = player.getEyePosition().distanceToSqr(topCenter);
                  boolean lineOfSight = canSeeSpot(level, player.getEyePosition(), topCenter);
                  if (d2 <= (lineOfSight ? maxR2 : wallR2)) {
                     float selfDmg = Damage.crystal(player, topCenter);
                     if (selfDmg <= this.maxSelf.floatValue() && (!this.antiSuicide.get() || selfDmg + 1.5F < selfHealth)) {
                        float targetDmg = this.predictedCrystalDamage(target, topCenter);
                        if (targetDmg >= bestDmg) {
                           bestDmg = targetDmg;
                           best = cursor.immutable();
                        }
                     }
                  }
               }
            }
         }
      }

      if (!allowReplaceableBase) {
         this.renderDamage = best != null ? bestDmg : 0.0F;
      }
      return best;
   }

   private float predictedCrystalDamage(LivingEntity target, Vec3 crystalCenter) {
      if (!this.predict.get()) {
         return Damage.crystal(target, crystalCenter);
      } else {
         Vec3 shift = target.getDeltaMovement().scale(this.predictTicks.get());
         Vec3 shiftedCrystal = crystalCenter.subtract(shift);
         return Damage.crystal(target, shiftedCrystal);
      }
   }

   private static boolean canPlaceCrystal(Level level, BlockPos base, boolean allowReplaceableBase) {
      BlockState state = level.getBlockState(base);
      if (!allowReplaceableBase && !state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
         return false;
      } else if (allowReplaceableBase && (!BlockPlacer.replaceable(level, base) || BlockPlacer.findPlacement(level, base, false) == null)) {
         return false;
      } else {
         BlockPos above = base.above();
         if (!VersionBridge.canPlaceCrystalSpace(level, base)) {
            return false;
         } else {
            AABB box = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
            return level.getEntities((Entity)null, box, e -> e.isAlive() && !e.isSpectator() && e.getType() != EntityTypes.END_CRYSTAL).isEmpty();
         }
      }
   }

   private static boolean canSeeSpot(Level level, Vec3 eye, Vec3 spot) {
      return level.clip(new ClipContext(eye, spot, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player)).getType() == Type.MISS;
   }

   @Override
   public void onRender3D(float partialTick) {
      if (this.render.get() && this.renderPos != null) {
         int argb = this.color.argb();
         Render3D.block(this.renderPos, argb, true, true, 1.8F, 65, true);
         Render3D.text(String.format(Locale.ROOT, "%.1f", this.renderDamage), Vec3.atCenterOf(this.renderPos).add(0.0, 0.65, 0.0), -1, 0.85F, true);
      }
   }
}
