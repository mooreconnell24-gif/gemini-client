package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.PlaceSettings;
import gemini.util.RotationManager;
import gemini.util.Targets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldExtras {
   private WorldExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new WorldExtras.ChestAura());
      sink.accept(new WorldExtras.Flamethrower());
      sink.accept(new WorldExtras.AutoSign());
      sink.accept(new WorldExtras.Collisions());
      sink.accept(new WorldExtras.AutoTorch());
      sink.accept(new WorldExtras.Fucker());
      sink.accept(new WorldExtras.BedDefender());
      sink.accept(new WorldExtras.BlockIn());
      sink.accept(new WorldExtras.Extinguish());
      sink.accept(new WorldExtras.PacketMine());
      sink.accept(new WorldExtras.StrongholdFinder());
      sink.accept(new WorldExtras.LawnMower());
   }

   static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && client.level != null
         && client.gameMode != null
         && client.getConnection() != null
         && !player.isSpectator()
         && !player.isDeadOrDying();
   }

   public static final class AutoSign extends Module {
      private final ListSetting lines = this.addSetting(
         new ListSetting("Lines", "Sign text, one entry per line (first four are used)", ListSetting.Kind.TEXT, "Gemini", "was here")
      );
      private final BooleanSetting backToo = this.addSetting(new BooleanSetting("Back Side", "Also write on the back of the sign", false));
      private final BooleanSetting closeScreen = this.addSetting(new BooleanSetting("Skip Editor", "Close the sign editor instantly", true));

      public AutoSign() {
         super("Auto Sign", "Fill in every sign you place with preset text.", Category.WORLD);
      }

      @Override
      public boolean onSendPacket(Packet<?> packet) {
         if (packet instanceof ServerboundSignUpdatePacket sign) {
            if (!sign.isFrontText() && !this.backToo.get()) {
               return false;
            } else {
               Minecraft client = Minecraft.getInstance();
               if (client.getConnection() == null) {
                  return false;
               } else {
                  client.getConnection()
                     .send(new ServerboundSignUpdatePacket(sign.getPos(), sign.isFrontText(), this.line(0), this.line(1), this.line(2), this.line(3)));
                  return true;
               }
            }
         } else {
            return false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.closeScreen.get() && client.gui.screen() instanceof AbstractSignEditScreen screen) {
            screen.onClose();
         }
      }

      private String line(int index) {
         List<String> all = this.lines.get();
         String value = index < all.size() ? all.get(index) : "";
         return value == null ? "" : (value.length() > 15 ? value.substring(0, 15) : value);
      }
   }

   public static final class AutoTorch extends Module {
      private final NumberSetting lightLevel = this.addSetting(
         new NumberSetting("Light Level", "Place a torch when block light is at or below this", 0.0, 0.0, 14.0, 1.0)
      );
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks around you to check", 3.0, 1.0, 5.0, 1.0));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between torches", 5.0, 1.0, 40.0, 1.0));
      private final ModeSetting kind = this.addSetting(new ModeSetting("Torch", "Which torch to use", "Any", "Any", "Torch", "Soul Torch"));
      private final ListSetting avoid = this.addSetting(
         new ListSetting("Avoid", "Blocks never to place torches on", ListSetting.Kind.BLOCK, "farmland", "ice", "packed_ice", "blue_ice")
      );
      private final BooleanSetting onlyWhenStill = this.addSetting(new BooleanSetting("Only When Still", "Do not place while you are moving", false));
      private int wait;

      public AutoTorch() {
         super("Auto Torch", "Light up dark floors around you with torches.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            Level level = client.level;
            if (!this.onlyWhenStill.get() || !MoveUtil.hasInput(player)) {
               int r = this.range.intValue();
               BlockPos center = player.blockPosition();
               BlockPos best = null;
               int darkest = Integer.MAX_VALUE;

               for (BlockPos cursor : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
                  BlockState state = level.getBlockState(cursor);
                  BlockPos below = cursor.below();
                  BlockState floor = level.getBlockState(below);
                  if (state.isAir()
                     && floor.isFaceSturdy(level, below, Direction.UP)
                     && !this.avoid.blocks().contains(floor.getBlock())
                     && !cursor.equals(center)) {
                     int light = level.getBrightness(LightLayer.BLOCK, cursor);
                     if (light <= this.lightLevel.intValue() && BlockPlacer.inReach(player, cursor, 4.5) && !lit(level, cursor) && light < darkest) {
                        darkest = light;
                        best = cursor.immutable();
                     }
                  }
               }

               if (best != null) {
                  BlockPlacer.place(client, best, stack -> {
                     String s0$ = this.kind.get();

                     return switch (s0$) {
                        case "Torch" -> stack.is(Items.TORCH);
                        case "Soul Torch" -> stack.is(Items.SOUL_TORCH);
                        default -> stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH);
                     };
                  }, true, true, false);
               }
            }
         }
      }

      private static boolean lit(Level level, BlockPos pos) {
         for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            BlockState state = level.getBlockState(near);
            if (state.is(Blocks.TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
               return true;
            }
         }

         return false;
      }
   }

   public static final class BedDefender extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.5);

      public BedDefender() {
         super("BedDefender", "Surround nearby beds with defensive hotbar blocks.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null && this.placer.ready()) {
            BlockPos c = client.player.blockPosition();
            List<BlockPos> targets = new ArrayList<>();

            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
               if (client.level.getBlockState(pos).is(BlockTags.BEDS)) {
                  targets.add(pos.above());

                  for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                     targets.add(pos.relative(dir));
                  }
               }
            }

            if (!targets.isEmpty()) {
               this.placer.place(client, targets, BlockPlacer::solidFiller);
            }
         }
      }
   }

   public static final class BlockIn extends Module {
      private final PlaceSettings placer = new PlaceSettings(this, 0, 4, 4.5);

      public BlockIn() {
         super("BlockIn", "Rapidly build a full 1x2 protective shell around.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null && this.placer.ready()) {
            BlockPos feet = client.player.blockPosition();
            List<BlockPos> shell = new ArrayList<>(10);

            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
               shell.add(feet.relative(dir));
               shell.add(feet.above().relative(dir));
            }

            shell.add(feet.above(2));
            this.placer.place(client, shell, BlockPlacer::solidFiller);
         }
      }
   }

   public static final class ChestAura extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away a container may be", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between opens", 5.0, 1.0, 40.0, 1.0));
      private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the container in packets", true));
      private final BooleanSetting onlyOnce = this.addSetting(new BooleanSetting("Open Once", "Remember containers that were already opened", true));
      private final BooleanSetting shulkers = this.addSetting(new BooleanSetting("Shulker Boxes", "Also open shulker boxes", true));
      private final BooleanSetting enderChests = this.addSetting(new BooleanSetting("Ender Chests", "Also open ender chests", false));
      private final Set<BlockPos> opened = new HashSet<>();
      private int wait;

      public ChestAura() {
         super("Chest Aura", "Automatically open containers near you.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.opened.clear();
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            Level level = client.level;
            int r = (int)Math.ceil(this.range.get());
            BlockPos center = player.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
               if (this.container(level.getBlockState(pos))
                  && (!this.onlyOnce.get() || !this.opened.contains(pos))
                  && BlockPlacer.inReach(player, pos, this.range.get())) {
                  double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = pos.immutable();
                  }
               }
            }

            if (best != null) {
               if (this.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, best), 0.0F, true, 0)) {
                  this.wait = 0;
               } else {
                  Vec3 hit = Vec3.atCenterOf(best).add(0.0, 0.5, 0.0);
                  client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
                  player.swing(InteractionHand.MAIN_HAND);
                  this.opened.add(best);
               }
            }
         }
      }

      private boolean container(BlockState state) {
         Block block = state.getBlock();
         if (block instanceof EnderChestBlock) {
            return this.enderChests.get();
         } else {
            return block instanceof ShulkerBoxBlock ? this.shulkers.get() : block instanceof AbstractChestBlock || block instanceof BarrelBlock;
         }
      }

      @Override
      public String info() {
         return this.opened.isEmpty() ? null : this.opened.size() + " opened";
      }
   }

   public static final class Collisions extends Module {
      private static WorldExtras.Collisions instance;
      private final BooleanSetting magma = this.addSetting(new BooleanSetting("Magma", "Walk over magma as if it were a raised block", true));
      private final BooleanSetting cactus = this.addSetting(new BooleanSetting("Cactus", "Treat cacti as full solid blocks", true));
      private final BooleanSetting berries = this.addSetting(new BooleanSetting("Berry Bushes", "Treat sweet berry bushes as solid", true));
      private final BooleanSetting fire = this.addSetting(new BooleanSetting("Fire", "Treat fire and campfires as solid", true));
      private final BooleanSetting powderSnow = this.addSetting(new BooleanSetting("Powder Snow", "Walk on powder snow", true));
      private final BooleanSetting cobweb = this.addSetting(new BooleanSetting("Cobwebs", "Treat cobwebs as solid", false));
      private final BooleanSetting lava = this.addSetting(new BooleanSetting("Lava", "Treat lava as solid and walk over it", false));
      private final BooleanSetting ignoreSneak = this.addSetting(
         new BooleanSetting("Off While Sneaking", "Use normal block collisions while sneaking", true)
      );
      private static final VoxelShape RAISED = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.25, 1.0);

      public Collisions() {
         super("Collisions", "Make dangerous blocks solid so you can walk over them.", Category.WORLD);
         instance = this;
      }

      public static VoxelShape replacement(BlockState state) {
         WorldExtras.Collisions module = instance;
         if (module != null && module.isEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (module.ignoreSneak.get() && client.player != null && client.player.isShiftKeyDown()) {
               return null;
            } else {
               Block block = state.getBlock();
               if (block == Blocks.MAGMA_BLOCK) {
                  return module.magma.get() ? RAISED : null;
               } else if (block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER) {
                  return module.cactus.get() ? Shapes.block() : null;
               } else if (block == Blocks.SWEET_BERRY_BUSH) {
                  return module.berries.get() ? Shapes.block() : null;
               } else if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
                  return module.fire.get() ? Shapes.block() : null;
               } else if (block == Blocks.POWDER_SNOW) {
                  return module.powderSnow.get() ? Shapes.block() : null;
               } else if (block == Blocks.COBWEB) {
                  return module.cobweb.get() ? Shapes.block() : null;
               } else if (block == Blocks.LAVA) {
                  return module.lava.get() ? Shapes.block() : null;
               } else {
                  return null;
               }
            }
         } else {
            return null;
         }
      }
   }

   public static final class Extinguish extends Module {
      public Extinguish() {
         super("Extinguish", "Automatically punch out fire blocks around your feet.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null) {
            BlockPos c = client.player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 2, 3))) {
               BlockState s = client.level.getBlockState(pos);
               if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
                  client.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
                  client.player.swing(InteractionHand.MAIN_HAND);
                  return;
               }
            }
         }
      }
   }

   public static final class Flamethrower extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far a target may be", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between ignitions", 4.0, 1.0, 40.0, 1.0));
      private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Burn players", true));
      private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Burn hostile mobs", true));
      private final BooleanSetting skipBurning = this.addSetting(new BooleanSetting("Skip Burning", "Ignore targets that are already on fire", true));
      private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the fire spot in packets", true));
      private int wait;

      public Flamethrower() {
         super("Flamethrower", "Ignite the ground beneath nearby enemies.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            Level level = client.level;
            int slot = Inventories.findHotbar(player, stack -> stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE));
            if (slot >= 0) {
               for (Entity entity : client.level.entitiesForRendering()) {
                  if (Targets.isCandidate(player, entity, this.players.get(), this.mobs.get(), false, true)
                     && (!this.skipBurning.get() || !entity.isOnFire())
                     && !(Targets.reach(player, entity) > this.range.get())) {
                     LivingEntity target = (LivingEntity)entity;
                     BlockPos feet = target.blockPosition();
                     if (level.getBlockState(feet).isAir() && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                        if (this.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, feet.below()), 0.0F, true, 5)) {
                           this.wait = 0;
                           return;
                        }

                        int previous = player.getInventory().getSelectedSlot();
                        player.getInventory().setSelectedSlot(slot);
                        Vec3 hit = Vec3.atCenterOf(feet.below()).add(0.0, 0.5, 0.0);
                        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, feet.below(), false));
                        player.swing(InteractionHand.MAIN_HAND);
                        player.getInventory().setSelectedSlot(previous);
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   public static final class Fucker extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Break reach through walls", 4.5, 1.5, 6.0, 0.1));
      private final BooleanSetting beds = this.addSetting(new BooleanSetting("Beds", "Break nearby beds", true));
      private final BooleanSetting eggs = this.addSetting(new BooleanSetting("Dragon Eggs", "Break dragon eggs", true));
      private final BooleanSetting cakes = this.addSetting(new BooleanSetting("Cakes", "Break cakes", false));

      public Fucker() {
         super("Fucker", "Break nearby Beds, Dragon Eggs, or Cakes through walls.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            int r = (int)Math.ceil(this.range.get());
            BlockPos c = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
               if (BlockPlacer.inReach(player, pos, this.range.get()) && this.matches(client.level.getBlockState(pos))) {
                  RotationManager.request(player, RotationManager.toward(player, pos), 90.0F, true, 5);
                  client.gameMode.continueDestroyBlock(pos.immutable(), Direction.UP);
                  player.swing(InteractionHand.MAIN_HAND);
                  return;
               }
            }
         }
      }

      private boolean matches(BlockState state) {
         return this.beds.get() && state.is(BlockTags.BEDS) || this.eggs.get() && state.is(Blocks.DRAGON_EGG) || this.cakes.get() && state.is(Blocks.CAKE);
      }
   }

   public static final class LawnMower extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Clear radius around player", 4.0, 2.0, 5.5, 0.5));
      private final BooleanSetting flowers = this.addSetting(new BooleanSetting("Flowers", "Also clear flowers", false));
      private final BooleanSetting cobwebs = this.addSetting(new BooleanSetting("Cobwebs", "Break cobwebs when holding a sword or shears", true));

      public LawnMower() {
         super("LawnMower", "Clear tall grass, ferns, and cobwebs around you.", Category.WORLD);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            int r = (int)Math.ceil(this.range.get());
            BlockPos c = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -1, -r), c.offset(r, 2, r))) {
               if (BlockPlacer.inReach(player, pos, this.range.get())) {
                  BlockState s = client.level.getBlockState(pos);
                  if (this.shouldClear(s)) {
                     client.gameMode.startDestroyBlock(pos.immutable(), Direction.UP);
                     player.swing(InteractionHand.MAIN_HAND);
                     return;
                  }
               }
            }
         }
      }

      private boolean shouldClear(BlockState s) {
         Block b = s.getBlock();
         if (b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH || b == Blocks.FIRE) {
            return true;
         } else {
            return this.flowers.get() && s.is(BlockTags.FLOWERS) ? true : this.cobwebs.get() && b == Blocks.COBWEB;
         }
      }
   }

   public static final class PacketMine extends Module {
      private BlockPos targetPos;

      public PacketMine() {
         super("PacketMine", "Mine clicked blocks silently in the background.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.targetPos = null;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldExtras.ready(client) && client.gui.screen() == null) {
            if (client.options.keyAttack.isDown() && client.hitResult instanceof BlockHitResult bhr && bhr.getType() == Type.BLOCK) {
               this.targetPos = bhr.getBlockPos().immutable();
            }

            if (this.targetPos != null) {
               if (!client.level.getBlockState(this.targetPos).isAir() && BlockPlacer.inReach(client.player, this.targetPos, 5.2)) {
                  client.gameMode.continueDestroyBlock(this.targetPos, Direction.UP);
               } else {
                  this.targetPos = null;
               }
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         if (this.targetPos != null) {
            Render3D.block(this.targetPos, -16748289, true, true, 1.8F, 55, true);
         }
      }
   }

   public static final class StrongholdFinder extends Module {
      private int trackedId = -1;

      public StrongholdFinder() {
         super("StrongholdFinder", "Track Eye of Ender flight angles to locate strongholds.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.trackedId = -1;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (client.level != null && client.player != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
               if (entity instanceof EyeOfEnder eye && eye.getId() != this.trackedId && eye.tickCount > 12) {
                  this.trackedId = eye.getId();
                  Vec3 delta = eye.getDeltaMovement();
                  double angle = Math.toDegrees(Math.atan2(-delta.x, delta.z));
                  CommandManager.feedback(
                     String.format(Locale.ROOT, "§b[StrongholdFinder] §7Eye heading: §f%.1f° §7from §f%d, %d", angle, eye.getBlockX(), eye.getBlockZ())
                  );
               }
            }
         }
      }
   }
}
