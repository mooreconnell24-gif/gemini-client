package gemini.module.modules.combat;

import gemini.Gemini;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.AutoTotemModule;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.PlaceSettings;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import gemini.util.VersionBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class CombatModules {
   private CombatModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new CombatModules.KillAura());
      sink.accept(new CombatModules.AutoCrystal());
      sink.accept(new CombatModules.Surround());
      sink.accept(new CombatModules.AutoTrap());
      sink.accept(new CombatModules.AutoWeapon());
      sink.accept(new CombatModules.Hitboxes());
      sink.accept(new CombatModules.Offhand());
      sink.accept(new AutoBlockModule());
      sink.accept(new BlockHitModule());
      BowModules.register(sink);
   }

   static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null && client.level != null && client.gameMode != null && !player.isSpectator() && !player.isDeadOrDying();
   }

   static Predicate<ItemStack> obsidianOrResistant() {
      return stack -> stack.getItem() instanceof BlockItem bi
         && (bi.getBlock() == Blocks.OBSIDIAN || bi.getBlock() == Blocks.CRYING_OBSIDIAN || bi.getBlock() == Blocks.ENDER_CHEST);
   }

   public static final class AutoCrystal extends Module {
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
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            LivingEntity target = CombatExtras.nearestEnemy(client, player, this.targetRange.get(), true);
            if (target == null) {
               this.renderPos = null;
            } else {
               if (this.breakTimer > 0) {
                  this.breakTimer--;
               } else {
                  Entity bestCrystal = this.findBestCrystal(level, player, target);
                  if (bestCrystal != null) {
                     float[] rot = RotationManager.toward(player, bestCrystal);
                     if (RotationManager.request(player, rot, 95.0F, this.silentRotate.get(), 10)) {
                        client.gameMode.attack(player, bestCrystal);
                        player.swing(InteractionHand.MAIN_HAND);
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
                        && (this.autoSwitch.is("None") || crystalSlot < 0)) {
                        return;
                     }

                     Vec3 hitVec = Vec3.atCenterOf(bestBase).add(0.0, 0.5, 0.0);
                     float[] rot = RotationManager.toward(player, hitVec);
                     if (RotationManager.request(player, rot, 95.0F, this.silentRotate.get(), 10)) {
                        int prev = player.getInventory().getSelectedSlot();
                        boolean swapped = false;
                        if (hand == InteractionHand.MAIN_HAND && !player.getMainHandItem().is(Items.END_CRYSTAL)) {
                           player.getInventory().setSelectedSlot(crystalSlot);
                           swapped = true;
                        }

                        client.gameMode.useItemOn(player, hand, new BlockHitResult(hitVec, Direction.UP, bestBase, false));
                        player.swing(hand);
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

   public static final class AutoTrap extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.8);
      private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Trap block coverage", "Full", "Full", "Head", "Feet"));
      private final NumberSetting targetRange = this.addSetting(new NumberSetting("Target Range", "Enemy search range", 5.0, 2.0, 7.0, 0.2));
      private final NumberSetting predictTicks = this.addSetting(new NumberSetting("Predict Ticks", "Predict enemy movement ticks ahead", 2.0, 0.0, 6.0, 1.0));
      private final BooleanSetting antiStep = this.addSetting(new BooleanSetting("Anti Step", "Cap top of trap to block stepping out", true));
      private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Highlight queued trap blocks", true));
      private final List<BlockPos> currentPlan = new ArrayList<>();

      public AutoTrap() {
         super("AutoTrap", "Encase nearby enemies in obsidian with predictive aim.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         this.currentPlan.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.targetRange.get(), true);
            if (enemy == null) {
               this.currentPlan.clear();
            } else {
               Vec3 predicted = enemy.position().add(enemy.getDeltaMovement().scale(this.predictTicks.get()));
               BlockPos base = BlockPos.containing(predicted.x, predicted.y + 0.1, predicted.z);
               this.currentPlan.clear();

               for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                  if (!this.mode.is("Head")) {
                     this.currentPlan.add(base.relative(dir));
                  }

                  if (!this.mode.is("Feet")) {
                     this.currentPlan.add(base.above().relative(dir));
                  }
               }

               if (!this.mode.is("Feet")) {
                  this.currentPlan.add(base.above().relative(Direction.NORTH).above());
                  this.currentPlan.add(base.above(2));
                  if (this.antiStep.get()) {
                     this.currentPlan.add(base.above(3));
                  }
               }

               if (this.placer.ready()) {
                  this.placer.place(client, this.currentPlan, CombatModules.obsidianOrResistant());
               }
            }
         } else {
            this.currentPlan.clear();
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         if (this.render.get() && client.level != null) {
            for (BlockPos pos : this.currentPlan) {
               if (BlockPlacer.replaceable(client.level, pos)) {
                  Render3D.block(pos, -16718337, true, true, 1.5F, 45, true);
               }
            }
         }
      }
   }

   public static final class AutoWeapon extends Module {
      private final ModeSetting prefer = this.addSetting(
         new ModeSetting("Prefer", "Preferred weapon type (Best compares DPS)", "Best", "Best", "Sword", "Axe", "Mace", "Trident")
      );
      private final BooleanSetting antiBreak = this.addSetting(new BooleanSetting("Anti Break", "Skip weapons near 0 durability", true));
      private final BooleanSetting keepCurrent = this.addSetting(new BooleanSetting("Keep Current", "Keep current slot if already holding a weapon", false));

      public AutoWeapon() {
         super("Auto Weapon", "Switch to your best hotbar weapon on attack.", Category.COMBAT);
      }

      @Override
      public void onAttack(Entity target) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && target instanceof LivingEntity) {
            int selected = player.getInventory().getSelectedSlot();
            if (!this.keepCurrent.get() || !isWeapon(player.getInventory().getItem(selected))) {
               int best = -1;
               double bestScore = 0.0;

               for (int slot = 0; slot < 9; slot++) {
                  ItemStack stack = player.getInventory().getItem(slot);
                  if (isWeapon(stack)
                     && this.matches(stack)
                     && (!this.antiBreak.get() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 2)) {
                     double score = dps(stack);
                     if (score > bestScore) {
                        bestScore = score;
                        best = slot;
                     }
                  }
               }

               if (best >= 0 && best != selected) {
                  player.getInventory().setSelectedSlot(best);
               }
            }
         }
      }

      private boolean matches(ItemStack stack) {
         String var2 = this.prefer.get();

         return switch (var2) {
            case "Sword" -> stack.is(ItemTags.SWORDS);
            case "Axe" -> stack.is(ItemTags.AXES);
            case "Mace" -> stack.is(Items.MACE);
            case "Trident" -> stack.is(Items.TRIDENT);
            default -> true;
         };
      }

      static boolean isWeapon(ItemStack stack) {
         return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
      }

      static double dps(ItemStack stack) {
         ItemAttributeModifiers modifiers = (ItemAttributeModifiers)stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
         double[] sums = new double[]{0.0, 0.0};
         modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value()) {
               sums[0] += modifier.amount();
            } else if (attribute.value() == Attributes.ATTACK_SPEED.value()) {
               sums[1] += modifier.amount();
            }
         });
         return (1.0 + sums[0]) * Math.max(0.5, 4.0 + sums[1]);
      }
   }

   public static final class Hitboxes extends Module {
      public static CombatModules.Hitboxes instance;
      private final NumberSetting expand = this.addSetting(new NumberSetting("Expand", "Extra blocks added to target hitboxes", 0.25, 0.0, 1.5, 0.05));
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Expand player hitboxes", true));
      private final BooleanSetting hostile = this.addSetting(new BooleanSetting("Hostile", "Expand hostile mob hitboxes", false));
      private final BooleanSetting passive = this.addSetting(new BooleanSetting("Passive", "Expand animal hitboxes", false));

      public Hitboxes() {
         super("Hitboxes", "Expand entity hitboxes for easier melee hits.", Category.COMBAT);
         instance = this;
      }

      public float extra(Entity entity) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && entity != player && entity instanceof LivingEntity) {
            return !Targets.isCandidate(player, entity, this.players.get(), this.hostile.get(), this.passive.get(), true) ? 0.0F : this.expand.floatValue();
         } else {
            return 0.0F;
         }
      }

      @Override
      public String info() {
         return String.format("%.2f", this.expand.get());
      }
   }

   public static final class KillAura extends Module {
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
         if (CombatModules.ready(client) && client.gui.screen() == null) {
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
            if (CombatModules.AutoWeapon.isWeapon(stack)) {
               double score = CombatModules.AutoWeapon.dps(stack);
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

   public static final class Offhand extends Module {
      private final ModeSetting item = this.addSetting(
         new ModeSetting("Item", "Preferred offhand item", "Totem", "Totem", "Golden Apple", "Enchanted Golden Apple", "End Crystal", "Shield")
      );
      private final NumberSetting totemHealth = this.addSetting(
         new NumberSetting("Totem Health", "Force totem at or below this health", 10.0, 0.0, 20.0, 1.0)
      );
      private final BooleanSetting swordGapple = this.addSetting(
         new BooleanSetting("Sword Gapple", "Hold golden apple while right-clicking with sword", false)
      );
      private final BooleanSetting hotbarOnly = this.addSetting(new BooleanSetting("Hotbar Only", "Only pull items from hotbar slots", false));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between offhand swaps", 2.0, 0.0, 20.0, 1.0));
      private int cooldown;

      public Offhand() {
         super("Offhand", "Keep your offhand stocked with totems.", Category.COMBAT);
      }

      @Override
      public String info() {
         return this.item.get();
      }

      public boolean wantsTotem(LocalPlayer player) {
         return this.totemHealth.get() > 0.0 && Damage.health(player) <= this.totemHealth.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (CombatModules.ready(client) && Inventories.inventoryOpenOrClosed(player)) {
            AutoTotemModule totem = Gemini.getInstance().modules().byClass(AutoTotemModule.class).orElse(null);
            if (totem == null || !totem.isEnabled() || !totem.wantsOffhand(player)) {
               if (this.cooldown > 0) {
                  this.cooldown--;
               } else {
                  Item wanted = this.wantedItem(client, player);
                  if (!player.getOffhandItem().is(wanted)) {
                     Item chosen = wanted;
                     int index = Inventories.find(player, stack -> stack.is(wanted), this.hotbarOnly.get());
                     if (index < 0 && (wanted == Items.ENCHANTED_GOLDEN_APPLE || wanted == Items.GOLDEN_APPLE)) {
                        Item fallback = wanted == Items.GOLDEN_APPLE ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
                        chosen = fallback;
                        index = Inventories.find(player, stack -> stack.is(fallback), this.hotbarOnly.get());
                     }

                     if (index >= 0 && !player.getOffhandItem().is(chosen)) {
                        this.cooldown = this.delay.intValue();
                        Inventories.swapToOffhand(client, player, index);
                     }
                  }
               }
            }
         }
      }

      private Item wantedItem(Minecraft client, LocalPlayer player) {
         if (this.totemHealth.get() > 0.0 && Damage.health(player) <= this.totemHealth.get()) {
            return Items.TOTEM_OF_UNDYING;
         } else if (this.swordGapple.get() && client.options.keyUse.isDown() && player.getMainHandItem().is(ItemTags.SWORDS)) {
            return Items.ENCHANTED_GOLDEN_APPLE;
         } else {
            String var3 = this.item.get();

            return switch (var3) {
               case "Golden Apple" -> Items.GOLDEN_APPLE;
               case "Enchanted Golden Apple" -> Items.ENCHANTED_GOLDEN_APPLE;
               case "End Crystal" -> Items.END_CRYSTAL;
               case "Shield" -> Items.SHIELD;
               default -> Items.TOTEM_OF_UNDYING;
            };
         }
      }
   }

   public static final class Surround extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 0, 4, 4.5);
      private final BooleanSetting center = this.addSetting(new BooleanSetting("Auto Center", "Snap to block center on enable", true));
      private final BooleanSetting support = this.addSetting(
         new BooleanSetting("Support Blocks", "Place support blocks under air edges", true)
      );
      private final BooleanSetting onlyGround = this.addSetting(new BooleanSetting("Only Ground", "Only place while standing on ground", true));
      private final BooleanSetting disableOnJump = this.addSetting(new BooleanSetting("Disable On Jump", "Disable when jumping out of hole", true));
      private int startY;

      public Surround() {
         super("Surround", "Surround your feet with obsidian to block crystal damage.", Category.COMBAT);
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            this.startY = player.getBlockY();
            if (this.center.get() && player.onGround()) {
               double cx = Math.floor(player.getX()) + 0.5;
               double cz = Math.floor(player.getZ()) + 0.5;
               player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
               player.setPos(cx, player.getY(), cz);
            }
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (this.disableOnJump.get() && player.getBlockY() > this.startY) {
               this.setEnabled(false);
               return;
            }

            if (this.onlyGround.get() && !player.onGround()) {
               return;
            }

            if (this.placer.ready()) {
               BlockPos feet = player.blockPosition();
               List<BlockPos> targets = new ArrayList<>(10);
               targets.add(feet.below());

               for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                  BlockPos side = feet.relative(dir);
                  if (this.support.get() && BlockPlacer.findPlacement(client.level, side, false) == null) {
                     targets.add(side.below());
                  }

                  targets.add(side);
               }

               this.placer.place(client, targets, CombatModules.obsidianOrResistant());
            }
         }
      }
   }
}
