package gemini.module.modules.world;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.util.BlockPlacer;
import gemini.util.PlaceSettings;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public final class LiquidFiller extends Module {
   private final PlaceSettings placing = new PlaceSettings(this, 1, 2, 4.5);
   private final ModeSetting liquid = this.addSetting(new ModeSetting("Liquid", "Which liquid gets covered", "Both", "Both", "Water", "Lava"));
   private final BooleanSetting sourcesOnly = this.addSetting(
      new BooleanSetting("Only Sources", "Skip flowing liquid", true)
   );
   private final ListSetting blocks = this.addSetting(
      new ListSetting(
         "Blocks",
         "Blocks used to cover the liquid",
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
      super("Liquid Filler", "Fill nearby water and lava source blocks automatically.", Category.WORLD);
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
      if (WorldHelpers.readyNoScreen(client) && this.placing.ready()) {
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
