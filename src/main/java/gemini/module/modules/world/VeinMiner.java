package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public final class VeinMiner extends Module {
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
      super("Vein Miner", "Mine a whole ore vein or tree after breaking one.", Category.WORLD);
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
      if (!WorldHelpers.readyNoScreen(client)) {
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
               client.gameMode.continueDestroyBlock(this.current, WorldHelpers.faceToward(player, this.current));
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
               client.gameMode.startDestroyBlock(pos, WorldHelpers.faceToward(player, pos));
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
