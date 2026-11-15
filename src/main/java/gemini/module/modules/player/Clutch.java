package gemini.module.modules.player;

import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.AttributeBoost;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.SpCommands;
import gemini.util.Swing;
import gemini.util.VersionBridge;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An all-round life saver. Every tick it looks for whatever is about to kill you (a lethal fall, lava below or
 * ahead, fire, the void, drowning, suffocation, low health) and answers it with the best thing in your hotbar: a
 * water-bucket MLG, a cobweb, slime block, powder snow or scaffolding catch, a totem, a water drop to put out
 * fire. When no item can help, the single-player last resort resets fall damage, gives fire resistance or water
 * breathing, heals, or teleports you back to the last safe spot. On top of that it can switch other modules on
 * while you are in danger and turn them back off once you are safe.
 */
public final class Clutch extends Module {
   /** Vanilla block interaction range; the server ray-casts buckets from your eyes this far. */
   private static final double REACH = 4.5;
   private static final int SCAN_DEPTH = 96;

   private final BooleanSetting falls = this.addSetting(new BooleanSetting("Falls", "MLG out of falls that would hurt or kill you", true));
   private final ModeSetting fallTrigger = this.addSetting(
      new ModeSetting("Fall Trigger", "Lethal clutches only falls that would kill you; Any Damage clutches every fall that would hurt", "Lethal", "Lethal", "Any Damage")
         .showIf(this.falls::get)
   );
   private final NumberSetting margin = this.addSetting(
      new NumberSetting("Margin", "Treat a fall as lethal if it would leave you with this much health (half-hearts) or less", 2.0, 0.0, 10.0, 1.0)
         .showIf(() -> this.falls.get() && this.fallTrigger.is("Lethal"))
   );
   private final BooleanSetting brake = this.addSetting(
      new BooleanSetting("Brake", "Slow a very fast fall just enough that the MLG lands inside your reach", true).showIf(this.falls::get)
   );
   private final BooleanSetting scoop = this.addSetting(new BooleanSetting("Scoop", "Pick placed water back up once you are safe", true));
   private final BooleanSetting lava = this.addSetting(new BooleanSetting("Lava", "Save yourself from falling or standing in lava", true));
   private final BooleanSetting lavaGuard = this.addSetting(
      new BooleanSetting("Lava Guard", "Stop before walking or stepping off into lava", true).showIf(this.lava::get)
   );
   private final BooleanSetting fire = this.addSetting(new BooleanSetting("Fire", "Put yourself out with water when burning", true));
   private final NumberSetting fireHealth = this.addSetting(
      new NumberSetting("Fire Health", "Only put out fire once health (half-hearts) is at or below this", 14.0, 1.0, 20.0, 1.0).showIf(this.fire::get)
   );
   private final BooleanSetting voidSave = this.addSetting(new BooleanSetting("Void", "Rescue yourself from falling into the void", true));
   private final BooleanSetting drowning = this.addSetting(new BooleanSetting("Drowning", "Swim up when you run low on air", true));
   private final BooleanSetting suffocation = this.addSetting(new BooleanSetting("Suffocation", "Get out of blocks you are stuck inside", true));
   private final BooleanSetting lowHealth = this.addSetting(new BooleanSetting("Low Health", "React when health drops low from anything", true));
   private final NumberSetting health = this.addSetting(
      new NumberSetting("Health", "Low health threshold (half-hearts, absorption included)", 6.0, 1.0, 19.0, 1.0).showIf(this.lowHealth::get)
   );
   private final BooleanSetting totem = this.addSetting(
      new BooleanSetting("Totem", "Move a totem into your offhand when health is low", true).showIf(this.lowHealth::get)
   );
   private final BooleanSetting lastResort = this.addSetting(
      new BooleanSetting(
         "Last Resort",
         "When no item can save you: reset fall damage, /effect fire resistance, water breathing or healing, or teleport back to the last safe spot",
         true
      )
   );
   private final BooleanSetting silentAim = this.addSetting(new BooleanSetting("Silent Aim", "Aim down only in packets", true));
   private final ListSetting dangerModules = this.addSetting(
      new ListSetting("Danger Modules", "Modules switched on during any danger", ListSetting.Kind.TEXT)
   );
   private final ListSetting fallModules = this.addSetting(
      new ListSetting("Fall Modules", "Modules switched on during a dangerous fall or a void fall", ListSetting.Kind.TEXT)
   );
   private final ListSetting lavaModules = this.addSetting(
      new ListSetting("Lava Modules", "Modules switched on near lava or while burning", ListSetting.Kind.TEXT, "Jesus")
   );
   private final ListSetting healthModules = this.addSetting(
      new ListSetting("Health Modules", "Modules switched on while health is low", ListSetting.Kind.TEXT, "AutoTotem")
   );
   private final BooleanSetting restore = this.addSetting(
      new BooleanSetting("Restore Modules", "Switch the modules Clutch turned on back off once you are safe", true)
   );
   private final NumberSetting restoreDelay = this.addSetting(
      new NumberSetting("Restore Delay", "Ticks of safety before those modules are switched off again", 40.0, 0.0, 400.0, 5.0).showIf(this.restore::get)
   );
   private final BooleanSetting notify = this.addSetting(new BooleanSetting("Notify", "Say in chat what saved you", true));

   private final Map<Danger, Integer> commandCooldown = new EnumMap<>(Danger.class);
   private final Set<String> enabledByUs = new LinkedHashSet<>();
   private int itemCooldown;
   private int safeTicks;
   private int lavaTicks;
   private int wallTicks;
   private BlockPos placedWater;
   private int scoopTicks;
   private boolean haveSafe;
   private String safeDimension;
   private double safeX;
   private double safeY;
   private double safeZ;
   private int saves;
   private String lastSave;

   public Clutch() {
      super("Clutch", "Saves you from lethal falls, lava, fire, the void, drowning and more, and can switch other modules on.", Category.PLAYER);
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.CRITICAL_SURVIVAL;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public List<String> aliases() {
      List<String> list = new ArrayList<>(super.aliases());
      list.add("MLG");
      list.add("Life Saver");
      return list;
   }

   @Override
   public void onEnable() {
      this.commandCooldown.clear();
      this.enabledByUs.clear();
      this.itemCooldown = 0;
      this.safeTicks = 0;
      this.lavaTicks = 0;
      this.wallTicks = 0;
      this.placedWater = null;
      this.scoopTicks = 0;
      this.haveSafe = false;
      this.saves = 0;
      this.lastSave = null;
   }

   @Override
   public void onDisable() {
      this.restoreModules();
      this.placedWater = null;
   }

   @Override
   public String info() {
      return this.saves == 0 ? null : this.lastSave + " · " + this.saves;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      Level level = client.level;
      if (player == null || level == null || client.gameMode == null || !player.isAlive() || player.isSpectator()) {
         return;
      }

      if (this.itemCooldown > 0) {
         this.itemCooldown--;
      }

      this.commandCooldown.replaceAll((danger, ticks) -> Math.max(0, ticks - 1));
      if (player.getAbilities().invulnerable || player.getAbilities().flying) {
         this.trackSafety(EnumSet.noneOf(Danger.class));
         return;
      }

      boolean canUseItems = client.gui.screen() == null;
      Set<Danger> dangers = EnumSet.noneOf(Danger.class);
      this.tickScoop(client, player, level, canUseItems);

      if (this.falls.get() || this.lava.get() || this.voidSave.get()) {
         this.handleFall(client, player, level, canUseItems, dangers);
      }

      if (this.lava.get()) {
         this.handleLava(client, player, level, canUseItems, dangers);
      }

      if (this.fire.get()) {
         this.handleFire(client, player, level, canUseItems, dangers);
      }

      if (this.drowning.get()) {
         this.handleDrowning(player, level, dangers);
      }

      if (this.suffocation.get()) {
         this.handleSuffocation(player, dangers);
      } else {
         this.wallTicks = 0;
      }

      if (this.lowHealth.get()) {
         this.handleLowHealth(client, player, dangers);
      }

      this.recordSafeSpot(player, level, dangers);
      this.trackSafety(dangers);
   }

   // ---------------------------------------------------------------- falls, lava falls and the void

   private void handleFall(Minecraft client, LocalPlayer player, Level level, boolean canUseItems, Set<Danger> dangers) {
      Vec3 motion = player.getDeltaMovement();
      if (player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava() || player.onClimbable() || motion.y >= -0.08) {
         return;
      }

      Landing landing = scanBelow(level, player);
      int minY = VersionBridge.minWorldY(level);
      if (landing == null) {
         if (this.voidSave.get() && player.getY() - minY < 12.0) {
            dangers.add(Danger.VOID);
            this.saveFromVoid(player, level, minY);
         }

         return;
      }

      double gap = player.getY() - landing.top;
      double nextGap = gap + motion.y;
      if (landing.kind == LandingKind.LAVA) {
         if (this.lava.get() && !fireResistant(player)) {
            dangers.add(Danger.LAVA);
            this.saveFromLavaFall(client, player, level, landing, gap, nextGap, canUseItems);
         }

         return;
      }

      if (!this.falls.get()) {
         return;
      }

      float predicted = predictFallDamage(player, (double)player.fallDistance + gap, landing.multiplier);
      float health = Damage.health(player);
      boolean dangerous = this.fallTrigger.is("Any Damage") ? predicted > 0.0F : predicted > 0.0F && predicted >= health - this.margin.floatValue();
      if (!dangerous) {
         return;
      }

      dangers.add(Danger.FALL);
      boolean nether = level.dimension() == Level.NETHER;
      FallSave save = canUseItems ? pickFallSave(player, nether, predicted, health) : null;
      double window = REACH - player.getEyeHeight() - 0.05;
      if (save != null) {
         double minGap = save.needsRoom ? 1.05 : 0.02;
         if (gap > window && nextGap < minGap && this.brake.get()) {
            // Too fast to land inside reach: slow down so the next tick ends up comfortably within it.
            double target = Math.max(minGap + 0.3, Math.min(window - 0.2, 1.8));
            player.setDeltaMovement(motion.x, -(gap - target), motion.z);
            return;
         }

         if (gap <= window && gap >= minGap && this.itemCooldown <= 0 && this.useFallSave(client, player, level, landing, save)) {
            this.itemCooldown = 4;
            this.saved(save.label);
            return;
         }

         if (gap > window) {
            return;
         }
      }

      // Nothing in reach can catch you: reset the fall on the integrated server just before you land.
      if (this.lastResort.get() && nextGap + motion.y * 0.98 <= 0.3) {
         AttributeBoost.resetFall(player);
         if (this.ready(Danger.FALL, 10)) {
            this.saved("Fall reset");
         }
      }
   }

   private void saveFromLavaFall(Minecraft client, LocalPlayer player, Level level, Landing landing, double gap, double nextGap, boolean canUseItems) {
      double window = REACH - player.getEyeHeight() - 0.05;
      boolean nether = level.dimension() == Level.NETHER;
      if (canUseItems && this.itemCooldown <= 0 && gap <= window) {
         // Water on lava turns it to obsidian or stone; the bucket ray goes through the lava to the floor below.
         BlockPos floor = landing.pos.below();
         boolean floorInReach = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty() && gap + 1.0 <= window;
         int water = nether ? -1 : findHotbar(player, s -> s.is(Items.WATER_BUCKET));
         if (water != NONE && floorInReach && this.useDown(client, player, water)) {
            // No scoop here: picking the water back up would uncover the lava again.
            this.itemCooldown = 4;
            this.saved("Water on lava");
            return;
         }

         // Otherwise cover the lava surface with a block, if something solid is next to it.
         if (gap >= 1.05 && BlockPlacer.findPlacement(level, landing.pos, false) != null
            && BlockPlacer.place(client, landing.pos, BlockPlacer::solidFiller, true, true, false)) {
            this.itemCooldown = 4;
            this.saved("Block on lava");
            return;
         }
      }

      if (this.lastResort.get() && nextGap <= 1.0 && this.ready(Danger.LAVA, 40)) {
         if (SpCommands.run("effect give @s minecraft:fire_resistance 30 0 true")) {
            AttributeBoost.resetFall(player);
            this.saved("Fire resistance");
         }
      }
   }

   private void saveFromVoid(LocalPlayer player, Level level, int minY) {
      if (!this.lastResort.get() || player.getY() > minY + 2.0 || !this.ready(Danger.VOID, 20)) {
         return;
      }

      String dimension = level.dimension().identifier().toString();
      if (this.haveSafe && dimension.equals(this.safeDimension) && this.teleportSafe(player)) {
         this.saved("Void rescue");
      }
   }

   // ---------------------------------------------------------------- lava on the ground and fire

   private void handleLava(Minecraft client, LocalPlayer player, Level level, boolean canUseItems, Set<Danger> dangers) {
      if (fireResistant(player)) {
         this.lavaTicks = 0;
         return;
      }

      if (player.isInLava()) {
         dangers.add(Danger.LAVA);
         this.lavaTicks++;
         boolean nether = level.dimension() == Level.NETHER;
         int water = nether ? -1 : findHotbar(player, s -> s.is(Items.WATER_BUCKET));
         if (canUseItems && water != NONE && this.itemCooldown <= 0 && this.useDown(client, player, water)) {
            this.itemCooldown = 6;
            this.saved("Water in lava");
         }

         // Swim out: up, and back toward the last safe spot when there is one nearby.
         Vec3 motion = player.getDeltaMovement();
         double x = motion.x;
         double z = motion.z;
         if (this.haveSafe && level.dimension().identifier().toString().equals(this.safeDimension)) {
            double dx = this.safeX - player.getX();
            double dz = this.safeZ - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.3 && distance < 16.0) {
               x = dx / distance * 0.12;
               z = dz / distance * 0.12;
            }
         }

         player.setDeltaMovement(x, Math.max(motion.y, 0.12), z);
         if (this.lastResort.get() && (this.lavaTicks >= 6 || Damage.health(player) <= this.health.floatValue()) && this.ready(Danger.LAVA, 40)) {
            boolean ok = SpCommands.run("effect give @s minecraft:fire_resistance 30 0 true");
            if (this.haveSafe && level.dimension().identifier().toString().equals(this.safeDimension)) {
               this.teleportSafe(player);
            }

            if (ok) {
               this.saved("Lava escape");
            }
         }

         return;
      }

      this.lavaTicks = 0;
      if (this.lavaGuard.get() && player.onGround() && lavaAhead(level, player)) {
         dangers.add(Danger.LAVA);
         Vec3 motion = player.getDeltaMovement();
         player.setDeltaMovement(0.0, motion.y, 0.0);
      }
   }

   private void handleFire(Minecraft client, LocalPlayer player, Level level, boolean canUseItems, Set<Danger> dangers) {
      if (!player.isOnFire() || player.isInLava() || player.isInWater() || fireResistant(player)) {
         return;
      }

      float health = Damage.health(player);
      if (health > this.fireHealth.floatValue()) {
         return;
      }

      dangers.add(Danger.FIRE);
      boolean nether = level.dimension() == Level.NETHER;
      int water = nether ? -1 : findHotbar(player, s -> s.is(Items.WATER_BUCKET));
      if (canUseItems && water != NONE && player.onGround() && this.itemCooldown <= 0 && this.placedWater == null && this.useDown(client, player, water)) {
         this.itemCooldown = 10;
         this.placedWater = player.blockPosition();
         this.scoopTicks = 20;
         this.saved("Extinguished");
         return;
      }

      if (this.lastResort.get() && health <= this.health.floatValue() && this.ready(Danger.FIRE, 60)) {
         if (SpCommands.run("effect give @s minecraft:fire_resistance 15 0 true")) {
            this.saved("Fire resistance");
         }
      }
   }

   // ---------------------------------------------------------------- drowning, suffocation, low health

   private void handleDrowning(LocalPlayer player, Level level, Set<Danger> dangers) {
      if (!player.isEyeInFluid(FluidTags.WATER) || player.hasEffect(MobEffects.WATER_BREATHING) || player.getAirSupply() > 80) {
         return;
      }

      dangers.add(Danger.DROWNING);
      BlockPos head = BlockPos.containing(player.getX(), player.getEyeY() + 0.6, player.getZ());
      boolean blocked = !level.getBlockState(head).getCollisionShape(level, head).isEmpty();
      if (!blocked) {
         Vec3 motion = player.getDeltaMovement();
         player.setDeltaMovement(motion.x, Math.max(motion.y, 0.3), motion.z);
      }

      if (this.lastResort.get() && (blocked || player.getAirSupply() <= 20) && this.ready(Danger.DROWNING, 60)) {
         if (SpCommands.run("effect give @s minecraft:water_breathing 30 0 true")) {
            this.saved("Water breathing");
         }
      }
   }

   private void handleSuffocation(LocalPlayer player, Set<Danger> dangers) {
      if (!player.isInWall()) {
         this.wallTicks = 0;
         return;
      }

      dangers.add(Danger.SUFFOCATION);
      this.wallTicks++;
      if (this.lastResort.get() && this.wallTicks >= 3 && this.ready(Danger.SUFFOCATION, 20)) {
         boolean moved = this.haveSafe && player.level().dimension().identifier().toString().equals(this.safeDimension)
            ? this.teleportSafe(player)
            : SpCommands.run("execute positioned over motion_blocking run tp @s ~ ~ ~");
         if (moved) {
            this.saved("Unstuck");
         }
      }
   }

   private void handleLowHealth(Minecraft client, LocalPlayer player, Set<Danger> dangers) {
      float health = Damage.health(player);
      if (health > this.health.floatValue()) {
         return;
      }

      dangers.add(Danger.LOW_HEALTH);
      if (this.totem.get()
         && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
         && !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
         && Inventories.inventoryOpenOrClosed(player)
         && this.itemCooldown <= 0) {
         int index = Inventories.find(player, s -> s.is(Items.TOTEM_OF_UNDYING), false);
         if (index >= 0) {
            Inventories.swapToOffhand(client, player, index);
            this.itemCooldown = 4;
            this.saved("Totem");
         }
      }

      if (this.lastResort.get() && this.ready(Danger.LOW_HEALTH, 40)) {
         if (SpCommands.run("effect give @s minecraft:instant_health 1 1 true")) {
            SpCommands.run("effect give @s minecraft:regeneration 5 1 true");
            this.saved("Healed");
         }
      }
   }

   // ---------------------------------------------------------------- picking up water

   private void tickScoop(Minecraft client, LocalPlayer player, Level level, boolean canUseItems) {
      if (this.placedWater == null) {
         return;
      }

      if (!this.scoop.get() || this.scoopTicks-- <= 0) {
         this.placedWater = null;
         return;
      }

      FluidState fluid = level.getFluidState(this.placedWater);
      if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
         // Became obsidian or stone on lava, or flowed away; nothing to pick up.
         if (!level.getFluidState(this.placedWater).is(FluidTags.WATER)) {
            this.placedWater = null;
         }

         return;
      }

      if (!canUseItems || this.itemCooldown > 0 || !(player.onGround() || player.isInWater()) || player.isOnFire()) {
         return;
      }

      int bucket = findHotbar(player, s -> s.is(Items.BUCKET));
      if (bucket != NONE && this.useDown(client, player, bucket)) {
         this.itemCooldown = 2;
      }

      this.placedWater = null;
   }

   // ---------------------------------------------------------------- modules on and off

   private void trackSafety(Set<Danger> dangers) {
      if (!dangers.isEmpty()) {
         this.safeTicks = 0;
         Set<String> wanted = new LinkedHashSet<>(this.dangerModules.get());
         for (Danger danger : dangers) {
            switch (danger) {
               case FALL, VOID -> wanted.addAll(this.fallModules.get());
               case LAVA, FIRE -> wanted.addAll(this.lavaModules.get());
               case LOW_HEALTH -> wanted.addAll(this.healthModules.get());
               default -> {
               }
            }
         }

         this.enableModules(wanted);
      } else if (!this.enabledByUs.isEmpty() && this.restore.get() && ++this.safeTicks >= this.restoreDelay.intValue()) {
         this.restoreModules();
      }
   }

   private void enableModules(Set<String> names) {
      Gemini gemini = Gemini.getInstance();
      if (gemini == null || names.isEmpty()) {
         return;
      }

      List<String> turnedOn = new ArrayList<>();
      for (String name : names) {
         gemini.modules().getByName(name).ifPresent(module -> {
            if (module != this && !module.isSavedEnabled() && module.canActivate()) {
               module.setEnabled(true, false);
               if (module.isEnabled()) {
                  this.enabledByUs.add(module.getName());
                  turnedOn.add(module.getName());
               }
            }
         });
      }

      if (!turnedOn.isEmpty() && this.notify.get()) {
         CommandManager.feedback("§bClutch §7switched on §f" + String.join(", ", turnedOn) + "§7.");
      }
   }

   private void restoreModules() {
      Gemini gemini = Gemini.getInstance();
      if (gemini != null) {
         for (String name : this.enabledByUs) {
            gemini.modules().getByName(name).ifPresent(module -> {
               if (module.isSavedEnabled()) {
                  module.setEnabled(false, false);
               }
            });
         }
      }

      this.enabledByUs.clear();
      this.safeTicks = 0;
   }

   // ---------------------------------------------------------------- safe spot tracking

   private void recordSafeSpot(LocalPlayer player, Level level, Set<Danger> dangers) {
      if (!dangers.isEmpty() || !player.onGround() || player.isInWater() || player.isInLava() || player.isOnFire() || player.isInWall()) {
         return;
      }

      BlockPos below = player.blockPosition().below();
      if (level.getBlockState(below).getCollisionShape(level, below).isEmpty() || level.getFluidState(below).is(FluidTags.LAVA)) {
         return;
      }

      this.haveSafe = true;
      this.safeDimension = level.dimension().identifier().toString();
      this.safeX = player.getX();
      this.safeY = player.getY();
      this.safeZ = player.getZ();
   }

   private boolean teleportSafe(LocalPlayer player) {
      if (this.haveSafe && SpCommands.teleport(this.safeDimension, this.safeX, this.safeY + 0.05, this.safeZ)) {
         player.setDeltaMovement(Vec3.ZERO);
         AttributeBoost.resetFall(player);
         return true;
      } else {
         return false;
      }
   }

   // ---------------------------------------------------------------- item use

   private static final int NONE = -1;
   private static final int OFFHAND = 40;

   /** A hotbar slot holding a matching item, {@link #OFFHAND} for the offhand, or {@link #NONE}. */
   private static int findHotbar(LocalPlayer player, Predicate<ItemStack> match) {
      if (match.test(player.getOffhandItem())) {
         return OFFHAND;
      }

      return Inventories.findHotbar(player, match);
   }

   /** Uses the item in {@code slot} while looking straight down (bucket placement and pickup). */
   private boolean useDown(Minecraft client, LocalPlayer player, int slot) {
      if (slot == OFFHAND) {
         if (!Hands.claimOffhand()) {
            return false;
         }

         RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), ActionPriority.CRITICAL_SURVIVAL, 180.0F);
         RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.OFF_HAND));
         return true;
      }

      if (!Hands.claimMainHand()) {
         return false;
      }

      RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), ActionPriority.CRITICAL_SURVIVAL, 180.0F);
      Inventories.withHotbarSlot(player, slot, () -> RotationManager.useWithAim(player, () -> client.gameMode.useItem(player, InteractionHand.MAIN_HAND)));
      return true;
   }

   /** Places the block in {@code slot} on top of {@code ground}. */
   private boolean placeOnTop(Minecraft client, LocalPlayer player, BlockPos ground, double top, int slot) {
      Vec3 hit = new Vec3(ground.getX() + 0.5, top, ground.getZ() + 0.5);
      BlockHitResult result = new BlockHitResult(hit, Direction.UP, ground, false);
      InteractionHand hand = slot == OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      if (slot == OFFHAND ? !Hands.claimOffhand() : !Hands.claimMainHand()) {
         return false;
      }

      RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), ActionPriority.CRITICAL_SURVIVAL, 180.0F);
      int previous = player.getInventory().getSelectedSlot();
      if (slot != OFFHAND) {
         player.getInventory().setSelectedSlot(slot);
      }

      boolean placed = client.gameMode.useItemOn(player, hand, result).consumesAction();
      Swing.interact(player, hand);
      if (slot != OFFHAND) {
         player.getInventory().setSelectedSlot(previous);
      }

      return placed;
   }

   private boolean useFallSave(Minecraft client, LocalPlayer player, Level level, Landing landing, FallSave save) {
      if (save.bucket) {
         if (this.useDown(client, player, save.slot)) {
            if (save.label.equals("Water MLG")) {
               this.placedWater = landing.pos.above();
               this.scoopTicks = 30;
            }

            return true;
         }

         return false;
      }

      BlockPos target = landing.pos.above();
      if (!BlockPlacer.replaceable(level, target)) {
         return false;
      }

      if (save.needsRoom && player.getBoundingBox().intersects(new AABB(target))) {
         return false;
      }

      return this.placeOnTop(client, player, landing.pos, landing.top, save.slot);
   }

   /** The best item to break this fall with, or null. */
   private static FallSave pickFallSave(LocalPlayer player, boolean nether, float predicted, float health) {
      if (!nether) {
         int water = findHotbar(player, s -> s.is(Items.WATER_BUCKET));
         if (water != NONE) {
            return new FallSave(water, true, false, "Water MLG");
         }
      }

      int web = findHotbar(player, s -> s.is(Items.COBWEB));
      if (web != NONE) {
         return new FallSave(web, false, false, "Cobweb MLG");
      }

      int vines = findHotbar(player, s -> s.is(Items.TWISTING_VINES));
      if (vines != NONE) {
         return new FallSave(vines, false, false, "Vine MLG");
      }

      int snow = findHotbar(player, s -> s.is(Items.POWDER_SNOW_BUCKET));
      if (snow != NONE) {
         return new FallSave(snow, false, true, "Snow MLG");
      }

      if (!player.isShiftKeyDown()) {
         int slime = findHotbar(player, s -> s.is(Items.SLIME_BLOCK));
         if (slime != NONE) {
            return new FallSave(slime, false, true, "Slime MLG");
         }
      }

      int scaffold = findHotbar(player, s -> s.is(Items.SCAFFOLDING));
      if (scaffold != NONE) {
         return new FallSave(scaffold, false, true, "Scaffold MLG");
      }

      // Hay and honey only cut fall damage to a fifth, so they only count when that is survivable.
      if (Math.ceil(predicted * 0.2F) < health) {
         int soft = findHotbar(player, s -> s.is(Items.HAY_BLOCK) || s.is(Items.HONEY_BLOCK));
         if (soft != NONE) {
            return new FallSave(soft, false, true, "Hay MLG");
         }
      }

      return null;
   }

   // ---------------------------------------------------------------- world checks

   /** The first thing you would land in or on straight below you, within {@link #SCAN_DEPTH} blocks. */
   private static Landing scanBelow(Level level, LocalPlayer player) {
      BlockPos feet = player.blockPosition();
      int minY = VersionBridge.minWorldY(level);
      for (int dy = 0; dy <= SCAN_DEPTH; dy++) {
         BlockPos pos = feet.below(dy);
         if (pos.getY() < minY) {
            return null;
         }

         FluidState fluid = level.getFluidState(pos);
         if (fluid.is(FluidTags.WATER)) {
            double top = pos.getY() + fluid.getHeight(level, pos);
            if (top <= player.getY() + 0.01) {
               return new Landing(pos, top, LandingKind.SAFE, 0.0F);
            }
         } else if (fluid.is(FluidTags.LAVA)) {
            double top = pos.getY() + fluid.getHeight(level, pos);
            if (top <= player.getY() + 0.01) {
               return new Landing(pos, top, LandingKind.LAVA, 1.0F);
            }
         }

         BlockState state = level.getBlockState(pos);
         if (stopsFall(state)) {
            return new Landing(pos, pos.getY(), LandingKind.SAFE, 0.0F);
         }

         VoxelShape shape = state.getCollisionShape(level, pos);
         if (!shape.isEmpty()) {
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            if (top <= player.getY() + 0.01) {
               return new Landing(pos, top, LandingKind.SOLID, landingMultiplier(state, player));
            }
         }
      }

      return null;
   }

   /** Blocks that reset your fall when you drop into them. */
   private static boolean stopsFall(BlockState state) {
      return state.is(Blocks.COBWEB)
         || state.is(Blocks.POWDER_SNOW)
         || state.is(Blocks.SWEET_BERRY_BUSH)
         || state.is(BlockTags.CLIMBABLE);
   }

   private static float landingMultiplier(BlockState state, LocalPlayer player) {
      if (state.is(Blocks.SLIME_BLOCK)) {
         return player.isShiftKeyDown() ? 1.0F : 0.0F;
      } else if (state.is(Blocks.HAY_BLOCK) || state.is(Blocks.HONEY_BLOCK)) {
         return 0.2F;
      } else if (state.is(BlockTags.BEDS)) {
         return 0.5F;
      } else if (state.is(Blocks.POINTED_DRIPSTONE)) {
         return 2.0F;
      } else {
         return 1.0F;
      }
   }

   /**
    * Fall damage vanilla would deal for {@code distance} blocks onto a block with {@code multiplier}. Feather
    * Falling and Protection are ignored, so this errs on the side of clutching.
    */
   private static float predictFallDamage(LocalPlayer player, double distance, float multiplier) {
      if (multiplier <= 0.0F) {
         return 0.0F;
      }

      double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
      double scale = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
      double raw = Math.ceil((distance - safe) * scale * multiplier);
      if (raw <= 0.0) {
         return 0.0F;
      }

      float damage = (float)raw;
      MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
      if (resistance != null) {
         damage *= Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
      }

      return damage;
   }

   /** Whether walking on (or stepping off) the next couple of blocks would put you in lava. */
   private static boolean lavaAhead(Level level, LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
      if (speed < 0.01) {
         return false;
      }

      for (double step = 0.6; step <= 1.6; step += 0.5) {
         double x = player.getX() + motion.x / speed * step;
         double z = player.getZ() + motion.z / speed * step;
         BlockPos feet = BlockPos.containing(x, player.getY() + 0.1, z);
         if (level.getFluidState(feet).is(FluidTags.LAVA)) {
            return true;
         }

         // Stepping off an edge: look down through the air for lava within a short drop.
         if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            for (int dy = 1; dy <= 4; dy++) {
               BlockPos below = feet.below(dy);
               if (level.getFluidState(below).is(FluidTags.LAVA)) {
                  return true;
               }

               if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() || !level.getFluidState(below).isEmpty()) {
                  break;
               }
            }
         }
      }

      return false;
   }

   private static boolean fireResistant(LocalPlayer player) {
      return player.hasEffect(MobEffects.FIRE_RESISTANCE);
   }

   // ---------------------------------------------------------------- bookkeeping

   /** Whether the last-resort action for {@code danger} is off cooldown; starts the cooldown if it is. */
   private boolean ready(Danger danger, int cooldown) {
      if (this.commandCooldown.getOrDefault(danger, 0) > 0) {
         return false;
      }

      this.commandCooldown.put(danger, cooldown);
      return true;
   }

   private void saved(String how) {
      this.saves++;
      this.lastSave = how;
      if (this.notify.get()) {
         CommandManager.feedback("§bClutch §7» §f" + how);
      }
   }

   private enum Danger {
      FALL,
      LAVA,
      FIRE,
      VOID,
      DROWNING,
      SUFFOCATION,
      LOW_HEALTH;
   }

   private enum LandingKind {
      SOLID,
      SAFE,
      LAVA;
   }

   private record Landing(BlockPos pos, double top, LandingKind kind, float multiplier) {
   }

   /** {@code needsRoom}: the placed block has a collision box, so it can't go where you already are. */
   private record FallSave(int slot, boolean bucket, boolean needsRoom, String label) {
   }
}
