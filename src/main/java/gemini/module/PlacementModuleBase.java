package gemini.module;

import gemini.util.ClientState;
import gemini.util.PlaceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * A module that keeps a set of block positions filled: Surround, SelfTrap, BlockIn, HoleFiller, WebAura,
 * BedDefender. They all ran the same tick: wait until the player can act and no screen is open, respect the
 * placement delay, work out the positions, hand them to {@link PlaceSettings}. That loop lives here; a module
 * only says which positions it wants and which hotbar blocks may fill them.
 *
 * <p>The placement settings (delay, blocks per tick, range, swing, air place, silent aim) are added first, exactly
 * as when each module created its own {@link PlaceSettings}, so saved configs keep working unchanged.
 */
public abstract class PlacementModuleBase extends Module {
   protected final PlaceSettings placer;
   private final List<BlockPos> targets = new ArrayList<>();

   protected PlacementModuleBase(String name, String description, Category category, int defaultDelay, int defaultPerTick, double defaultRange) {
      super(name, description, category);
      this.placer = new PlaceSettings(this, defaultDelay, defaultPerTick, defaultRange);
   }

   @Override
   public final void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.readyNoScreen(client)) {
         return;
      }

      LocalPlayer player = client.player;
      if (!this.shouldPlace(client, player) || !this.placer.ready()) {
         return;
      }

      this.targets.clear();
      this.collectTargets(client, player, this.targets);
      if (!this.targets.isEmpty()) {
         this.placer.place(client, this.targets, this.blocks(), this.avoidEntities());
      }
   }

   /** A chance to skip this tick (or switch the module off) before the placement delay is consulted. */
   protected boolean shouldPlace(Minecraft client, LocalPlayer player) {
      return true;
   }

   /** Adds the positions to fill, most important first; only as many as "Blocks Per Tick" allows are placed. */
   protected abstract void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets);

   /** Which hotbar stacks may be placed. */
   protected abstract Predicate<ItemStack> blocks();

   /** False for blocks that are meant to go where an entity stands (cobwebs at an enemy's feet). */
   protected boolean avoidEntities() {
      return true;
   }
}
