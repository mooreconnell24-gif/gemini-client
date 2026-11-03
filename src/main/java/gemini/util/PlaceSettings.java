package gemini.util;

import gemini.event.ActionPriority;
import gemini.event.EventBus;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class PlaceSettings {
   public final NumberSetting delay;
   public final NumberSetting perTick;
   public final NumberSetting range;
   public final BooleanSetting swing;
   public final BooleanSetting airPlace;
   public final BooleanSetting rotate;
   private int cooldown;

   public PlaceSettings(Module module, int defaultDelay, int defaultPerTick, double defaultRange) {
      this.delay = module.addSetting(new NumberSetting("Place Delay", "Ticks between placement bursts", defaultDelay, 0.0, 10.0, 1.0));
      this.perTick = module.addSetting(new NumberSetting("Blocks Per Tick", "Most blocks placed in one burst", defaultPerTick, 1.0, 8.0, 1.0));
      this.range = module.addSetting(new NumberSetting("Place Range", "Farthest block you will place", defaultRange, 1.5, 6.0, 0.1));
      this.swing = module.addSetting(new BooleanSetting("Swing", "Swing your hand when placing", true));
      this.airPlace = module.addSetting(new BooleanSetting("Air Place", "Place against empty space when no neighbouring", false));
      this.rotate = module.addSetting(new BooleanSetting("Silent Aim", "Face each block in packets only while placing", true));
   }

   /** The running module's priority, so a survival module's placements outrank a builder's. */
   private static ActionPriority priority() {
      ActionPriority current = EventBus.currentPriority();
      return current != null ? current : ActionPriority.WORLD_UTILITY;
   }

   public boolean ready() {
      if (this.cooldown > 0) {
         this.cooldown--;
         return false;
      } else {
         return true;
      }
   }

   public void reset() {
      this.cooldown = 0;
   }

   public void backoff(int ticks) {
      this.cooldown = Math.max(this.cooldown, ticks);
   }

   public int place(Minecraft client, Iterable<BlockPos> targets, Predicate<ItemStack> filter, boolean checkEntities) {
      return this.place(client, targets, filter, checkEntities, null);
   }

   public int place(Minecraft client, Iterable<BlockPos> targets, Predicate<ItemStack> filter, boolean checkEntities, Consumer<BlockPos> onPlaced) {
      LocalPlayer player = client.player;
      if (player == null) {
         return 0;
      } else {
         int limit = this.perTick.intValue();
         int placed = 0;

         for (BlockPos pos : targets) {
            if (placed >= limit) {
               break;
            }

            if (BlockPlacer.inReach(player, pos, this.range.get())
               && (!this.rotate.get() || RotationManager.request(player, RotationManager.toward(player, pos), 0.0F, true, priority()))
               && BlockPlacer.place(client, pos, filter, this.swing.get(), true, this.airPlace.get(), checkEntities)) {
               placed++;
               if (onPlaced != null) {
                  onPlaced.accept(pos);
               }
            }
         }

         if (placed > 0) {
            this.cooldown = this.delay.intValue();
         }

         return placed;
      }
   }

   public int place(Minecraft client, Iterable<BlockPos> targets, Predicate<ItemStack> filter) {
      return this.place(client, targets, filter, true);
   }
}
