package gemini.module.modules.world;

import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.ShapeSettings;
import gemini.util.BlockPlacer;
import gemini.util.Inventories;
import gemini.util.PlaceSettings;
import gemini.util.Rotations;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class WorldModules {
   private WorldModules() {
   }

   public static void register(Consumer<Module> sink) {
      sink.accept(new WorldModules.AirPlace());
      sink.accept(new WorldModules.AutoBreed());
      sink.accept(new WorldModules.AutoNametag());
      sink.accept(new WorldModules.AutoShearer());
      sink.accept(new WorldModules.LiquidFiller());
      sink.accept(new WorldModules.SpawnProofer());
      sink.accept(new WorldModules.VeinMiner());
      sink.accept(new WorldModules.EndermanLook());
      sink.accept(new WorldModules.AutoFarm());
   }

   private static boolean ready(Minecraft client) {
      LocalPlayer player = client.player;
      return player != null
         && client.level != null
         && client.gameMode != null
         && client.gui.screen() == null
         && !player.isSpectator()
         && !player.isDeadOrDying();
   }

   private static boolean withinReach(LocalPlayer player, Entity entity, double reach) {
      return entity.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
   }

   private static boolean useOnEntity(Minecraft client, LocalPlayer player, Entity target, Predicate<ItemStack> filter, boolean swing) {
      int slot = BlockPlacer.pickSlot(player, filter);
      if (slot < 0) {
         return false;
      } else {
         int previous = player.getInventory().getSelectedSlot();
         if (slot != previous) {
            player.getInventory().setSelectedSlot(slot);
         }

         InteractionResult result = client.gameMode.interact(player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
         if (swing) {
            player.swing(InteractionHand.MAIN_HAND);
         }

         if (slot != previous) {
            player.getInventory().setSelectedSlot(previous);
         }

         return result.consumesAction();
      }
   }

   private static Direction faceToward(LocalPlayer player, BlockPos pos) {
      Vec3 eye = player.getEyePosition();
      return Direction.getApproximateNearest(eye.x - (pos.getX() + 0.5), eye.y - (pos.getY() + 0.5), eye.z - (pos.getZ() + 0.5));
   }

   private static void trim(Int2LongOpenHashMap map) {
      if (map.size() > 512) {
         map.clear();
      }
   }

   public static final class AirPlace extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far ahead the block is placed", 5.0, 2.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between blocks while the button is held", 3.0, 0.0, 10.0, 1.0));
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when placing", true));
      private final BooleanSetting preview = this.addSetting(new BooleanSetting("Preview", "Outline the spot the block will go", true));
      private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Colour of the preview outline", 175).showIf(this.preview::get));
      private final ShapeSettings shape = new ShapeSettings(this, "Both", true, this.preview::get);
      private int cooldown;

      public AirPlace() {
         super("Air Place", "Place the block in your hand anywhere you aim, even with nothing to build on.", Category.WORLD);
      }

      private BlockPos target(LocalPlayer player, float partialTick) {
         Vec3 end = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(this.range.get()));
         return BlockPos.containing(end);
      }

      private static boolean aimingAtNothing(Minecraft client) {
         HitResult hit = client.hitResult;
         return hit == null || hit.getType() == Type.MISS;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (WorldModules.ready(client) && client.options.keyUse.isDown() && !client.player.isUsingItem() && aimingAtNothing(client)) {
            Item held = client.player.getMainHandItem().getItem();
            if (held instanceof BlockItem
               && BlockPlacer.place(client, this.target(client.player, 1.0F), stack -> stack.getItem() == held, this.swing.get(), false, true)) {
               this.cooldown = this.delay.intValue();
            }
         }
      }

      @Override
      public void onRender3D(float partialTick) {
         Minecraft client = Minecraft.getInstance();
         LocalPlayer player = client.player;
         if (this.preview.get()
            && player != null
            && client.gui.screen() == null
            && player.getMainHandItem().getItem() instanceof BlockItem
            && aimingAtNothing(client)) {
            this.shape.block(this.target(player, partialTick), this.color.argb());
         }
      }
   }

   public static final class AutoBreed extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away animals are fed", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between feedings", 3.0, 0.0, 20.0, 1.0));
      private final ModeSetting age = this.addSetting(new ModeSetting("Age", "Which animals get fed", "Adults", "Adults", "Babies", "Both"));
      private final ListSetting animals = this.addSetting(
         new ListSetting("Animals", "Only feed these (leave empty to feed every animal)", ListSetting.Kind.ENTITY)
      );
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when feeding", true));
      private final Int2LongOpenHashMap loveUntil = new Int2LongOpenHashMap();
      private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
      private final Int2IntOpenHashMap misses = new Int2IntOpenHashMap();
      private int cooldown;

      public AutoBreed() {
         super("Auto Breed", "Feed nearby animals with the food in your hotbar so they breed.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.loveUntil.clear();
         this.nextTry.clear();
         this.misses.clear();
      }

      @Override
      public boolean onReceivePacket(Packet<?> packet) {
         if (packet instanceof ClientboundEntityEventPacket event && event.getEventId() == 18) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : event.getEntity(level);
            if (entity != null) {
               this.loveUntil.put(entity.getId(), level.getGameTime() + 600L);
               this.misses.remove(entity.getId());
            }
         }

         return false;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (WorldModules.ready(client)) {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            long now = level.getGameTime();
            double reach = this.range.get();
            Set<EntityType<?>> allowed = this.animals.entityTypes();
            Animal best = null;
            double bestDistance = Double.MAX_VALUE;

            for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(reach + 1.0))) {
               int id = animal.getId();
               if (animal.isAlive()
                  && this.loveUntil.get(id) <= now
                  && this.nextTry.get(id) <= now
                  && (!this.age.is("Adults") || !animal.isBaby())
                  && (!this.age.is("Babies") || animal.isBaby())
                  && (this.animals.isEmpty() || allowed.contains(animal.getType()))) {
                  double distance = animal.getBoundingBox().distanceToSqr(player.getEyePosition());
                  if (!(distance > reach * reach) && !(distance >= bestDistance) && BlockPlacer.pickSlot(player, animal::isFood) >= 0) {
                     best = animal;
                     bestDistance = distance;
                  }
               }
            }

            if (best == null) {
               this.cooldown = 5;
            } else {
               WorldModules.useOnEntity(client, player, best, best::isFood, this.swing.get());
               int id = best.getId();
               int tries = this.misses.get(id);
               this.nextTry.put(id, now + Math.min(6000L, 100L << Math.min(tries, 6)));
               this.misses.put(id, tries + 1);
               this.cooldown = this.delay.intValue();
               WorldModules.trim(this.loveUntil);
               WorldModules.trim(this.nextTry);
               if (this.misses.size() > 512) {
                  this.misses.clear();
               }
            }
         }
      }
   }

   public static final class AutoFarm extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far from you crops are worked", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between bursts", 2.0, 0.0, 10.0, 1.0));
      private final NumberSetting perTick = this.addSetting(new NumberSetting("Blocks Per Tick", "Most crops handled in one burst", 2.0, 1.0, 8.0, 1.0));
      private final BooleanSetting crops = this.addSetting(new BooleanSetting("Crops", "Wheat, carrots, potatoes, beetroot and cocoa", true));
      private final BooleanSetting wart = this.addSetting(new BooleanSetting("Nether Wart", "Harvest grown nether wart", true));
      private final BooleanSetting fruit = this.addSetting(new BooleanSetting("Melons And Pumpkins", "Only fruit that grew next to a stem", true));
      private final BooleanSetting stalks = this.addSetting(new BooleanSetting("Sugar Cane And Cactus", "Cut everything above the bottom block", true));
      private final BooleanSetting replant = this.addSetting(new BooleanSetting("Replant", "Plant the same seed again after harvesting", true));
      private final BooleanSetting boneMeal = this.addSetting(
         new BooleanSetting("Bone Meal", "Use bone meal from your hotbar on crops that are still growing", false)
      );
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand while working", true));
      private final Map<BlockPos, Item> seeds = new LinkedHashMap<>();
      private BlockPos current;
      private int stuck;
      private int cooldown;

      public AutoFarm() {
         super("Auto Farm", "Harvest ripe crops around you, replant them and optionally bone meal the rest.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         Minecraft client = Minecraft.getInstance();
         if (this.current != null && client.gameMode != null) {
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
         if (WorldModules.ready(client)) {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            double reach = this.range.get();
            Vec3 eye = player.getEyePosition();
            boolean creative = player.isCreative();
            if (this.current != null) {
               BlockState state = level.getBlockState(this.current);
               if (!state.isAir() && ++this.stuck <= 80 && !(eye.distanceToSqr(Vec3.atCenterOf(this.current)) > (reach + 1.0) * (reach + 1.0))) {
                  client.gameMode.continueDestroyBlock(this.current, Direction.UP);
                  if (this.swing.get()) {
                     player.swing(InteractionHand.MAIN_HAND);
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
                  player.swing(InteractionHand.MAIN_HAND);
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
         if (slot < 0) {
            return false;
         } else {
            int previous = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(slot);
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            player.getInventory().setSelectedSlot(previous);
            return true;
         }
      }
   }

   public static final class AutoNametag extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away mobs are named", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between name tags", 6.0, 0.0, 40.0, 1.0));
      private final ListSetting entities = this.addSetting(
         new ListSetting(
            "Entities",
            "Which mobs get a name tag",
            ListSetting.Kind.ENTITY,
            "minecraft:wolf",
            "minecraft:cat",
            "minecraft:horse",
            "minecraft:donkey",
            "minecraft:mule",
            "minecraft:llama",
            "minecraft:parrot",
            "minecraft:axolotl",
            "minecraft:fox",
            "minecraft:allay",
            "minecraft:camel",
            "minecraft:villager",
            "minecraft:iron_golem"
         )
      );
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when naming", true));
      private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
      private int cooldown;
      private boolean warned;

      public AutoNametag() {
         super("Auto Nametag", "Name every nearby unnamed mob using a renamed name tag from your hotbar.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.nextTry.clear();
         this.warned = false;
      }

      private static boolean namedTag(ItemStack stack) {
         return stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (WorldModules.ready(client)) {
            LocalPlayer player = client.player;
            if (BlockPlacer.pickSlot(player, WorldModules.AutoNametag::namedTag) < 0) {
               if (!this.warned) {
                  this.warned = true;
                  CommandManager.feedback("§bAuto Nametag §7needs a name tag you renamed in an anvil on your hotbar.");
               }

               this.cooldown = 20;
            } else {
               this.warned = false;
               ClientLevel level = client.level;
               long now = level.getGameTime();
               double reach = this.range.get();
               Set<EntityType<?>> allowed = this.entities.entityTypes();
               LivingEntity best = null;
               double bestDistance = Double.MAX_VALUE;

               for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0))) {
                  if (candidate != player
                     && !(candidate instanceof Player)
                     && candidate.isAlive()
                     && !candidate.hasCustomName()
                     && this.nextTry.get(candidate.getId()) <= now
                     && allowed.contains(candidate.getType())) {
                     double distance = candidate.getBoundingBox().distanceToSqr(player.getEyePosition());
                     if (distance <= reach * reach && distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                     }
                  }
               }

               if (best == null) {
                  this.cooldown = 10;
               } else {
                  WorldModules.useOnEntity(client, player, best, WorldModules.AutoNametag::namedTag, this.swing.get());
                  this.nextTry.put(best.getId(), now + 60L);
                  this.cooldown = this.delay.intValue();
                  WorldModules.trim(this.nextTry);
               }
            }
         }
      }
   }

   public static final class AutoShearer extends Module {
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away animals are sheared", 4.5, 1.0, 6.0, 0.5));
      private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between shears", 6.0, 0.0, 40.0, 1.0));
      private final BooleanSetting mooshrooms = this.addSetting(new BooleanSetting("Mooshrooms", "Also shear mooshrooms (this turns them into cows)", false));
      private final BooleanSetting preventBreaking = this.addSetting(
         new BooleanSetting("Prevent Breaking", "Stop before the shears are one use from breaking", true)
      );
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when shearing", true));
      private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
      private int cooldown;

      public AutoShearer() {
         super("Auto Shearer", "Shear grown sheep with the shears in your hotbar.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         this.nextTry.clear();
      }

      private boolean usableShears(ItemStack stack) {
         return !stack.is(Items.SHEARS) ? false : !this.preventBreaking.get() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > 1;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (this.cooldown > 0) {
            this.cooldown--;
         } else if (WorldModules.ready(client)) {
            LocalPlayer player = client.player;
            if (BlockPlacer.pickSlot(player, this::usableShears) < 0) {
               this.cooldown = 20;
            } else {
               ClientLevel level = client.level;
               long now = level.getGameTime();
               double reach = this.range.get();
               LivingEntity best = null;
               double bestDistance = Double.MAX_VALUE;

               for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach + 1.0))) {
                  boolean shearable = candidate instanceof Sheep || this.mooshrooms.get() && candidate instanceof MushroomCow;
                  if (shearable && candidate instanceof Shearable target && target.readyForShearing() && this.nextTry.get(candidate.getId()) <= now) {
                     double distance = candidate.getBoundingBox().distanceToSqr(player.getEyePosition());
                     if (distance <= reach * reach && distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                     }
                  }
               }

               if (best == null) {
                  this.cooldown = 8;
               } else {
                  WorldModules.useOnEntity(client, player, best, this::usableShears, this.swing.get());
                  this.nextTry.put(best.getId(), now + 40L);
                  this.cooldown = this.delay.intValue();
                  WorldModules.trim(this.nextTry);
               }
            }
         }
      }
   }

   public static final class EndermanLook extends Module {
      private final ModeSetting mode = this.addSetting(
         new ModeSetting("Mode", "Look Away: never meet their eyes. Look At: stare at the closest one", "Look Away", "Look Away", "Look At")
      );
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far away Endermen matter", 32.0, 4.0, 64.0, 1.0));

      public EndermanLook() {
         super("Enderman Look", "Look away from Endermen so they stay calm, or stare at them on purpose.", Category.WORLD);
      }

      @Override
      public String info() {
         return this.mode.get();
      }

      private static boolean lookingAt(LocalPlayer player, EnderMan enderman, double margin) {
         Vec3 look = player.getViewVector(1.0F).normalize();
         Vec3 toward = new Vec3(enderman.getX() - player.getX(), enderman.getEyeY() - player.getEyeY(), enderman.getZ() - player.getZ());
         double distance = toward.length();
         return distance < 1.0E-4 ? true : look.dot(toward.scale(1.0 / distance)) > 1.0 - margin / distance && player.hasLineOfSight(enderman);
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldModules.ready(client)) {
            LocalPlayer player = client.player;
            boolean away = this.mode.is("Look Away");
            if (!away || !player.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN)) {
               EnderMan nearest = null;
               double nearestDistance = Double.MAX_VALUE;

               for (EnderMan enderman : client.level.getEntitiesOfClass(EnderMan.class, player.getBoundingBox().inflate(this.range.get()))) {
                  if (enderman.isAlive()) {
                     if (away) {
                        if (lookingAt(player, enderman, 0.12)) {
                           player.setXRot(90.0F);
                           return;
                        }
                     } else {
                        double distance = enderman.distanceToSqr(player);
                        if (distance < nearestDistance && player.hasLineOfSight(enderman)) {
                           nearest = enderman;
                           nearestDistance = distance;
                        }
                     }
                  }
               }

               if (nearest != null) {
                  float[] rotation = Rotations.face(player, nearest);
                  player.setYRot(rotation[0]);
                  player.setXRot(rotation[1]);
               }
            }
         }
      }
   }

   public static final class LiquidFiller extends Module {
      private final PlaceSettings placing = new PlaceSettings(this, 1, 2, 4.5);
      private final ModeSetting liquid = this.addSetting(new ModeSetting("Liquid", "Which liquid gets covered", "Both", "Both", "Water", "Lava"));
      private final BooleanSetting sourcesOnly = this.addSetting(
         new BooleanSetting("Only Sources", "Skip flowing liquid; it dries up once its source is covered", true)
      );
      private final ListSetting blocks = this.addSetting(
         new ListSetting(
            "Blocks",
            "Blocks used to cover the liquid (leave empty to use any solid block)",
            ListSetting.Kind.BLOCK,
            "minecraft:cobblestone",
            "minecraft:dirt",
            "minecraft:netherrack",
            "minecraft:stone",
            "minecraft:andesite",
            "minecraft:diorite",
            "minecraft:granite",
            "minecraft:cobbled_deepslate"
         )
      );

      public LiquidFiller() {
         super("Liquid Filler", "Cover water and lava around you with blocks from your hotbar.", Category.WORLD);
      }

      @Override
      public void onEnable() {
         this.placing.reset();
      }

      private boolean wanted(FluidState fluid) {
         if (!fluid.isEmpty() && (!this.sourcesOnly.get() || fluid.isSource())) {
            String var2 = this.liquid.get();

            return switch (var2) {
               case "Water" -> fluid.is(FluidTags.WATER);
               case "Lava" -> fluid.is(FluidTags.LAVA);
               default -> true;
            };
         } else {
            return false;
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (WorldModules.ready(client) && this.placing.ready()) {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            double reach = this.placing.range.get();
            int radius = (int)Math.ceil(reach);
            Vec3 eye = player.getEyePosition();
            BlockPos origin = BlockPos.containing(eye);
            boolean air = this.placing.airPlace.get();
            MutableBlockPos cursor = new MutableBlockPos();
            List<BlockPos> targets = new ArrayList<>();

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (this.wanted(level.getFluidState(cursor))
                        && !(eye.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5) > reach * reach)) {
                        BlockPos pos = cursor.immutable();
                        if (air || BlockPlacer.findPlacement(level, pos, false) != null) {
                           targets.add(pos);
                        }
                     }
                  }
               }
            }

            if (targets.isEmpty()) {
               this.placing.backoff(8);
            } else {
               targets.sort(Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx))));
               Set<Block> allowed = this.blocks.blocks();
               Predicate<ItemStack> filter = allowed.isEmpty()
                  ? BlockPlacer::solidFiller
                  : stack -> stack.getItem() instanceof BlockItem item && allowed.contains(item.getBlock());
               if (this.placing.place(client, targets, filter) == 0) {
                  this.placing.backoff(6);
               }
            }
         }
      }
   }

   public static final class SpawnProofer extends Module {
      private final PlaceSettings placing = new PlaceSettings(this, 3, 1, 4.0);
      private final NumberSetting maxLight = this.addSetting(
         new NumberSetting("Max Light", "Floor with at most this much block light counts as spawnable", 0.0, 0.0, 14.0, 1.0)
      );
      private final ListSetting blocks = this.addSetting(
         new ListSetting(
            "Blocks", "What goes on the floor", ListSetting.Kind.BLOCK, "minecraft:torch", "minecraft:stone_button", "minecraft:stone_pressure_plate"
         )
      );
      private final ArrayDeque<BlockPos> recent = new ArrayDeque<>();
      private final ArrayDeque<Integer> recentTick = new ArrayDeque<>();
      private int tick;

      public SpawnProofer() {
         super("Spawn Proofer", "Cover dark floor around you with torches or buttons so monsters cannot spawn.", Category.WORLD);
      }

      @Override
      public void onEnable() {
         this.placing.reset();
         this.recent.clear();
         this.recentTick.clear();
      }

      private boolean nearRecent(BlockPos pos) {
         for (BlockPos earlier : this.recent) {
            if (earlier.distSqr(pos) <= 9.0) {
               return true;
            }
         }

         return false;
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         this.tick++;

         while (!this.recentTick.isEmpty() && this.tick - this.recentTick.peekFirst() > 15) {
            this.recentTick.pollFirst();
            this.recent.pollFirst();
         }

         if (WorldModules.ready(client) && this.placing.ready()) {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            double reach = this.placing.range.get();
            int radius = (int)Math.ceil(reach);
            Vec3 eye = player.getEyePosition();
            BlockPos origin = player.blockPosition();
            int light = this.maxLight.intValue();
            MutableBlockPos cursor = new MutableBlockPos();
            MutableBlockPos floor = new MutableBlockPos();
            MutableBlockPos head = new MutableBlockPos();
            List<BlockPos> targets = new ArrayList<>();

            for (int x = -radius; x <= radius; x++) {
               for (int y = -radius; y <= radius; y++) {
                  for (int z = -radius; z <= radius; z++) {
                     cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                     if (level.getBlockState(cursor).isAir()
                        && !(eye.distanceToSqr(cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5) > reach * reach)) {
                        floor.set(cursor).move(Direction.DOWN);
                        if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                           head.set(cursor).move(Direction.UP);
                           if (level.getBlockState(head).getCollisionShape(level, head).isEmpty() && level.getBrightness(LightLayer.BLOCK, cursor) <= light) {
                              BlockPos pos = cursor.immutable();
                              if (!this.nearRecent(pos)) {
                                 targets.add(pos);
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (targets.isEmpty()) {
               this.placing.backoff(10);
            } else {
               targets.sort(Comparator.comparingDouble(posx -> eye.distanceToSqr(Vec3.atCenterOf(posx))));
               Set<Block> allowed = this.blocks.blocks();
               Predicate<ItemStack> filter = stack -> stack.getItem() instanceof BlockItem item && allowed.contains(item.getBlock());
               int placed = this.placing.place(client, targets, filter, false, posx -> {
                  this.recent.addLast(posx);
                  this.recentTick.addLast(this.tick);
               });
               if (placed == 0) {
                  this.placing.backoff(6);
               }
            }
         }
      }
   }

   public static final class VeinMiner extends Module {
      private final ModeSetting targets = this.addSetting(
         new ModeSetting("Targets", "Which blocks count as a vein", "Ores", "Ores", "Logs", "Ores And Logs", "Same Block")
      );
      private final NumberSetting maxBlocks = this.addSetting(new NumberSetting("Max Blocks", "Largest vein that is followed", 32.0, 2.0, 128.0, 1.0));
      private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block that is mined", 4.5, 2.0, 6.0, 0.5));
      private final NumberSetting perTick = this.addSetting(new NumberSetting("Blocks Per Tick", "Creative only: blocks broken each tick", 4.0, 1.0, 16.0, 1.0));
      private final BooleanSetting diagonals = this.addSetting(new BooleanSetting("Diagonals", "Follow veins that only touch at corners", true));
      private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand while mining", true));
      private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      private BlockPos watchedPos;
      private Object watchedKey;
      private BlockPos current;
      private int stuck;

      public VeinMiner() {
         super("Vein Miner", "Mine a whole ore vein or tree after breaking one block of it.", Category.WORLD);
      }

      @Override
      public void onDisable() {
         Minecraft client = Minecraft.getInstance();
         if (this.current != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }

         this.current = null;
         this.watchedPos = null;
         this.watchedKey = null;
         this.queue.clear();
      }

      private static Object oreKey(BlockState state) {
         if (state.is(BlockTags.IRON_ORES)) {
            return "iron";
         } else if (state.is(BlockTags.COPPER_ORES)) {
            return "copper";
         } else if (state.is(BlockTags.GOLD_ORES)) {
            return "gold";
         } else if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return "coal";
         } else if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return "redstone";
         } else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            return "lapis";
         } else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return "diamond";
         } else if (!state.is(Blocks.EMERALD_ORE) && !state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return !state.is(Blocks.NETHER_QUARTZ_ORE) && !state.is(Blocks.NETHER_GOLD_ORE) && !state.is(Blocks.ANCIENT_DEBRIS) ? null : state.getBlock();
         } else {
            return "emerald";
         }
      }

      private Object keyOf(BlockState state) {
         if (state.isAir()) {
            return null;
         } else {
            String mode = this.targets.get();
            if (mode.equals("Same Block")) {
               return state.getBlock();
            } else {
               if (mode.contains("Ores")) {
                  Object ore = oreKey(state);
                  if (ore != null) {
                     return ore;
                  }
               }

               return mode.contains("Logs") && state.is(BlockTags.LOGS) ? state.getBlock() : null;
            }
         }
      }

      @Override
      public void onTick() {
         Minecraft client = Minecraft.getInstance();
         if (!WorldModules.ready(client)) {
            this.queue.clear();
            this.current = null;
            this.watchedPos = null;
         } else {
            LocalPlayer player = client.player;
            ClientLevel level = client.level;
            boolean attacking = client.options.keyAttack.isDown();
            boolean justBroke = attacking || player.swinging;
            if (this.watchedPos != null && justBroke && this.queue.isEmpty() && this.current == null && level.getBlockState(this.watchedPos).isAir()) {
               this.follow(level, player, this.watchedPos, this.watchedKey);
            }

            this.watchedPos = null;
            this.watchedKey = null;
            if (client.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
               Object key = this.keyOf(level.getBlockState(hit.getBlockPos()));
               if (key != null) {
                  this.watchedPos = hit.getBlockPos();
                  this.watchedKey = key;
               }
            }

            this.mine(client, player, level, attacking);
         }
      }

      private void follow(ClientLevel level, LocalPlayer player, BlockPos origin, Object key) {
         double reach = this.range.get();
         Vec3 eye = player.getEyePosition();
         int limit = this.maxBlocks.intValue();
         boolean corners = this.diagonals.get();
         LongOpenHashSet seen = new LongOpenHashSet();
         ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
         List<BlockPos> vein = new ArrayList<>();
         seen.add(origin.asLong());
         frontier.add(origin);

         while (!frontier.isEmpty() && vein.size() < limit) {
            BlockPos at = frontier.poll();

            for (int dx = -1; dx <= 1 && vein.size() < limit; dx++) {
               for (int dy = -1; dy <= 1 && vein.size() < limit; dy++) {
                  for (int dz = -1; dz <= 1 && vein.size() < limit; dz++) {
                     if ((dx != 0 || dy != 0 || dz != 0) && (corners || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1)) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (seen.add(next.asLong())
                           && key.equals(this.keyOf(level.getBlockState(next)))
                           && !(eye.distanceToSqr(Vec3.atCenterOf(next)) > reach * reach)) {
                           vein.add(next);
                           frontier.add(next);
                        }
                     }
                  }
               }
            }
         }

         vein.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
         this.queue.addAll(vein);
      }

      private void mine(Minecraft client, LocalPlayer player, ClientLevel level, boolean attacking) {
         boolean creative = player.isCreative();
         double reach = this.range.get() + 1.0;
         Vec3 eye = player.getEyePosition();
         if (this.current != null) {
            if (!level.getBlockState(this.current).isAir() && ++this.stuck <= 80 && !(eye.distanceToSqr(Vec3.atCenterOf(this.current)) > reach * reach)) {
               if (!attacking) {
                  client.gameMode.continueDestroyBlock(this.current, WorldModules.faceToward(player, this.current));
                  if (this.swing.get()) {
                     player.swing(InteractionHand.MAIN_HAND);
                  }

                  return;
               }

               return;
            }

            this.current = null;
            this.stuck = 0;
         }

         if (!this.queue.isEmpty() && (creative || !attacking)) {
            int budget = creative ? this.perTick.intValue() : 1;

            while (budget-- > 0 && !this.queue.isEmpty()) {
               BlockPos pos = this.queue.poll();
               BlockState state = level.getBlockState(pos);
               if (!state.isAir() && !(state.getDestroySpeed(level, pos) < 0.0F) && !(eye.distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach)) {
                  client.gameMode.startDestroyBlock(pos, WorldModules.faceToward(player, pos));
                  if (!creative && !level.getBlockState(pos).isAir()) {
                     this.current = pos;
                     this.stuck = 0;
                     break;
                  }
               }
            }

            if (this.swing.get()) {
               player.swing(InteractionHand.MAIN_HAND);
            }
         }
      }
   }
}
