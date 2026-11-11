package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.util.BlockBreaking;
import gemini.module.Category;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.Inventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class AutoFarm extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far from you crops are worked", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bursts", 2.0, 0.0, 10.0, 1.0));
   private final NumberSetting perTick = this.addSetting(new NumberSetting("Blocks Per Tick", "Most crops handled in one burst", 2.0, 1.0, 8.0, 1.0));
   private final BooleanSetting crops = this.addSetting(new BooleanSetting("Crops", "Wheat, carrots, potatoes, beetroot and cocoa", true));
   private final BooleanSetting wart = this.addSetting(new BooleanSetting("Nether Wart", "Harvest grown nether wart", true));
   private final BooleanSetting fruit = this.addSetting(new BooleanSetting("Melons And Pumpkins", "Only fruit that grew next to a stem", true));
   private final BooleanSetting stalks = this.addSetting(new BooleanSetting("Sugar Cane And Cactus", "Cut everything above the bottom block", true));
   private final BooleanSetting replant = this.addSetting(new BooleanSetting("Replant", "Plant the same seed again after harvesting", true));
   private final BooleanSetting boneMeal = this.addSetting(
      new BooleanSetting("Bone Meal", "Apply hotbar bone meal to unripe crops nearby", false)
   );
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand while working", true));
   private final Map<BlockPos, Item> seeds = new LinkedHashMap<>();
   private BlockPos current;
   private int stuck;
   private int cooldown;

   public AutoFarm() {
      super("Auto Farm", "Harvest ripe crops around you.", Category.WORLD);
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      if (this.current != null && client.gameMode != null) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.current = null;
      this.seeds.clear();
   }

   private static Item seedFor(Block block) {
      if (block == Blocks.WHEAT) {
         return Items.WHEAT_SEEDS;
      } else if (block == Blocks.CARROTS) {
         return Items.CARROT;
      } else if (block == Blocks.POTATOES) {
         return Items.POTATO;
      } else if (block == Blocks.BEETROOTS) {
         return Items.BEETROOT_SEEDS;
      } else if (block == Blocks.NETHER_WART) {
         return Items.NETHER_WART;
      } else {
         return block == Blocks.TORCHFLOWER_CROP ? Items.TORCHFLOWER_SEEDS : null;
      }
   }

   private boolean ripe(BlockState state) {
      Block block = state.getBlock();
      if (this.crops.get() && block instanceof CropBlock crop) {
         return crop.isMaxAge(state);
      } else {
         return this.crops.get() && block instanceof CocoaBlock
            ? (Integer)state.getValue(CocoaBlock.AGE) >= 2
            : this.wart.get() && block instanceof NetherWartBlock && (Integer)state.getValue(NetherWartBlock.AGE) >= 3;
      }
   }

   private boolean harvestable(ClientLevel level, BlockPos pos, BlockState state) {
      if (this.ripe(state)) {
         return true;
      } else if (!this.fruit.get() || !state.is(Blocks.MELON) && !state.is(Blocks.PUMPKIN)) {
         return this.stalks.get() && (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)) && level.getBlockState(pos.below()).is(state.getBlock());
      } else {
         for (Direction direction : Plane.HORIZONTAL) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.getBlock() instanceof AttachedStemBlock && neighbour.getValue(BlockStateProperties.HORIZONTAL_FACING) == direction.getOpposite()) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean seedMatches(ItemStack stack, Item seed) {
      return stack.is(seed);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.readyNoScreen(client)) {
         LocalPlayer player = client.player;
         ClientLevel level = client.level;
         double reach = this.range.get();
         Vec3 eye = player.getEyePosition();
         boolean creative = player.isCreative();
         if (this.current != null) {
            BlockState state = level.getBlockState(this.current);
            if (!state.isAir() && ++this.stuck <= 80 && !(eye.distanceToSqr(Vec3.atCenterOf(this.current)) > (reach + 1.0) * (reach + 1.0))) {
               BlockBreaking.keep();
               client.gameMode.continueDestroyBlock(this.current, Direction.UP);
               if (this.swing.get()) {
                  Swing.attack(player, InteractionHand.MAIN_HAND);
               }

               return;
            }

            this.current = null;
            this.stuck = 0;
         }

         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            int radius = (int)Math.ceil(reach);
            BlockPos origin = BlockPos.containing(eye);
            MutableBlockPos cursor = new MutableBlockPos();
            List<BlockPos> harvest = new ArrayList<>();
            List<BlockPos> growing = new ArrayList<>();

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (!(eye.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5) > reach * reach)) {
                        BlockState state = level.getBlockState(cursor);
                        if (!state.isAir()) {
                           if (this.harvestable(level, cursor, state)) {
                              harvest.add(cursor.immutable());
                           } else if (this.boneMeal.get() && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                              growing.add(cursor.immutable());
                           }
                        }
                     }
                  }
               }
            }

            harvest.sort(Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx))));
            int budget = this.perTick.intValue();
            boolean worked = false;

            for (BlockPos pos : harvest) {
               if (budget <= 0) {
                  break;
               }

               BlockState state = level.getBlockState(pos);
               Item seed = seedFor(state.getBlock());
               BlockBreaking.keep();
               client.gameMode.startDestroyBlock(pos, Direction.UP);
               worked = true;
               budget--;
               if (seed != null && this.replant.get()) {
                  this.seeds.put(pos, seed);
               }

               if (!creative && !level.getBlockState(pos).isAir()) {
                  this.current = pos;
                  this.stuck = 0;
                  break;
               }
            }

            if (this.current == null && this.replant.get() && !this.seeds.isEmpty()) {
               worked |= this.replantSeeds(client, player, level, reach, budget);
            }

            if (this.current == null && !worked && !growing.isEmpty()) {
               growing.sort(Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx))));
               worked = this.fertilise(client, player, growing.get(0));
            }

            if (worked && this.swing.get()) {
               Swing.attack(player, InteractionHand.MAIN_HAND);
            }

            this.cooldown = worked ? this.delay.intValue() : 6;
         }
      }
   }

   private boolean replantSeeds(Minecraft client, LocalPlayer player, ClientLevel level, double reach, int budget) {
      boolean planted = false;
      Iterator<Entry<BlockPos, Item>> iterator = this.seeds.entrySet().iterator();

      while (iterator.hasNext() && budget > 0) {
         Entry<BlockPos, Item> entry = iterator.next();
         BlockPos pos = entry.getKey();
         Item seed = entry.getValue();
         BlockState state = level.getBlockState(pos);
         if (state.isAir() && BlockPlacer.inReach(player, pos, reach)) {
            if ((BlockPlacer.pickSlot(player, stack -> seedMatches(stack, seed)) >= 0 || pullSeed(client, player, seed))
               && BlockPlacer.place(client, pos, stack -> seedMatches(stack, seed), this.swing.get(), true, false)) {
               iterator.remove();
               planted = true;
               budget--;
            }
         } else if (!state.isAir() && !(state.getBlock() instanceof CropBlock) && !(state.getBlock() instanceof NetherWartBlock)) {
            iterator.remove();
         }
      }

      if (this.seeds.size() > 128) {
         this.seeds.clear();
      }

      return planted;
   }

   private static boolean pullSeed(Minecraft client, LocalPlayer player, Item seed) {
      int index = Inventories.find(player, stack -> stack.is(seed), false);
      if (index < 0) {
         return false;
      } else {
         client.gameMode
            .handleContainerInput(
               player.inventoryMenu.containerId, Inventories.menuSlot(index), player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player
            );
         return true;
      }
   }

   private boolean fertilise(Minecraft client, LocalPlayer player, BlockPos pos) {
      int slot = BlockPlacer.pickSlot(player, stack -> stack.is(Items.BONE_MEAL));
      if (slot < 0 || !Hands.claimMainHand()) {
         return false;
      } else {
         int previous = player.getInventory().getSelectedSlot();
         player.getInventory().setSelectedSlot(slot);
         client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
         player.getInventory().setSelectedSlot(previous);
         return true;
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.seeds.clear();
   }
}
