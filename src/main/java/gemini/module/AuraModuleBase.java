package gemini.module;

import gemini.module.setting.NumberSetting;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.Swing;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/**
 * An "aura" that uses a hotbar item on the nearest suitable mob around the player: Auto Shearer, Auto Nametag,
 * Auto Breed. They shared one loop, copied three times: count down a cooldown, check the player can act, find the
 * closest candidate in reach that is not waiting for a retry, use the item on it with the hotbar slot swapped in
 * and back, and remember not to try the same mob again for a while.
 *
 * <p>That loop is here. A module supplies the type of mob, what makes one a candidate, which item to use on it,
 * and how long to leave it alone afterwards. "Range" and "Delay" are added first, with the wording each module
 * had, so saved configs keep working unchanged.
 *
 * @param <T> the kind of mob this aura acts on
 */
public abstract class AuraModuleBase<T extends LivingEntity> extends Module {
   /** Retry bookkeeping is dropped beyond this many mobs; entity ids are never reused, so it would only grow. */
   private static final int MAX_TRACKED = 512;
   protected final NumberSetting range;
   protected final NumberSetting delay;
   private final Class<T> type;
   private final Int2LongOpenHashMap nextTry = new Int2LongOpenHashMap();
   private int cooldown;

   protected AuraModuleBase(
      String name, String description, Category category, Class<T> type, String rangeDescription, String delayDescription, double defaultDelay, double maxDelay
   ) {
      super(name, description, category);
      this.type = type;
      this.range = this.addSetting(new NumberSetting("Range", rangeDescription, 4.5, 1.0, 6.0, 0.5));
      this.delay = this.addSetting(new NumberSetting("Delay", delayDescription, defaultDelay, 0.0, maxDelay, 1.0));
   }

   @Override
   public void onDisable() {
      this.nextTry.clear();
      this.cooldown = 0;
   }

   @Override
   public void onWorldChange() {
      this.nextTry.clear();
   }

   @Override
   public final void onTick() {
      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      Minecraft client = Minecraft.getInstance();
      if (!ClientState.readyNoScreen(client)) {
         return;
      }

      LocalPlayer player = client.player;
      if (!this.prepare(client, player)) {
         this.cooldown = this.unpreparedTicks();
         return;
      }

      ClientLevel level = client.level;
      long now = level.getGameTime();
      double reach = this.range.get();
      double reachSq = reach * reach;
      T best = null;
      double bestDistance = Double.MAX_VALUE;
      for (T candidate : level.getEntitiesOfClass(this.type, player.getBoundingBox().inflate(reach + 1.0))) {
         if (candidate != player && candidate.isAlive() && this.nextTry.get(candidate.getId()) <= now && this.accepts(player, candidate, now)) {
            double distance = candidate.getBoundingBox().distanceToSqr(player.getEyePosition());
            if (distance <= reachSq && distance < bestDistance && Inventories.findHotbar(player, this.itemFor(candidate)) >= 0) {
               best = candidate;
               bestDistance = distance;
            }
         }
      }

      if (best == null) {
         this.cooldown = this.idleTicks();
         return;
      }

      int outcome = useOn(client, player, best, this.itemFor(best), this.swings());
      if (outcome == NOT_USED) {
         // Another module holds the hand this tick (or the item just left the hotbar): try again shortly without
         // counting it against the mob, which was never touched.
         this.cooldown = 2;
         return;
      }

      if (this.nextTry.size() >= MAX_TRACKED) {
         this.nextTry.clear();
      }

      this.nextTry.put(best.getId(), now + this.retryTicks(best));
      this.cooldown = this.delay.intValue();
      this.afterUse(best, now);
   }

   /** {@link #useOn} could not act: no matching hotbar item, or a more important module holds the main hand. */
   public static final int NOT_USED = -1;
   /** {@link #useOn} interacted, but the game did not take the item (the mob refused it). */
   public static final int REFUSED = 0;
   /** {@link #useOn} interacted and the item was used. */
   public static final int USED = 1;

   /**
    * Uses the first matching hotbar item on the entity with the main hand, swapping the slot in and back (the
    * selected slot is restored even if the interaction throws). Returns {@link #NOT_USED}, {@link #REFUSED} or
    * {@link #USED}.
    */
   public static int useOn(Minecraft client, LocalPlayer player, LivingEntity target, Predicate<ItemStack> item, boolean swing) {
      int slot = BlockPlacer.pickSlot(player, item);
      if (slot < 0 || client.gameMode == null || !Hands.claimMainHand()) {
         return NOT_USED;
      }

      int[] outcome = {NOT_USED};
      Inventories.withHotbarSlot(player, slot, () -> {
         InteractionResult result = client.gameMode.interact(player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
         if (swing) {
            Swing.interact(player, InteractionHand.MAIN_HAND);
         }

         outcome[0] = result.consumesAction() ? USED : REFUSED;
      });
      return outcome[0];
   }

   /** Checked before looking for a mob; return false to wait (no tool on the hotbar, for example). */
   protected boolean prepare(Minecraft client, LocalPlayer player) {
      return true;
   }

   /** Ticks to wait after {@link #prepare} said no. */
   protected int unpreparedTicks() {
      return 20;
   }

   /** Whether this mob should be acted on. It is already known to be alive, in the search box and not on retry hold. */
   protected abstract boolean accepts(LocalPlayer player, T candidate, long gameTime);

   /** The hotbar item to use on the mob. */
   protected abstract Predicate<ItemStack> itemFor(T target);

   /** Ticks before the same mob is tried again. */
   protected abstract long retryTicks(T target);

   /** Ticks to wait when no mob qualified. */
   protected abstract int idleTicks();

   protected abstract boolean swings();

   /** Called after the item was offered to the mob (taken or refused), for bookkeeping of the module's own. */
   protected void afterUse(T target, long gameTime) {
   }
}
