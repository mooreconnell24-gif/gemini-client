package gemini.module.modules.movement;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.Holes;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.Targets;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Items;
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

public final class MovementModules {
   private MovementModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new MovementModules.ElytraFly());
      sink.accept(new MovementModules.Sneak());
      sink.accept(new MovementModules.Slippy());
      sink.accept(new MovementModules.AutoJump());
      sink.accept(new MovementModules.EntitySpeed());
      sink.accept(new MovementModules.BoatFly());
      sink.accept(new MovementModules.ClickTp());
      sink.accept(new MovementModules.Glide());
      sink.accept(new MovementModules.TridentBoost());
      sink.accept(new MovementModules.AutoFirework());
      sink.accept(new MovementModules.FastFall());
      sink.accept(new MovementModules.TargetStrafe());
      sink.accept(new MovementModules.Strafe());
      sink.accept(new MovementModules.Freeze());
      sink.accept(new MovementModules.NoWeb());
      sink.accept(new MovementModules.AntiLevitation());
      sink.accept(new MovementModules.TerrainSpeed());
      sink.accept(new MovementModules.HoleSnap());
      sink.accept(new MovementModules.TimerRange());
      sink.accept(new MovementModules.KeepY());
      sink.accept(new MovementModules.FastSwim());
      sink.accept(new MovementModules.EdgeJump());
      sink.accept(new MovementModules.AnchorPull());
      sink.accept(new MovementModules.WallClimb());
   }

   private static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null && client.level != null && client.gameMode != null && !player.isSpectator() && !player.isDeadOrDying();
   }

   private static boolean airborne(LocalPlayer player) {
      return !player.onGround()
         && !player.getAbilities().flying
         && !player.isFallFlying()
         && !player.isInWater()
         && !player.isInLava()
         && !player.onClimbable()
         && !player.isPassenger();
   }

   private static boolean passable(Level level, BlockPos pos) {
      return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   private static double groundDistance(Level level, LocalPlayer player, int scan) {
      for (int i = 0; i <= scan; i++) {
         BlockPos below = BlockPos.containing(player.getX(), player.getY() - i - 0.01, player.getZ());
         if (!passable(level, below)) {
            return Math.max(0.0, player.getY() - (below.getY() + 1.0));
         }
      }

      return -1.0;
   }

   public static final class AnchorPull extends Module {
      private final NumberSetting maxDrop = this.addSetting(new NumberSetting("Max Drop", "Maximum vertical distance to pull into a hole", 4.0, 1.5, 6.0, 0.5));
      private final NumberSetting pullSpeed = this.addSetting(new NumberSetting("Pull Speed", "Downward pull velocity", 0.38, 0.15, 0.8, 0.02));

      public AnchorPull() {
         super("AnchorPull", "Cancel horizontal air drift and pull straight down when passing over a safe hole.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client)) {
            LocalPlayer player = client.player;
            BlockPos pos = player.blockPosition();

            for (int d = 1; d <= this.maxDrop.intValue(); d++) {
               BlockPos below = pos.below(d);
               if (Holes.isHole(client.level, below)) {
                  double cx = below.getX() + 0.5 - player.getX();
                  double cz = below.getZ() + 0.5 - player.getZ();
                  player.setDeltaMovement(cx * 0.25, -this.pullSpeed.get(), cz * 0.25);
                  return;
               }

               if (!MovementModules.passable(client.level, below)) {
                  break;
               }
            }
         }
      }
   }

   public static final class AntiLevitation extends Module {
      public AntiLevitation() {
         super("AntiLevitation", "Neutralize upward drift from the Shulker Levitation effect.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.hasEffect(MobEffects.LEVITATION)) {
            Vec3 m = player.getDeltaMovement();
            if (m.y > 0.0) {
               player.setDeltaMovement(m.x, 0.0, m.z);
            }
         }
      }
   }

   public static final class AutoFirework extends Module {
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Minimum ticks between rockets", 50.0, 10.0, 200.0, 5.0));
      private final BooleanSetting onlySlow = this.addSetting(new BooleanSetting("Only When Slow", "Skip rockets while you are already fast", true));
      private final NumberSetting minSpeed = this.addSetting(
         new NumberSetting("Min Speed", "Fire when slower than this (blocks per tick)", 1.0, 0.2, 3.0, 0.1).showIf(this.onlySlow::get)
      );
      private int cooldown;

      public AutoFirework() {
         super("Auto Firework", "Use firework rockets automatically while gliding.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && player.isFallFlying()) {
            if (this.cooldown > 0) {
               this.cooldown--;
            } else if (!this.onlySlow.get() || !(player.getDeltaMovement().length() >= this.minSpeed.get())) {
               if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
                  client.gameMode.useItem(player, InteractionHand.OFF_HAND);
               } else {
                  int slot = Inventories.findHotbar(player, stack -> stack.is(Items.FIREWORK_ROCKET));
                  if (slot < 0) {
                     return;
                  }

                  int previous = player.getInventory().getSelectedSlot();
                  player.getInventory().setSelectedSlot(slot);
                  client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                  player.getInventory().setSelectedSlot(previous);
               }

               this.cooldown = this.delay.intValue();
            }
         }
      }
   }

   public static final class AutoJump extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Always = bunny hop while moving, Obstacle = hop over blocks in your way", "Obstacle", "Always", "Obstacle")
      );

      public AutoJump() {
         super("Auto Jump", "Jump automatically while walking.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client)
            && player.onGround()
            && !player.isShiftKeyDown()
            && !player.isInWater()
            && !player.isPassenger()
            && MoveUtil.hasInput(player)
            && (this.mode.is("Always") || player.horizontalCollision)) {
            player.jumpFromGround();
         }
      }
   }

   public static final class BoatFly extends Module {
      private final NumberSetting horizontal = this.addSetting(new NumberSetting("Horizontal Speed", "Speed in blocks per tick", 1.0, 0.1, 4.0, 0.1));
      private final NumberSetting vertical = this.addSetting(
         new NumberSetting("Vertical Speed", "Climb and descent speed with Jump and Sneak", 0.5, 0.1, 2.0, 0.1)
      );
      private final BooleanSetting antiKick = this.addSetting(
         new BooleanSetting("Anti Kick", "Sink a little now and then so servers don't kick for flying", true)
      );
      private AbstractBoat flying;
      private int hoverTicks;

      public BoatFly() {
         super("Boat Fly", "Fly while sitting in a boat.", Category.MOVEMENT);
      }

      @Override
      public void onDisable() {
         if (this.flying != null) {
            this.flying.setNoGravity(false);
            this.flying = null;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && player.input != null) {
            if (player.getVehicle() instanceof AbstractBoat boat) {
               if (this.flying != boat) {
                  this.onDisable();
                  this.flying = boat;
               }

               boat.setNoGravity(true);
               Input keys = player.input.keyPresses;
               double y = 0.0;
               if (keys.jump()) {
                  y += this.vertical.get();
               }

               if (keys.shift()) {
                  y -= this.vertical.get();
               }

               if (y == 0.0 && this.antiKick.get() && ++this.hoverTicks % 30 == 0) {
                  y = -0.04;
               }

               Vec3 direction = MoveUtil.direction(player);
               double x = direction == null ? 0.0 : direction.x * this.horizontal.get();
               double z = direction == null ? 0.0 : direction.z * this.horizontal.get();
               boat.setDeltaMovement(x, y, z);
            } else {
               this.onDisable();
            }
         }
      }
   }

   public static final class ClickTp extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block you can teleport to", 60.0, 5.0, 200.0, 5.0));
      private final ModeSetting trigger = this.addSetting(
         new ModeSetting("Trigger", "What starts the teleport", "Middle Click", "Middle Click", "Sneak + Right Click")
      );
      private final BooleanSetting steps = this.addSetting(
         new BooleanSetting("Step Packets", "Send the route in short hops so servers accept long jumps", true)
      );
      private boolean previous;

      public ClickTp() {
         super("Click TP", "Teleport to the block you are looking at.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && client.gui.screen() == null) {
            boolean pressed = this.trigger.is("Middle Click")
               ? client.mouseHandler.isMiddlePressed()
               : client.options.keyUse.isDown() && player.isShiftKeyDown();
            if (pressed && !this.previous) {
               this.teleport(client, player);
            }

            this.previous = pressed;
         } else {
            this.previous = false;
         }
      }

      private void teleport(Minecraft client, LocalPlayer player) {
         Vec3 from = player.getEyePosition();
         Vec3 to = from.add(player.getLookAngle().scale(this.range.get()));
         BlockHitResult hit = client.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, player));
         if (hit.getType() == Type.MISS) {
            CommandManager.feedback("§bClick TP §7no block in range.");
         } else {
            BlockPos above = hit.getBlockPos().relative(hit.getDirection());
            Vec3 destination = null;

            for (int lift = 0; lift < 3 && destination == null; lift++) {
               double y = hit.getDirection() == Direction.UP ? hit.getBlockPos().getY() + 1.0 + lift : above.getY() + lift;
               Vec3 candidate = new Vec3(above.getX() + 0.5, y, above.getZ() + 0.5);
               Vec3 delta = candidate.subtract(player.position());
               if (client.level.noCollision(player, player.getBoundingBox().move(delta))) {
                  destination = candidate;
               }
            }

            if (destination == null) {
               CommandManager.feedback("§bClick TP §7there is no room to stand there.");
            } else {
               MoveUtil.teleport(client, player, destination, this.steps.get());
            }
         }
      }
   }

   public static final class EdgeJump extends Module {
      private final NumberSetting edgeOffset = this.addSetting(
         new NumberSetting("Edge Threshold", "How close to the block edge before jumping", 0.18, 0.05, 0.4, 0.02)
      );

      public EdgeJump() {
         super("EdgeJump", "Automatically jump at the exact outer edge of blocks when running off ledges.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && client.player.onGround() && !client.player.isShiftKeyDown() && MoveUtil.hasInput(client.player)) {
            LocalPlayer player = client.player;
            Vec3 motion = player.getDeltaMovement();
            AABB nextBox = player.getBoundingBox().move(motion.x, -0.5, motion.z).deflate(this.edgeOffset.get(), 0.0, this.edgeOffset.get());
            if (client.level.noCollision(player, nextBox)) {
               player.jumpFromGround();
            }
         }
      }
   }

   public static final class ElytraFly extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Control = fly where you look at a fixed speed, Boost = accelerate forwards", "Control", "Control", "Boost")
      );
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Flight speed in blocks per tick", 1.2, 0.2, 5.0, 0.1));
      private final NumberSetting vertical = this.addSetting(
         new NumberSetting("Vertical Speed", "Climb and descent speed with Jump and Sneak", 0.5, 0.1, 2.0, 0.1).showIf(() -> this.mode.is("Control"))
      );
      private final BooleanSetting hover = this.addSetting(
         new BooleanSetting("Hover", "Hang still in the air when no movement key is held", true).showIf(() -> this.mode.is("Control"))
      );
      private final BooleanSetting takeoff = this.addSetting(new BooleanSetting("Auto Takeoff", "Open the elytra when you press Jump in mid-air", true));

      public ElytraFly() {
         super("Elytra Fly", "Fly with an elytra with full control.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && player.input != null) {
            Input keys = player.input.keyPresses;
            if (!player.isFallFlying()) {
               ClientPacketListener connection = client.getConnection();
               if (this.takeoff.get()
                  && connection != null
                  && keys.jump()
                  && !player.onGround()
                  && !player.isInWater()
                  && !player.isPassenger()
                  && player.getDeltaMovement().y < 0.0
                  && player.tryToStartFallFlying()) {
                  connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
               }
            } else {
               Vec3 look = player.getLookAngle();
               Vec3 motion = player.getDeltaMovement();
               if (this.mode.is("Boost")) {
                  if (keys.forward()) {
                     Vec3 boosted = motion.add(look.scale(0.1));
                     double length = boosted.length();
                     player.setDeltaMovement(length > this.speed.get() ? boosted.scale(this.speed.get() / length) : boosted);
                  }
               } else {
                  Vec3 target;
                  if (keys.forward()) {
                     target = look.scale(this.speed.get());
                  } else if (keys.backward()) {
                     target = look.scale(-this.speed.get());
                  } else if (this.hover.get()) {
                     target = Vec3.ZERO;
                  } else {
                     target = motion;
                  }

                  double y = target.y;
                  if (keys.jump()) {
                     y += this.vertical.get();
                  }

                  if (keys.shift()) {
                     y -= this.vertical.get();
                  }

                  player.setDeltaMovement(target.x, y, target.z);
               }
            }
         }
      }
   }

   public static final class EntitySpeed extends Module {
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Mount speed in blocks per tick", 0.6, 0.1, 3.0, 0.05));
      private final BooleanSetting onlyGround = this.addSetting(new BooleanSetting("Only On Ground", "Leave airborne mounts alone", false));

      public EntitySpeed() {
         super("Entity Speed", "Speed up the vehicle or animal you are riding.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return String.format("%.2f", this.speed.get());
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client)) {
            Entity vehicle = player.getVehicle();
            Vec3 direction = MoveUtil.direction(player);
            if (vehicle != null && direction != null && (!this.onlyGround.get() || vehicle.onGround())) {
               vehicle.setDeltaMovement(direction.x * this.speed.get(), vehicle.getDeltaMovement().y, direction.z * this.speed.get());
            }
         }
      }
   }

   public static final class FastFall extends Module {
      private final NumberSetting speed = this.addSetting(new NumberSetting("Fall Speed", "Downward speed in blocks per tick", 1.0, 0.3, 4.0, 0.1));
      private final BooleanSetting onlySafe = this.addSetting(
         new BooleanSetting("Only When Safe", "Only speed up when the whole fall stays under three blocks", true)
      );

      public FastFall() {
         super("Fast Fall", "Drop to the ground faster.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && MovementModules.airborne(player)) {
            Vec3 motion = player.getDeltaMovement();
            if (!(motion.y > -0.2)) {
               double ground = MovementModules.groundDistance(client.level, player, 12);
               if (!(ground < 0.0) && (!this.onlySafe.get() || !(player.fallDistance + ground > 3.0))) {
                  double fall = Math.min(this.speed.get(), Math.max(0.1, ground - 0.05));
                  player.setDeltaMovement(motion.x, -fall, motion.z);
               }
            }
         }
      }
   }

   public static final class FastSwim extends Module {
      private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Water acceleration mode", "Dolphin", "Dolphin", "Stream", "Pulse"));
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Horizontal swim multiplier", 1.55, 1.1, 3.0, 0.05));
      private int tick;

      public FastSwim() {
         super("FastSwim", "Accelerate underwater and surface swimming speed like LiquidBounce FastSwim.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && (client.player.isInWater() || client.player.isInLava()) && MoveUtil.hasInput(client.player)) {
            LocalPlayer player = client.player;
            this.tick++;
            double mult = this.mode.is("Pulse") && this.tick % 4 != 0 ? 1.05 : this.speed.get();
            MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player) * 0.72 * mult);
         }
      }
   }

   public static final class Freeze extends Module {
      public Freeze() {
         super("Freeze", "Freeze your character mid-air and cancel outgoing movement packets.", Category.MOVEMENT);
      }

      @Override
      public boolean selfTestExempt() {
         return true;
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            player.setDeltaMovement(Vec3.ZERO);
         }
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         return packet instanceof ServerboundMovePlayerPacket;
      }
   }

   public static final class Glide extends Module {
      private final NumberSetting fall = this.addSetting(new NumberSetting("Fall Speed", "Fastest you may fall, in blocks per tick", 0.12, 0.02, 0.5, 0.01));
      private final NumberSetting boost = this.addSetting(new NumberSetting("Speed", "Horizontal speed multiplier while gliding", 1.0, 1.0, 2.0, 0.05));
      private final BooleanSetting noFall = this.addSetting(new BooleanSetting("No Fall Damage", "Reset fall distance while gliding", true));

      public Glide() {
         super("Glide", "Fall slowly, like a feather.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (MovementModules.ready(client) && MovementModules.airborne(player)) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < -this.fall.get()) {
               player.setDeltaMovement(motion.x, -this.fall.get(), motion.z);
            }

            if (this.boost.get() > 1.0) {
               MoveUtil.setHorizontal(player, 0.2806 * this.boost.get());
            }

            if (this.noFall.get()) {
               player.resetFallDistance();
            }
         }
      }
   }

   public static final class HoleSnap extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max distance to pull into a safe hole", 4.0, 1.5, 6.0, 0.5));

      public HoleSnap() {
         super("HoleSnap", "Pull your character smoothly into the nearest Bedrock or Obsidian hole.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client)) {
            LocalPlayer player = client.player;
            if (!Holes.isHole(client.level, player.blockPosition())) {
               List<BlockPos> found = Holes.around(client.level, player.blockPosition(), this.range.intValue(), 2);
               if (!found.isEmpty()) {
                  BlockPos hole = found.getFirst();
                  Vec3 center = new Vec3(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5);
                  Vec3 pull = center.subtract(player.position()).normalize().scale(0.24);
                  player.setDeltaMovement(pull.x, Math.min(player.getDeltaMovement().y, -0.15), pull.z);
               }
            }
         }
      }
   }

   public static final class KeepY extends Module {
      private final BooleanSetting boost = this.addSetting(new BooleanSetting("Air Strafe", "Maintain sprint air speed while jumping", true));
      private int lockedY;

      public KeepY() {
         super("KeepY", "Automatically jump on touch-down to keep your vertical floor locked for Telly bridging and PvP.", Category.MOVEMENT);
      }

      @Override
      public void onEnable() {
         LocalPlayer player = Minecraft.getInstance().player;
         this.lockedY = player == null ? 0 : player.getBlockY();
      }

      @Override
      public String info() {
         return "Y=" + this.lockedY;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && MoveUtil.hasInput(client.player)) {
            LocalPlayer player = client.player;
            if (player.onGround()) {
               this.lockedY = player.getBlockY();
               player.jumpFromGround();
            } else if (this.boost.get()) {
               MoveUtil.setHorizontal(player, Math.max(MoveUtil.horizontalSpeed(player), MoveUtil.baseSpeed(player)));
            }
         }
      }
   }

   public static final class NoWeb extends Module {
      public NoWeb() {
         super("NoWeb", "Prevent cobwebs and sweet berry bushes from slowing down your movement.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client)) {
            LocalPlayer player = client.player;
            BlockState feet = client.level.getBlockState(player.blockPosition());
            if (feet.is(Blocks.COBWEB) || feet.is(Blocks.SWEET_BERRY_BUSH)) {
               MoveUtil.setHorizontal(player, MoveUtil.baseSpeed(player));
            }
         }
      }
   }

   public static final class Slippy extends Module {
      public static MovementModules.Slippy instance;
      private final NumberSetting friction = this.addSetting(
         new NumberSetting("Friction", "Block friction (0.6 = normal, 0.98 = ice, 1.0 = no friction)", 0.98, 0.6, 1.0, 0.01)
      );
      private final ListSetting ignored = this.addSetting(new ListSetting("Ignored Blocks", "Blocks that keep their normal friction", ListSetting.Kind.BLOCK));

      public Slippy() {
         super("Slippy", "Make every block as slippery as ice.", Category.MOVEMENT);
         instance = this;
      }

      @Override
      public String info() {
         return String.format("%.2f", this.friction.get());
      }

      public float override(net.minecraft.world.level.block.Block block, float original) {
         return this.ignored.blocks().contains(block) ? original : this.friction.floatValue();
      }
   }

   public static final class Sneak extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Vanilla = really crouch, Packet = only the server sees you crouching", "Vanilla", "Vanilla", "Packet")
      );
      private final BooleanSetting onlyMoving = this.addSetting(
         new BooleanSetting("Only Moving", "Sneak only while you are walking", false).showIf(() -> this.mode.is("Vanilla"))
      );

      public Sneak() {
         super("Sneak", "Stay crouched without holding the key.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onEnable() {
         if (this.mode.is("Packet")) {
            sendInput(new Input(false, false, false, false, false, true, false));
         }
      }

      @Override
      public void onDisable() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null) {
            client.options.keyShift.setDown(MoveUtil.physicalKeyDown(client.options.keyShift));
            if (this.mode.is("Packet")) {
               sendInput(client.player.input == null ? Input.EMPTY : client.player.input.keyPresses);
            }
         }
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (this.mode.is("Packet") && packet instanceof ServerboundPlayerInputPacket input && !input.input().shift()) {
            Input base = input.input();
            sendInput(new Input(base.forward(), base.backward(), base.left(), base.right(), base.jump(), true, base.sprint()));
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.player != null && this.mode.is("Vanilla") && client.gui.screen() == null) {
            boolean sneak = !this.onlyMoving.get() || MoveUtil.hasInput(client.player);
            client.options.keyShift.setDown(sneak || MoveUtil.physicalKeyDown(client.options.keyShift));
         }
      }

      private static void sendInput(Input input) {
         ClientPacketListener connection = Minecraft.getInstance().getConnection();
         if (connection != null) {
            connection.send(new ServerboundPlayerInputPacket(input));
         }
      }
   }

   public static final class Strafe extends Module {
      private final NumberSetting strength = this.addSetting(new NumberSetting("Strength", "Air strafe responsiveness", 1.0, 0.2, 1.5, 0.05));

      public Strafe() {
         super("Strafe", "Maintain full directional air control while jumping.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && !client.player.onGround() && MoveUtil.hasInput(client.player)) {
            double currentSpeed = Math.max(MoveUtil.horizontalSpeed(client.player), MoveUtil.baseSpeed(client.player) * this.strength.get());
            MoveUtil.setHorizontal(client.player, currentSpeed);
         }
      }
   }

   public static final class TargetStrafe extends Module {
      private final NumberSetting radius = this.addSetting(new NumberSetting("Radius", "Orbit distance around target", 2.6, 1.0, 5.0, 0.2));
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Orbit speed multiplier", 1.05, 0.5, 2.0, 0.05));
      private final BooleanSetting holdJump = this.addSetting(new BooleanSetting("Require Jump", "Only orbit while holding Jump", true));
      private final BooleanSetting renderRing = this.addSetting(new BooleanSetting("Render Ring", "Draw the 3D orbit ring around the target", true));
      private int dir = 1;
      private LivingEntity activeTarget;

      public TargetStrafe() {
         super("TargetStrafe", "Orbit smoothly around nearby combat targets like LiquidBounce TargetStrafe.", Category.MOVEMENT);
      }

      @Override
      public void onDisable() {
         this.activeTarget = null;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (!MovementModules.ready(client)) {
            this.activeTarget = null;
         } else {
            LocalPlayer player = client.player;
            this.activeTarget = findNearestTarget(client, player, 6.0);
            if (this.activeTarget != null && (!this.holdJump.get() || client.options.keyJump.isDown())) {
               if (player.horizontalCollision) {
                  this.dir = -this.dir;
               }

               double angle = Math.atan2(player.getZ() - this.activeTarget.getZ(), player.getX() - this.activeTarget.getX()) + this.dir * 0.26;
               double r = this.radius.get();
               double tx = this.activeTarget.getX() + Math.cos(angle) * r;
               double tz = this.activeTarget.getZ() + Math.sin(angle) * r;
               Vec3 delta = new Vec3(tx - player.getX(), 0.0, tz - player.getZ()).normalize().scale(MoveUtil.baseSpeed(player) * this.speed.get());
               player.setDeltaMovement(delta.x, player.getDeltaMovement().y, delta.z);
            }
         }
      }

      private static LivingEntity findNearestTarget(Minecraft client, LocalPlayer self, double range) {
         LivingEntity best = null;
         double bestD = range * range;

         for (Entity e : client.level.entitiesForRendering()) {
            if (Targets.isCandidate(self, e, true, true, false, true)) {
               double d = self.distanceToSqr(e);
               if (d < bestD) {
                  bestD = d;
                  best = (LivingEntity)e;
               }
            }
         }

         return best;
      }

      @Override
      public void onRender3D(float partialTick) {
         if (this.renderRing.get() && this.activeTarget != null && this.activeTarget.isAlive()) {
            Vec3 c = this.activeTarget.getPosition(partialTick);
            double r = this.radius.get();
            int segs = 24;

            for (int i = 0; i < segs; i++) {
               double a1 = i * Math.PI * 2.0 / segs;
               double a2 = (i + 1) * Math.PI * 2.0 / segs;
               Render3D.line(
                  new Vec3(c.x + Math.cos(a1) * r, c.y + 0.05, c.z + Math.sin(a1) * r),
                  new Vec3(c.x + Math.cos(a2) * r, c.y + 0.05, c.z + Math.sin(a2) * r),
                  -16722689,
                  2.0F,
                  false
               );
            }
         }
      }
   }

   public static final class TerrainSpeed extends Module {
      private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Speed multiplier on ice, soul sand, and stairs", 1.35, 1.05, 2.5, 0.05));

      public TerrainSpeed() {
         super("TerrainSpeed", "Accelerate movement over ice, soul sand, snow, and stairs.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && client.player.onGround() && MoveUtil.hasInput(client.player)) {
            BlockState below = client.level.getBlockState(client.player.blockPosition().below());
            if (below.is(BlockTags.ICE) || below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL) || below.is(BlockTags.STAIRS)) {
               MoveUtil.setHorizontal(client.player, MoveUtil.baseSpeed(client.player) * this.boost.get());
            }
         }
      }
   }

   public static final class TimerRange extends Module {
      private final NumberSetting boost = this.addSetting(
         new NumberSetting("Burst Speed", "Horizontal burst multiplier when closing gap", 1.45, 1.1, 2.2, 0.05)
      );
      private int cooldown;

      public TimerRange() {
         super("TimerRange", "Burst movement speed briefly when closing distance to a combat target.", Category.MOVEMENT);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (MovementModules.ready(client) && client.player.onGround() && MoveUtil.hasInput(client.player)) {
            LivingEntity target = MovementModules.TargetStrafe.findNearestTarget(client, client.player, 6.0);
            if (target != null && client.player.distanceTo(target) > 3.1) {
               MoveUtil.setHorizontal(client.player, MoveUtil.baseSpeed(client.player) * this.boost.get());
               this.cooldown = 14;
            }
         }
      }
   }

   public static final class TridentBoost extends Module {
      private final NumberSetting boost = this.addSetting(new NumberSetting("Boost", "Multiplier on riptide launch speed", 2.0, 1.0, 6.0, 0.25));
      private boolean spinning;

      public TridentBoost() {
         super("Trident Boost", "Launch much farther with a riptide trident.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return String.format("%.2fx", this.boost.get());
      }

      @Override
      public void onTick() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            boolean now = player.isAutoSpinAttack();
            if (now && !this.spinning) {
               player.setDeltaMovement(player.getDeltaMovement().scale(this.boost.get()));
            }

            this.spinning = now;
         }
      }
   }

   public static final class WallClimb extends Module {
      private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Climbing physics mode", "Motion", "Motion", "Pulse", "LedgeStep"));
      private final NumberSetting speed = this.addSetting(new NumberSetting("Climb Speed", "Upward velocity against walls", 0.26, 0.1, 0.5, 0.02));
      private int tick;

      public WallClimb() {
         super("WallClimb", "Climb up solid walls with Motion, Pulse, or LedgeStep modes.", Category.MOVEMENT);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (MovementModules.ready(client) && client.player.horizontalCollision && MoveUtil.hasInput(client.player)) {
            LocalPlayer player = client.player;
            this.tick++;
            Vec3 m = player.getDeltaMovement();
            if (this.mode.is("Pulse")) {
               if (this.tick % 4 == 0) {
                  player.setDeltaMovement(m.x, 0.42, m.z);
               }
            } else {
               player.setDeltaMovement(m.x, this.speed.get(), m.z);
            }
         }
      }
   }
}
