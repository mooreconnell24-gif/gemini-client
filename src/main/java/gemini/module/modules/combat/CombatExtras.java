package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.Holes;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.PlaceSettings;
import gemini.util.RotationManager;
import gemini.util.Rotations;
import gemini.util.Targets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class CombatExtras {
   private CombatExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new CombatExtras.AutoExp());
      sink.accept(new CombatExtras.AutoPot());
      sink.accept(new CombatExtras.TriggerBot());
      sink.accept(new CombatExtras.SuperKnockback());
      sink.accept(new CombatExtras.AimAssist());
      sink.accept(new CombatExtras.Backtrack());
      sink.accept(new CombatExtras.HoleFiller());
      sink.accept(new CombatExtras.Burrow());
      sink.accept(new CombatExtras.SelfTrap());
      sink.accept(new CombatExtras.WebAura());
      sink.accept(new CombatExtras.AnchorAura());
      sink.accept(new CombatExtras.BedAura());
      sink.accept(new CombatExtras.AutoSoup());
      sink.accept(new CombatExtras.AutoPearl());
      sink.accept(new CombatExtras.MaceKill());
      sink.accept(new CombatExtras.ShieldBreaker());
      sink.accept(new CombatExtras.WTap());
      sink.accept(new CombatExtras.KeepRange());
   }

   static LivingEntity nearestEnemy(Minecraft client, LocalPlayer player, double range, boolean mobs) {
      LivingEntity best = null;
      double bestDistance = range * range;

      for (Entity entity : client.level.entitiesForRendering()) {
         if (Targets.isCandidate(player, entity, true, mobs, false, true)) {
            double distance = player.distanceToSqr(entity);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = (LivingEntity)entity;
            }
         }
      }

      return best;
   }

   static boolean click(Minecraft client, LocalPlayer player, BlockPos pos, Direction face) {
      Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
      return client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false)).consumesAction();
   }

   static boolean withSlot(LocalPlayer player, Predicate<ItemStack> filter, Runnable action) {
      int slot = Inventories.findHotbar(player, filter);
      if (slot < 0) {
         return false;
      } else {
         int previous = player.getInventory().getSelectedSlot();
         player.getInventory().setSelectedSlot(slot);

         try {
            action.run();
         } finally {
            player.getInventory().setSelectedSlot(previous);
         }

         return true;
      }
   }

   static boolean armorDamaged(LocalPlayer player, int percent) {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = player.getItemBySlot(slot);
         if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
            double left = 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (left < percent) {
               return true;
            }
         }
      }

      return false;
   }

   public static final class AimAssist extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max target distance", 4.5, 2.0, 6.0, 0.1));
      private final NumberSetting fov = this.addSetting(new NumberSetting("FOV", "Field of view cone", 90.0, 15.0, 180.0, 5.0));
      private final NumberSetting speed = this.addSetting(new NumberSetting("Smooth Speed", "Camera assist speed in deg/tick", 14.0, 2.0, 45.0, 1.0));
      private final BooleanSetting weaponOnly = this.addSetting(new BooleanSetting("Weapon Only", "Only assist while holding a weapon", true));

      public AimAssist() {
         super("AimAssist", "Smoothly guide your crosshair toward nearby targets.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (this.weaponOnly.get() && !CombatModules.AutoWeapon.isWeapon(player.getMainHandItem())) {
               return;
            }

            LivingEntity target = CombatExtras.nearestEnemy(client, player, this.range.get(), true);
            if (target != null && Targets.inFov(player, target, this.fov.get())) {
               float[] look = Rotations.face(player, target);
               RotationManager.request(player, look[0], look[1], this.speed.floatValue(), false, 0, 2.0F);
            }
         }
      }
   }

   public static final class AnchorAura extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Target and placement range", 4.5, 2.0, 6.0, 0.5));
      private final NumberSetting maxSelf = this.addSetting(new NumberSetting("Max Self Damage", "Max allowed self explosion damage", 9.0, 2.0, 18.0, 1.0));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between anchor actions", 2.0, 0.0, 10.0, 1.0));
      private int wait;

      public AnchorAura() {
         super("AnchorAura", "Place, charge, and detonate Respawn Anchors on enemies.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && client.level.dimension() != Level.NETHER && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.range.get(), false);
            if (enemy != null) {
               BlockPos targetPos = enemy.blockPosition().above(2);
               if (!BlockPlacer.inReach(player, targetPos, this.range.get())) {
                  targetPos = enemy.blockPosition().above();
               }

               float selfDmg = Damage.crystal(player, Vec3.atCenterOf(targetPos));
               if (selfDmg > this.maxSelf.floatValue() || selfDmg + 1.5F >= Damage.health(player)) {
                  return;
               }

               BlockState state = client.level.getBlockState(targetPos);
               BlockPos finalPos = targetPos;
               if (state.is(Blocks.RESPAWN_ANCHOR)) {
                  RotationManager.request(player, RotationManager.toward(player, finalPos), 90.0F, true, 8);
                  int charges = (Integer)state.getValue(RespawnAnchorBlock.CHARGE);
                  if (charges == 0) {
                     CombatExtras.withSlot(player, s -> s.is(Items.GLOWSTONE), () -> CombatExtras.click(client, player, finalPos, Direction.UP));
                  } else {
                     CombatExtras.withSlot(player, s -> !s.is(Items.GLOWSTONE), () -> CombatExtras.click(client, player, finalPos, Direction.UP));
                  }
               } else if (BlockPlacer.replaceable(client.level, finalPos)) {
                  BlockPlacer.place(client, finalPos, s -> s.is(Items.RESPAWN_ANCHOR), true, true, true);
               }
            }
         }
      }
   }

   public static final class AutoExp extends Module {
      private final NumberSetting threshold = this.addSetting(
         new NumberSetting("Durability %", "Repair when armor durability is below %", 60.0, 5.0, 99.0, 1.0)
      );
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between throws", 2.0, 1.0, 20.0, 1.0));
      private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim down in packets only", true));
      private int wait;

      public AutoExp() {
         super("Auto EXP", "Throw XP bottles at your feet when armor is damaged.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (CombatExtras.armorDamaged(player, this.threshold.intValue())) {
               boolean aimed = RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, this.silent.get(), 0, 2.0F);
               if (aimed && this.wait-- <= 0) {
                  this.wait = this.delay.intValue();
                  CombatExtras.withSlot(player, stack -> stack.is(Items.EXPERIENCE_BOTTLE), () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND));
               }
            }
         }
      }
   }

   public static final class AutoPearl extends Module {
      private final NumberSetting health = this.addSetting(new NumberSetting("Panic Health", "Throw pearl below this health", 6.0, 1.0, 16.0, 1.0));
      private int cooldown;

      public AutoPearl() {
         super("AutoPearl", "Throw an Ender Pearl when health drops critically low.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (CombatModules.ready(client) && client.gui.screen() == null && Damage.health(client.player) <= this.health.floatValue()) {
            LocalPlayer player = client.player;
            if (CombatExtras.withSlot(player, s -> s.is(Items.ENDER_PEARL), () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND))) {
               this.cooldown = 40;
            }
         }
      }
   }

   public static final class AutoPot extends Module {
      private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Throw potion at or below this health", 10.0, 1.0, 20.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between throws", 10.0, 1.0, 60.0, 1.0));
      private final BooleanSetting silent = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward in packets only", true));
      private final BooleanSetting onlyHealing = this.addSetting(new BooleanSetting("Healing Only", "Use only healing/regen potions", true));
      private int wait;

      public AutoPot() {
         super("Auto Pot", "Splash healing potions at your feet when low on HP.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (!(player.getHealth() > this.health.get())
               && this.wait-- <= 0
               && RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, this.silent.get(), 10, 2.0F)
               && CombatExtras.withSlot(player, this::usable, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND))) {
               this.wait = this.delay.intValue();
            }
         }
      }

      private boolean usable(ItemStack stack) {
         if (!stack.is(Items.SPLASH_POTION)) {
            return false;
         } else {
            PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) {
               return false;
            } else {
               for (MobEffectInstance effect : contents.getAllEffects()) {
                  String id = effect.getEffect().getRegisteredName();
                  if (!this.onlyHealing.get() && ((MobEffect)effect.getEffect().value()).isBeneficial()) {
                     return true;
                  }

                  if (id.endsWith("instant_health") || id.endsWith("regeneration") || id.endsWith("absorption")) {
                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   public static final class AutoSoup extends Module {
      private final NumberSetting health = this.addSetting(new NumberSetting("Health", "Eat soup at or below this health", 12.0, 2.0, 19.0, 1.0));
      private final BooleanSetting dropBowl = this.addSetting(new BooleanSetting("Drop Bowl", "Drop empty bowl after eating", true));

      public AutoSoup() {
         super("AutoSoup", "Instantly eat mushroom stew when low on health.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && client.player.getHealth() <= this.health.floatValue()) {
            LocalPlayer player = client.player;
            CombatExtras.withSlot(player, s -> s.is(Items.MUSHROOM_STEW), () -> {
               client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
               if (this.dropBowl.get() && player.getMainHandItem().is(Items.BOWL)) {
                  player.drop(true);
               }
            });
         }
      }
   }

   public static final class Backtrack extends Module {
      private final NumberSetting maxTicks = this.addSetting(new NumberSetting("History Ticks", "Ticks of target positions to retain", 8.0, 2.0, 20.0, 1.0));
      private final BooleanSetting render = this.addSetting(new BooleanSetting("Render Box", "Draw delayed backtrack hitbox in 3D", true));
      private final ArrayDeque<AABB> history = new ArrayDeque<>();
      private LivingEntity tracked;

      public Backtrack() {
         super("Backtrack", "Show where your combat target was a few ticks ago.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         this.history.clear();
         this.tracked = null;
      }

      @Override
      public String info() {
         return this.maxTicks.intValue() + "t";
      }

      @Override
      public void onAttack(Entity target) {
         if (target instanceof LivingEntity living) {
            this.tracked = living;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (!CombatModules.ready(client)) {
            this.history.clear();
         } else {
            if (this.tracked == null || !this.tracked.isAlive() || this.tracked.distanceToSqr(client.player) > 49.0) {
               this.tracked = CombatExtras.nearestEnemy(client, client.player, 6.0, false);
               this.history.clear();
            }

            if (this.tracked != null) {
               this.history.addLast(this.tracked.getBoundingBox());

               while (this.history.size() > this.maxTicks.intValue()) {
                  this.history.removeFirst();
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         if (this.render.get() && !this.history.isEmpty()) {
            Render3D.box(this.history.getFirst(), -16722689, true, true, 1.5F, 40, true);
         }
      }
   }

   public static final class BedAura extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Bed placement and detonation reach", 4.5, 2.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bed actions", 3.0, 1.0, 15.0, 1.0));
      private int wait;

      public BedAura() {
         super("BedAura", "Place and detonate beds near enemies in Nether or End.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && client.level.dimension() != Level.OVERWORLD && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.range.get(), false);
            if (enemy != null) {
               BlockPos pos = enemy.blockPosition().above();
               if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
                  RotationManager.request(player, RotationManager.toward(player, pos), 90.0F, true, 8);
                  CombatExtras.click(client, player, pos, Direction.UP);
               } else {
                  BlockPlacer.place(client, pos, stack -> stack.is(ItemTags.BEDS), true, true, true, false);
               }
            }
         }
      }
   }

   public static final class Burrow extends Module {
      private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward in packets", true));

      public Burrow() {
         super("Burrow", "Lag-place obsidian or ender chest inside your feet.", Category.COMBAT);
      }

      @Override
      public boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onEnable() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener conn = client.getConnection();
         if (CombatModules.ready(client) && conn != null && player.onGround()) {
            BlockPos feet = player.blockPosition();
            Predicate<ItemStack> burrowBlock = CombatModules.obsidianOrResistant().or(s -> s.is(Items.ANVIL) || s.is(Items.CHIPPED_ANVIL));
            int slot = Inventories.findHotbar(player, burrowBlock);
            if (slot >= 0) {
               double x = player.getX();
               double y = player.getY();
               double z = player.getZ();
               conn.send(new Pos(x, y + 0.41999998688698, z, true, false));
               conn.send(new Pos(x, y + 0.7531999805212, z, true, false));
               conn.send(new Pos(x, y + 1.00133597911214, z, true, false));
               conn.send(new Pos(x, y + 1.16610926093821, z, true, false));
               if (this.rotate.get()) {
                  RotationManager.request(player, player.getYRot(), 90.0F, 0.0F, true, 10, 5.0F);
               }

               BlockPlacer.place(client, feet, burrowBlock, true, true, true, false);
               conn.send(new Pos(x, y + 2.34, z, false, false));
            }
         }

         this.setEnabled(false, false);
      }
   }

   public static final class HoleFiller extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.8);
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Hole scan radius", 5.0, 2.0, 6.0, 1.0));
      private final BooleanSetting smart = this.addSetting(new BooleanSetting("Smart Only", "Only fill holes close to an enemy", true));

      public HoleFiller() {
         super("HoleFiller", "Fill nearby safe holes before enemies can enter them.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && this.placer.ready()) {
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, 8.0, false);
            if (this.smart.get() && enemy == null) {
               return;
            }

            List<BlockPos> holes = Holes.around(client.level, player.blockPosition(), this.radius.intValue(), 2);
            List<BlockPos> targets = new ArrayList<>();

            for (BlockPos hole : holes) {
               if (!hole.equals(player.blockPosition()) && (enemy == null || enemy.blockPosition().distSqr(hole) <= 9.0)) {
                  targets.add(hole);
               }
            }

            this.placer.place(client, targets, CombatModules.obsidianOrResistant());
         }
      }
   }

   public static final class KeepRange extends Module {
      private final NumberSetting minDistance = this.addSetting(
         new NumberSetting("Min Distance", "Back up if target is closer than this", 2.6, 1.5, 4.0, 0.1)
      );
      private final NumberSetting maxDistance = this.addSetting(new NumberSetting("Max Distance", "Max engagement distance", 3.6, 2.5, 5.5, 0.1));
      private final BooleanSetting onlyDuringCooldown = this.addSetting(
         new BooleanSetting("Wait Cooldown", "Only back up while attack is recharging", true)
      );
      private boolean backingUp;

      public KeepRange() {
         super("KeepRange", "Keep optimal melee spacing from your combat target.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         this.release(Minecraft.getInstance());
      }

      private void release(Minecraft client) {
         if (this.backingUp) {
            client.options.keyDown.setDown(MoveUtil.physicalKeyDown(client.options.keyDown));
            this.backingUp = false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            LivingEntity enemy = CombatExtras.nearestEnemy(client, player, this.maxDistance.get(), false);
            if (enemy != null) {
               double dist = player.distanceTo(enemy);
               boolean recharging = !this.onlyDuringCooldown.get() || player.getAttackStrengthScale(0.0F) < 0.85F;
               if (dist < this.minDistance.get() && recharging) {
                  client.options.keyDown.setDown(true);
                  this.backingUp = true;
                  return;
               }
            }

            this.release(client);
         } else {
            this.release(client);
         }
      }

      @Override
      public String info() {
         return String.format(Locale.ROOT, "%.1fm", this.minDistance.get());
      }
   }

   public static final class MaceKill extends Module {
      private final NumberSetting height = this.addSetting(new NumberSetting("Spoof Height", "Simulated fall height in blocks", 6.0, 2.0, 22.0, 1.0));

      public MaceKill() {
         super("MaceKill", "Spoof a vertical drop packet for Mace smash damage.", Category.COMBAT);
      }

      @Override
      public void onAttack(Entity target) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener conn = client.getConnection();
         if (player != null && conn != null && player.getMainHandItem().is(Items.MACE)) {
            double h = this.height.get();
            conn.send(new Pos(player.getX(), player.getY() + h, player.getZ(), false, false));
            conn.send(new Pos(player.getX(), player.getY(), player.getZ(), false, false));
         }
      }
   }

   public static final class SelfTrap extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.5);

      public SelfTrap() {
         super("SelfTrap", "Encase your upper body and head in obsidian.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && this.placer.ready()) {
            BlockPos feet = client.player.blockPosition();
            List<BlockPos> positions = List.of(feet.above().north(), feet.above().south(), feet.above().east(), feet.above().west(), feet.above(2));
            this.placer.place(client, positions, CombatModules.obsidianOrResistant());
         }
      }
   }

   public static final class ShieldBreaker extends Module {
      private int cooldown;

      public ShieldBreaker() {
         super("ShieldBreaker", "Swap to an axe and strike when an enemy blocks.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (CombatModules.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (CombatExtras.nearestEnemy(client, player, 4.2, false) instanceof Player targetPlayer && targetPlayer.isBlocking()) {
               CombatExtras.withSlot(player, s -> s.is(ItemTags.AXES), () -> {
                  client.gameMode.attack(player, targetPlayer);
                  player.swing(InteractionHand.MAIN_HAND);
                  this.cooldown = 6;
               });
            }
         }
      }
   }

   public static final class SuperKnockback extends Module {
      private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Sprint reset method on hit", "Packet", "Packet", "WTap", "StopStart"));
      private final BooleanSetting onlyMoving = this.addSetting(new BooleanSetting("Only Moving", "Require forward movement", true));

      public SuperKnockback() {
         super("SuperKnockback", "Reset sprint state on hit for maximum knockback.", Category.COMBAT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onAttack(Entity target) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         ClientPacketListener conn = client.getConnection();
         if (player != null && conn != null && target instanceof LivingEntity && (!this.onlyMoving.get() || MoveUtil.hasInput(player))) {
            if (this.mode.is("WTap")) {
               player.setSprinting(false);
               conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
               player.setSprinting(true);
            } else if (this.mode.is("StopStart")) {
               if (player.isSprinting()) {
                  conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
               }
               conn.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
               player.setSprinting(true);
            } else {
               conn.send(new ServerboundPlayerCommandPacket(player, Action.STOP_SPRINTING));
               conn.send(new ServerboundPlayerCommandPacket(player, Action.START_SPRINTING));
               player.setSprinting(true);
            }
         }
      }
   }

   public static final class TriggerBot extends Module {
      private final NumberSetting cooldown = this.addSetting(new NumberSetting("Cooldown %", "Required weapon charge % to attack", 92.0, 50.0, 100.0, 2.0));
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Attack players under crosshair", true));
      private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Attack hostile mobs under crosshair", true));
      private final BooleanSetting critsOnly = this.addSetting(new BooleanSetting("Wait For Fall", "Wait until falling when airborne for crits", false));

      public TriggerBot() {
         super("TriggerBot", "Attack targets under your crosshair when charged.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && client.hitResult != null && client.hitResult.getType() == Type.ENTITY) {
            LocalPlayer player = client.player;
            if (!player.isUsingItem() && player.getAttackStrengthScale(0.0F) * 100.0F >= this.cooldown.floatValue()) {
               if (this.critsOnly.get() && !player.onGround() && player.getDeltaMovement().y >= -0.02) {
                  return;
               }

               Entity hit = ((EntityHitResult)client.hitResult).getEntity();
               if (Targets.isCandidate(player, hit, this.players.get(), this.mobs.get(), false, true)) {
                  client.gameMode.attack(player, hit);
                  player.swing(InteractionHand.MAIN_HAND);
               }
            }
         }
      }
   }

   public static final class WTap extends Module {
      private final NumberSetting holdTicks = this.addSetting(
         new NumberSetting("Release Ticks", "Ticks to release W after landing a hit", 2.0, 1.0, 5.0, 1.0)
      );
      private int releaseTimer;

      public WTap() {
         super("WTap", "Briefly re-tap W after melee hits for combo knockback.", Category.COMBAT);
      }

      @Override
      public void onDisable() {
         if (this.releaseTimer > 0) {
            Minecraft client = Minecraft.getInstance();
            client.options.keyUp.setDown(MoveUtil.physicalKeyDown(client.options.keyUp));
            this.releaseTimer = 0;
         }
      }

      @Override
      public void onAttack(Entity target) {
         Minecraft client = Minecraft.getInstance();
         if (target instanceof LivingEntity && client.player != null && client.player.isSprinting()) {
            this.releaseTimer = this.holdTicks.intValue();
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.releaseTimer > 0 && client.player != null && client.gui.screen() == null) {
            this.releaseTimer--;
            if (this.releaseTimer > 0) {
               client.options.keyUp.setDown(false);
               client.player.setSprinting(false);
            } else {
               boolean down = MoveUtil.physicalKeyDown(client.options.keyUp);
               client.options.keyUp.setDown(down);
               if (down) {
                  client.player.setSprinting(true);
               }
            }
         }
      }

      @Override
      public String info() {
         return this.holdTicks.intValue() + "t";
      }
   }

   public static final class WebAura extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 1, 2, 4.5);
      private final NumberSetting range = this.addSetting(new NumberSetting("Enemy Range", "Target distance for cobwebs", 4.5, 2.0, 6.0, 0.5));

      public WebAura() {
         super("WebAura", "Place cobwebs at enemy feet to stall movement.", Category.COMBAT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (CombatModules.ready(client) && client.gui.screen() == null && this.placer.ready()) {
            LivingEntity enemy = CombatExtras.nearestEnemy(client, client.player, this.range.get(), false);
            if (enemy != null) {
               this.placer
                  .place(
                     client,
                     List.of(enemy.blockPosition(), enemy.blockPosition().above()),
                     stack -> stack.getItem() instanceof BlockItem bi && bi.getBlock() == Blocks.COBWEB,
                     false
                  );
            }
         }
      }
   }
}
