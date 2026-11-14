package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.PlaceSettings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class SpawnProofer extends Module {
   private final PlaceSettings placing = new PlaceSettings(this, 3, 1, 4.0);
   private final NumberSetting maxLight = this.addSetting(
      new NumberSetting("Max Light", "Maximum block light level treated as monster-spawnable", 0.0, 0.0, 14.0, 1.0)
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
      super("Spawn Proofer", "Cover dark floor around you with torches or buttons.", Category.WORLD);
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

      if (ClientState.readyNoScreen(client) && this.placing.ready()) {
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

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.recent.clear();
      this.recentTick.clear();
   }
}
