package gemini.module.modules.player;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.Damage;
import gemini.util.Inventories;
import gemini.util.MoveUtil;
import gemini.util.OffhandClaim;
import gemini.util.RotationManager;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlayerExtras {
   private PlayerExtras() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new PlayerExtras.AutoMend());
      sink.accept(new PlayerExtras.AntiBreak());
      sink.accept(new PlayerExtras.Eagle());
      sink.accept(new PlayerExtras.Tower());
      sink.accept(new PlayerExtras.Phase());
      sink.accept(new PlayerExtras.AutoSleep());
      sink.accept(new PlayerExtras.InventoryCleaner());
      sink.accept(new PlayerExtras.FastUse());
      sink.accept(new PlayerExtras.NoRotateSet());
      sink.accept(new PlayerExtras.AutoWindCharge());
      sink.accept(new PlayerExtras.GhostHand());
      sink.accept(new PlayerExtras.SmartEat());
      sink.accept(new PlayerExtras.AutoClutch());
      sink.accept(new PlayerExtras.NoPush());
      sink.accept(new PlayerExtras.HotbarSorter());
   }

   static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null && client.level != null && client.gameMode != null && !player.isSpectator() && !player.isDeadOrDying();
   }

   static boolean mending(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamageableItem() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP);
   }

   public static final class AntiBreak extends Module {
      private final NumberSetting durability = this.addSetting(
         new NumberSetting("Durability", "Protect items with this many uses left or fewer", 10.0, 1.0, 100.0, 1.0)
      );
      private final BooleanSetting tools = this.addSetting(new BooleanSetting("Held Items", "Move a nearly broken held item out of the hotbar", true));
      private final BooleanSetting armor = this.addSetting(new BooleanSetting("Armor", "Take off armor that is about to break", true));
      private final BooleanSetting notify = this.addSetting(new BooleanSetting("Notify", "Say which item was saved", true));

      public AntiBreak() {
         super("Anti Break", "Stop using items that are one hit from breaking.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && Inventories.inventoryOpenOrClosed(client.player)) {
            LocalPlayer player = client.player;
            if (this.tools.get()) {
               ItemStack held = player.getMainHandItem();
               if (this.atRisk(held)) {
                  int target = emptySlot(player);
                  if (target >= 0) {
                     int selected = player.getInventory().getSelectedSlot();
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(selected), 0, ContainerInput.PICKUP, player);
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(target), 0, ContainerInput.PICKUP, player);
                     this.report(held);
                     return;
                  }
               }
            }

            if (this.armor.get()) {
               for (int index = 36; index < 40; index++) {
                  ItemStack worn = player.getInventory().getItem(index);
                  if (this.atRisk(worn)) {
                     int target = emptySlot(player);
                     if (target >= 0) {
                        client.gameMode.handleContainerInput(player.inventoryMenu.containerId, 8 - (index - 36), 0, ContainerInput.QUICK_MOVE, player);
                        this.report(worn);
                        return;
                     }
                  }
               }
            }
         }
      }

      private boolean atRisk(ItemStack stack) {
         return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= this.durability.intValue();
      }

      private static int emptySlot(LocalPlayer player) {
         for (int index = 9; index < 36; index++) {
            if (player.getInventory().getItem(index).isEmpty()) {
               return index;
            }
         }

         return -1;
      }

      private void report(ItemStack stack) {
         if (this.notify.get()) {
            CommandManager.feedback("§bAnti Break §7saved §f" + stack.getHoverName().getString() + "§7.");
         }
      }
   }

   public static final class AutoClutch extends Module {
      private final NumberSetting minFall = this.addSetting(new NumberSetting("Min Fall", "Minimum fall distance before clutching", 3.5, 2.0, 12.0, 0.5));
      private final BooleanSetting pickupWater = this.addSetting(
         new BooleanSetting("Pickup Water", "Automatically scoop placed water back up on landing", true)
      );
      private final BooleanSetting silentAim = this.addSetting(new BooleanSetting("Silent Aim", "Aim downward only in packets", true));
      private BlockPos placedWaterPos;
      private int pickupTicks;

      public AutoClutch() {
         super("AutoClutch", "Auto MLG clutch with water, cobweb, slime, or hay.", Category.PLAYER);
      }

      @Override
      public void onDisable() {
         this.placedWaterPos = null;
         this.pickupTicks = 0;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            Level level = client.level;
            if (this.placedWaterPos != null) {
               if (this.pickupTicks-- <= 0 || !this.pickupWater.get()) {
                  this.placedWaterPos = null;
               } else if (player.onGround() || player.isInWater()) {
                  int bucketSlot = Inventories.findHotbar(player, s -> s.is(Items.BUCKET));
                  if (bucketSlot >= 0 && level.getBlockState(this.placedWaterPos).is(Blocks.WATER)) {
                     RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), 10, 2.0F);
                     int prev = player.getInventory().getSelectedSlot();
                     player.getInventory().setSelectedSlot(bucketSlot);
                     client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                     player.getInventory().setSelectedSlot(prev);
                  }

                  this.placedWaterPos = null;
               }
            } else {
               if (!player.onGround()
                  && !player.isInWater()
                  && !player.isFallFlying()
                  && player.fallDistance >= this.minFall.floatValue()
                  && player.getDeltaMovement().y < -0.25) {
                  BlockPos feet = player.blockPosition();
                  BlockPos ground = null;

                  for (int dy = 1; dy <= 4; dy++) {
                     BlockPos check = feet.below(dy);
                     if (!level.getBlockState(check).getCollisionShape(level, check).isEmpty()) {
                        ground = check;
                        break;
                     }
                  }

                  if (ground != null) {
                     int clutchSlot = findClutchSlot(player, level.dimension() == Level.NETHER);
                     if (clutchSlot >= 0) {
                        RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, this.silentAim.get(), 10, 3.0F);
                        ItemStack stack = player.getInventory().getItem(clutchSlot);
                        int prev = player.getInventory().getSelectedSlot();
                        player.getInventory().setSelectedSlot(clutchSlot);
                        if (!stack.is(Items.WATER_BUCKET) && !stack.is(Items.POWDER_SNOW_BUCKET)) {
                           Vec3 hit = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
                           client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, ground, false));
                           player.swing(InteractionHand.MAIN_HAND);
                        } else {
                           client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                           if (stack.is(Items.WATER_BUCKET)) {
                              this.placedWaterPos = ground.above();
                              this.pickupTicks = 12;
                           }
                        }

                        player.getInventory().setSelectedSlot(prev);
                     }
                  }
               }
            }
         }
      }

      private static int findClutchSlot(LocalPlayer player, boolean inNether) {
         if (!inNether) {
            int water = Inventories.findHotbar(player, s -> s.is(Items.WATER_BUCKET));
            if (water >= 0) {
               return water;
            }
         }

         int snow = Inventories.findHotbar(player, s -> s.is(Items.POWDER_SNOW_BUCKET));
         if (snow >= 0) {
            return snow;
         } else {
            int web = Inventories.findHotbar(player, s -> s.is(Items.COBWEB) || s.is(Items.SLIME_BLOCK) || s.is(Items.HAY_BLOCK));
            return web >= 0 ? web : Inventories.findHotbar(player, s -> s.is(Items.TWISTING_VINES) || s.is(Items.SCAFFOLDING));
         }
      }
   }

   public static final class AutoMend extends Module {
      private final NumberSetting threshold = this.addSetting(
         new NumberSetting("Repair Below %", "Start repairing an item under this durability", 90.0, 5.0, 100.0, 1.0)
      );
      private final BooleanSetting includeArmor = this.addSetting(
         new BooleanSetting("Include Armor", "Also pull worn armor into the offhand while it mends", false)
      );
      private final BooleanSetting restore = this.addSetting(
         new BooleanSetting("Restore Offhand", "Put the original offhand item back when everything", true)
      );
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between swaps", 10.0, 1.0, 60.0, 1.0));
      private ItemStack original = ItemStack.EMPTY;
      private int originalSlot = -1;
      private boolean swapped;
      private int wait;

      public AutoMend() {
         super("Auto Mend", "Swap damaged Mending gear to offhand to repair.", Category.PLAYER);
      }

      @Override
      public void onEnable() {
         this.swapped = false;
         this.original = ItemStack.EMPTY;
         this.originalSlot = -1;
      }

      @Override
      public void onDisable() {
         Minecraft client = Minecraft.getInstance();
         if (this.restore.get() && this.swapped && PlayerExtras.ready(client) && this.originalSlot >= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
            Inventories.swapToOffhand(client, client.player, this.originalSlot);
         }

         this.swapped = false;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player) && !OffhandClaim.totemOwns(client.player)) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            ItemStack offhand = player.getOffhandItem();
            if (!PlayerExtras.mending(offhand) || !offhand.isDamaged()) {
               int slot = Inventories.find(player, stackx -> PlayerExtras.mending(stackx) && durability(stackx) < this.threshold.get(), false);
               if (slot < 0 && this.includeArmor.get()) {
                  for (int armor = 36; armor < 40; armor++) {
                     ItemStack stack = player.getInventory().getItem(armor);
                     if (PlayerExtras.mending(stack) && durability(stack) < this.threshold.get()) {
                        slot = armor;
                        break;
                     }
                  }
               }

               if (slot < 0) {
                  if (this.swapped && this.restore.get() && this.originalSlot >= 0) {
                     Inventories.swapToOffhand(client, player, this.originalSlot);
                     this.swapped = false;
                  }
               } else if (slot >= 36) {
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, 8 - (slot - 36), 0, ContainerInput.QUICK_MOVE, player);
               } else {
                  if (!this.swapped) {
                     this.original = offhand.copy();
                     this.originalSlot = slot;
                     this.swapped = true;
                  }

                  Inventories.swapToOffhand(client, player, slot);
               }
            }
         }
      }

      private static double durability(ItemStack stack) {
         return stack.getMaxDamage() <= 0 ? 100.0 : 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
      }

      @Override
      public String info() {
         return this.swapped ? "mending" : null;
      }
   }

   public static final class AutoSleep extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Blocks around you to look for a bed", 4.0, 1.0, 6.0, 0.5));
      private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the bed server-side only", true));
      private final BooleanSetting disableAfter = this.addSetting(new BooleanSetting("Disable After", "Turn off once you are lying down", false));
      private int wait;

      public AutoSleep() {
         super("Auto Sleep", "Hop into the nearest bed when night falls.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            Level level = client.level;
            if (player.isSleeping()) {
               if (this.disableAfter.get()) {
                  this.setEnabled(false, false);
               }
            } else if (level.dimension() == Level.OVERWORLD && level.isDarkOutside() && this.wait-- <= 0) {
               this.wait = 10;
               int r = (int)Math.ceil(this.range.get());
               BlockPos center = player.blockPosition();
               BlockPos best = null;
               double bestDistance = Double.MAX_VALUE;

               for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
                  if (level.getBlockState(pos).is(BlockTags.BEDS) && BlockPlacer.inReach(player, pos, this.range.get())) {
                     double distance = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                     if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                     }
                  }
               }

               if (best != null && RotationManager.request(player, RotationManager.toward(player, best), 0.0F, this.rotate.get(), 0)) {
                  Vec3 hit = Vec3.atCenterOf(best).add(0.0, 0.5, 0.0);
                  client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, best, false));
                  player.swing(InteractionHand.MAIN_HAND);
               }
            }
         }
      }
   }

   public static final class AutoWindCharge extends Module {
      private boolean wasOnGround = true;

      public AutoWindCharge() {
         super("AutoWindCharge", "Fire a Wind Charge downward when jumping.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (this.wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0.25) {
               int slot = Inventories.findHotbar(player, s -> s.is(Items.WIND_CHARGE));
               if (slot >= 0) {
                  RotationManager.request(player, player.getYRot(), 90.0F, 180.0F, true, 10, 3.0F);
                  int prev = player.getInventory().getSelectedSlot();
                  player.getInventory().setSelectedSlot(slot);
                  client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                  player.getInventory().setSelectedSlot(prev);
               }
            }

            this.wasOnGround = player.onGround();
         }
      }
   }

   public static final class Eagle extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Edge sneaks at drops; Always sneaks over air", "Edge", "Edge", "Always")
      );
      private final NumberSetting lookAhead = this.addSetting(
         new NumberSetting("Look Ahead", "How far past your hitbox counts as the edge", 0.3, 0.05, 0.6, 0.05)
      );
      private final BooleanSetting onlyBackwards = this.addSetting(new BooleanSetting("Only Backwards", "Only sneak while holding the back key", false));
      private boolean sneaking;

      public Eagle() {
         super("Eagle", "Hold sneak for you at block edges while bridging.", Category.PLAYER);
      }

      @Override
      public void onDisable() {
         this.release();
      }

      private void release() {
         if (this.sneaking) {
            this.sneaking = false;
            Minecraft client = Minecraft.getInstance();
            client.options.keyShift.setDown(MoveUtil.physicalKeyDown(client.options.keyShift));
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (player.onGround() && (!this.onlyBackwards.get() || client.options.keyDown.isDown())) {
               boolean edge = this.mode.is("Always") ? nothingBelow(client, player, 0.0) : nothingBelow(client, player, this.lookAhead.get());
               if (edge) {
                  this.sneaking = true;
                  client.options.keyShift.setDown(true);
               } else {
                  this.release();
               }
            } else {
               this.release();
            }
         } else {
            this.release();
         }
      }

      private static boolean nothingBelow(Minecraft client, LocalPlayer player, double extra) {
         Level level = client.level;
         double y = player.getY() - 0.5;
         double half = player.getBbWidth() / 2.0 + extra;

         for (double dx : new double[]{-half, half}) {
            for (double dz : new double[]{-half, half}) {
               BlockPos pos = BlockPos.containing(player.getX() + dx, y, player.getZ() + dz);
               if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                  return false;
               }
            }
         }

         return true;
      }

      @Override
      public String info() {
         return this.mode.get();
      }
   }

   public static final class FastUse extends Module {
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between item uses", 1.0, 1.0, 4.0, 1.0));
      private int wait;

      public FastUse() {
         super("FastUse", "Use throwables and XP bottles rapidly on right-click.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null && client.options.keyUse.isDown() && this.wait-- <= 0) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            ItemStack main = player.getMainHandItem();
            if (main.is(Items.EXPERIENCE_BOTTLE)
               || main.is(Items.SNOWBALL)
               || main.is(Items.EGG)
               || main.is(Items.WIND_CHARGE)
               || main.is(Items.ENDER_PEARL)
               || main.is(Items.SPLASH_POTION)
               || main.is(Items.LINGERING_POTION)) {
               client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
         }
      }
   }

   public static final class GhostHand extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Max reach through walls", 4.5, 2.0, 6.0, 0.5));
      private boolean wasDown;

      public GhostHand() {
         super("GhostHand", "Open containers through walls.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            boolean down = client.options.keyUse.isDown();
            if (down && !this.wasDown) {
               LocalPlayer player = client.player;
               Vec3 eye = player.getEyePosition();
               Vec3 look = player.getLookAngle();

               for (double d = 1.0; d <= this.range.get(); d += 0.4) {
                  BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
                  BlockState s = client.level.getBlockState(pos);
                  if (s.getBlock() instanceof AbstractChestBlock
                     || s.getBlock() instanceof EnderChestBlock
                     || s.getBlock() instanceof ShulkerBoxBlock
                     || s.getBlock() instanceof BarrelBlock) {
                     client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                     player.swing(InteractionHand.MAIN_HAND);
                     break;
                  }
               }
            }

            this.wasDown = down;
         }
      }
   }

   public static final class HotbarSorter extends Module {
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay Ticks", "Ticks between slot swaps", 3.0, 1.0, 15.0, 1.0));
      private final BooleanSetting swordSlot1 = this.addSetting(new BooleanSetting("Sword Slot 1", "Keep sword in slot 1", true));
      private final BooleanSetting pickSlot2 = this.addSetting(new BooleanSetting("Pickaxe Slot 2", "Keep pickaxe in slot 2", true));
      private final BooleanSetting gappleSlot3 = this.addSetting(new BooleanSetting("Gapple Slot 3", "Keep Golden Apples in slot 3", true));
      private final BooleanSetting blocksSlot9 = this.addSetting(new BooleanSetting("Blocks Slot 9", "Keep building blocks in slot 9", true));
      private int wait;

      public HotbarSorter() {
         super("HotbarSorter", "Keep sword, pickaxe, gapples, and blocks sorted.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            if (this.swordSlot1.get() && this.ensureSlot(client, player, 0, s -> s.is(ItemTags.SWORDS))) {
               return;
            }

            if (this.pickSlot2.get() && this.ensureSlot(client, player, 1, s -> s.is(ItemTags.PICKAXES))) {
               return;
            }

            if (this.gappleSlot3.get() && this.ensureSlot(client, player, 2, s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE))) {
               return;
            }

            if (this.blocksSlot9.get()) {
               this.ensureSlot(client, player, 8, BlockPlacer::solidFiller);
            }
         }
      }

      private boolean ensureSlot(Minecraft client, LocalPlayer player, int targetHotbar, Predicate<ItemStack> matcher) {
         if (matcher.test(player.getInventory().getItem(targetHotbar))) {
            return false;
         } else {
            int found = Inventories.find(player, matcher, false);
            if (found >= 0 && found != targetHotbar) {
               client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(found), targetHotbar, ContainerInput.SWAP, player);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   public static final class InventoryCleaner extends Module {
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay Ticks", "Ticks between inventory clean actions", 3.0, 1.0, 20.0, 1.0));
      private final BooleanSetting dropJunk = this.addSetting(new BooleanSetting("Drop Junk", "Drop rotten flesh, dirt, seeds, and poisonous", true));
      private int wait;

      public InventoryCleaner() {
         super("InventoryCleaner", "Clean junk items and organize your hotbar.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && this.wait-- <= 0 && Inventories.inventoryOpenOrClosed(client.player)) {
            this.wait = this.delay.intValue();
            LocalPlayer player = client.player;
            if (this.dropJunk.get()) {
               for (int i = 9; i < 36; i++) {
                  ItemStack stack = player.getInventory().getItem(i);
                  if (isJunk(stack)) {
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(i), 1, ContainerInput.THROW, player);
                     return;
                  }
               }
            }

            if (!player.getInventory().getItem(0).is(ItemTags.SWORDS)) {
               int sword = Inventories.find(player, s -> s.is(ItemTags.SWORDS), false);
               if (sword > 0) {
                  client.gameMode.handleContainerInput(player.inventoryMenu.containerId, Inventories.menuSlot(sword), 0, ContainerInput.SWAP, player);
               }
            }
         }
      }

      private static boolean isJunk(ItemStack s) {
         if (s.isEmpty() || s.isEnchanted() || s.has(DataComponents.CUSTOM_NAME)) {
            return false;
         }
         return s.is(Items.ROTTEN_FLESH)
            || s.is(Items.POISONOUS_POTATO)
            || s.is(Items.WHEAT_SEEDS)
            || s.is(Items.BEETROOT_SEEDS)
            || s.is(Items.DEAD_BUSH)
            || s.is(Items.BOWL)
            || s.is(Items.STRING)
            || s.is(Items.SPIDER_EYE)
            || s.is(Items.FEATHER)
            || s.is(Items.FLINT);
      }
   }

   public static final class NoPush extends Module {
      private final BooleanSetting entities = this.addSetting(new BooleanSetting("Entities", "Cancel horizontal push from crowded entities", true));
      private final BooleanSetting water = this.addSetting(new BooleanSetting("Water Current", "Resist flowing water drift when standing still", true));

      public NoPush() {
         super("NoPush", "Prevent entities and water currents from pushing you.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client)) {
            LocalPlayer player = client.player;
            if (!MoveUtil.hasInput(player) && player.hurtTime == 0) {
               if (this.water.get() && player.isInWater()) {
                  Vec3 v = player.getDeltaMovement();
                  player.setDeltaMovement(v.x * 0.15, v.y, v.z * 0.15);
               } else if (this.entities.get() && player.onGround()) {
                  Vec3 v = player.getDeltaMovement();
                  if (v.horizontalDistanceSqr() > 1.0E-4 && v.horizontalDistanceSqr() < 0.04) {
                     player.setDeltaMovement(0.0, v.y, 0.0);
                  }
               }
            }
         }
      }
   }

   public static final class NoRotateSet extends Module {
      private float savedYaw;
      private float savedPitch;
      private boolean restorePending;

      public NoRotateSet() {
         super("NoRotateSet", "Block server packets from snapping your camera.", Category.PLAYER);
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         Minecraft client = Minecraft.getInstance();
         if (packet instanceof ClientboundPlayerPositionPacket && client.player != null) {
            this.savedYaw = client.player.getYRot();
            this.savedPitch = client.player.getXRot();
            this.restorePending = true;
         }

         return false;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.restorePending && client.player != null && client.getConnection() != null) {
            this.restorePending = false;
            client.getConnection().send(new Rot(client.player.getYRot(), client.player.getXRot(), client.player.onGround(), client.player.horizontalCollision));
            client.player.setYRot(this.savedYaw);
            client.player.setXRot(this.savedPitch);
         }
      }
   }

   public static final class Phase extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Walk keeps you grounded", "Walk", "Walk", "Full")
      );
      private final NumberSetting speed = this.addSetting(new NumberSetting("Speed", "Movement speed while phasing", 0.2, 0.05, 1.0, 0.01));
      private final BooleanSetting disableOnDrop = this.addSetting(
         new BooleanSetting("Disable On Fall", "Turn off when you leave the block you phased into", false)
      );

      public Phase() {
         super("Phase", "No-clip through blocks.", Category.PLAYER);
      }

      @Override
      protected boolean restoresEnabledState() {
         return false;
      }

      @Override
      public void onDisable() {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            player.noPhysics = false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client)) {
            LocalPlayer player = client.player;
            player.noPhysics = true;
            player.setOnGround(true);
            Vec3 direction = MoveUtil.direction(player);
            if (direction == null) {
               direction = Vec3.ZERO;
            }

            double vertical = 0.0;
            if (this.mode.is("Full")) {
               if (client.options.keyJump.isDown()) {
                  vertical = this.speed.get();
               } else if (client.options.keyShift.isDown()) {
                  vertical = -this.speed.get();
               }
            } else {
               vertical = client.options.keyShift.isDown() ? -this.speed.get() : 0.0;
            }

            player.setDeltaMovement(direction.x * this.speed.get(), vertical, direction.z * this.speed.get());
            if (this.disableOnDrop.get()
               && client.level.getBlockState(player.blockPosition()).isAir()
               && client.level.getBlockState(player.blockPosition().below()).isAir()) {
               this.setEnabled(false, false);
            }
         }
      }

      @Override
      public String info() {
         return this.mode.get();
      }
   }

   public static final class SmartEat extends Module {
      private final NumberSetting gappleHealth = this.addSetting(new NumberSetting("Gapple Health", "Eat Golden Apple below this health", 12.0, 4.0, 18.0, 1.0));
      private boolean eating;

      public SmartEat() {
         super("SmartEat", "Eat Golden Apples when low on HP and food when hungry.", Category.PLAYER);
      }

      @Override
      public void onDisable() {
         if (this.eating) {
            Minecraft client = Minecraft.getInstance();
            client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
            this.eating = false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            boolean needGap = Damage.health(player) <= this.gappleHealth.floatValue();
            boolean needFood = player.getFoodData().needsFood();
            if (needGap || needFood) {
               int slot = Inventories.findHotbar(
                  player, s -> needGap ? s.is(Items.ENCHANTED_GOLDEN_APPLE) || s.is(Items.GOLDEN_APPLE) : s.has(DataComponents.FOOD)
               );
               if (slot >= 0) {
                  player.getInventory().setSelectedSlot(slot);
                  client.options.keyUse.setDown(true);
                  this.eating = true;
                  return;
               }
            }

            if (this.eating) {
               client.options.keyUse.setDown(MoveUtil.physicalKeyDown(client.options.keyUse));
               this.eating = false;
            }
         }
      }
   }

   public static final class Tower extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Vanilla jumps normally, Fast nudges you up every", "Fast", "Vanilla", "Fast")
      );
      private final BooleanSetting requireJump = this.addSetting(new BooleanSetting("Hold Jump", "Only tower while jump is held", true));
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your arm on each placement", true));

      public Tower() {
         super("Tower", "Build straight up by jumping and placing blocks.", Category.PLAYER);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (PlayerExtras.ready(client) && client.gui.screen() == null) {
            LocalPlayer player = client.player;
            if (!this.requireJump.get() || client.options.keyJump.isDown()) {
               BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
               if (BlockPlacer.replaceable(client.level, below) && player.getY() - below.getY() > 0.9) {
                  if (BlockPlacer.place(client, below, BlockPlacer::solidFiller, this.swing.get(), true, true) && this.mode.is("Fast")) {
                     player.setDeltaMovement(player.getDeltaMovement().x * 0.3, 0.42, player.getDeltaMovement().z * 0.3);
                     player.setPos(player.getX(), below.getY() + 1.0, player.getZ());
                  }
               } else if (player.onGround()) {
                  player.setDeltaMovement(player.getDeltaMovement().x, 0.42, player.getDeltaMovement().z);
               }
            }
         }
      }

      @Override
      public String info() {
         return this.mode.get();
      }
   }
}
