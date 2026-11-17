package gemini.module.modules.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.NumberSetting;
import gemini.ui.HudOverlay;
import gemini.util.BlockBreaking;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.Swing;
import gemini.util.Tools;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Restocks from a shulker box in one go: puts a shulker from the inventory down, opens it, takes what the layout
 * is short of, closes it and mines the box back up.
 *
 * <p>Kits in this client are handed out with {@code /give}, so there is no stored kit to copy. The layout here is
 * a set of targets instead: a count for each combat staple (totems, crystals, golden apples, obsidian, pearls, XP
 * bottles, food), a count for every item on the "Extra Items" list, and optionally a saved picture of the hotbar
 * that is put back slot by slot.
 *
 * <p>Every step has a time limit. Whatever goes wrong, the module closes the box if it opened one, stops mining,
 * selects the slot you had, says what happened and switches itself off.
 */
public final class AutoRegear extends Module {
   private static final Predicate<ItemStack> SHULKER = stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
   private static final String TOTEMS = "totems";
   private static final String CRYSTALS = "crystals";
   private static final String GAPPLES = "gapples";
   private static final String OBSIDIAN = "obsidian";
   private static final String PEARLS = "pearls";
   private static final String XP = "xp";
   private static final String FOOD = "food";
   /** The most container clicks one run may make; a guard on top of the per-step time limits. */
   private static final int MAX_CLICKS = 96;
   private final NumberSetting totems = this.addSetting(new NumberSetting("Totems", "Totems of undying to carry", 6.0, 0.0, 36.0, 1.0));
   private final NumberSetting crystals = this.addSetting(new NumberSetting("Crystals", "End crystals to carry", 128.0, 0.0, 640.0, 16.0));
   private final NumberSetting gapples = this.addSetting(new NumberSetting("Gapples", "Golden and enchanted golden apples to carry", 32.0, 0.0, 128.0, 4.0));
   private final NumberSetting obsidian = this.addSetting(new NumberSetting("Obsidian", "Obsidian to carry", 64.0, 0.0, 320.0, 16.0));
   private final NumberSetting pearls = this.addSetting(new NumberSetting("Pearls", "Ender pearls to carry", 16.0, 0.0, 64.0, 4.0));
   private final NumberSetting xpBottles = this.addSetting(new NumberSetting("XP Bottles", "Bottles o' enchanting to carry", 64.0, 0.0, 320.0, 16.0));
   private final NumberSetting food = this.addSetting(new NumberSetting("Food", "Other food to carry (anything that is not a golden apple)", 32.0, 0.0, 128.0, 8.0));
   private final ListSetting extraItems = this.addSetting(
      new ListSetting("Extra Items", "Other items to refill, each up to Extra Count", ListSetting.Kind.ITEM)
   );
   private final NumberSetting extraCount = this.addSetting(
      new NumberSetting("Extra Count", "How many of each extra item to carry", 64.0, 1.0, 320.0, 1.0).showIf(() -> !this.extraItems.isEmpty())
   );
   private final BooleanSetting matchHotbar = this.addSetting(
      new BooleanSetting("Match Hotbar", "Also put the saved hotbar back, slot by slot, from the box and your inventory", false)
   );
   private final BooleanSetting mineBack = this.addSetting(new BooleanSetting("Mine Back", "Break the shulker box and pick it up again afterwards", true));
   private final NumberSetting clickDelay = this.addSetting(new NumberSetting("Click Delay", "Ticks between item moves", 1.0, 0.0, 5.0, 1.0));
   private final NumberSetting range = this.addSetting(new NumberSetting("Place Range", "Farthest spot to place the box, in blocks", 3.5, 2.0, 4.5, 0.1));
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Face the box without moving your camera while placing, opening and mining it", true));
   private final BooleanSetting swing = this.addSetting(new BooleanSetting("Swing", "Swing your hand when placing and mining", true));
   /** Item id per hotbar slot, null for a slot that was empty when the picture was taken. */
   private final String[] savedHotbar = new String[9];
   private final int[] savedHotbarCounts = new int[9];
   private boolean hotbarSaved;
   private State state = State.IDLE;
   private int stateTicks;
   private int cooldown;
   private int clicks;
   private int moved;
   private BlockPos boxPos;
   private int originalSlot = -1;
   private int borrowedInventory = -1;
   private int borrowedHotbar = -1;
   private boolean openedMenu;
   private boolean mining;
   private int shulkersBefore;
   private final Set<Integer> skippedSlots = new HashSet<>();
   private final Set<Integer> triedHotbar = new HashSet<>();

   public AutoRegear() {
      super("Auto Regear", "Places a shulker box, restocks your inventory from it and picks it back up.", Category.PLAYER);
      this.addSetting(
         new ActionSetting("Save Hotbar", "Remember the hotbar as it is now for Match Hotbar", this::saveHotbar).showIf(this.matchHotbar::get)
      );
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   /** The self-test switches every module on; this one would rearrange the inventory. */
   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   public String info() {
      return this.state == State.IDLE ? null : this.state.label;
   }

   private void saveHotbar() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         HudOverlay.toast("Auto Regear", "Join a world to save your hotbar", HudOverlay.Severity.WARNING);
         return;
      }

      for (int i = 0; i < 9; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         this.savedHotbar[i] = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
         this.savedHotbarCounts[i] = stack.getCount();
      }

      this.hotbarSaved = true;
      this.save();
      HudOverlay.toast("Auto Regear", "Hotbar layout saved", HudOverlay.Severity.SUCCESS);
   }

   @Override
   public void writeExtra(JsonObject object) {
      if (!this.hotbarSaved) {
         return;
      }

      JsonArray slots = new JsonArray();
      for (int i = 0; i < 9; i++) {
         JsonObject slot = new JsonObject();
         slot.addProperty("item", this.savedHotbar[i] == null ? "" : this.savedHotbar[i]);
         slot.addProperty("count", this.savedHotbarCounts[i]);
         slots.add(slot);
      }

      object.add("hotbar", slots);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.hotbarSaved = false;
      if (!object.has("hotbar") || !object.get("hotbar").isJsonArray()) {
         return;
      }

      JsonArray slots = object.getAsJsonArray("hotbar");
      for (int i = 0; i < 9; i++) {
         this.savedHotbar[i] = null;
         this.savedHotbarCounts[i] = 0;
         if (i < slots.size()) {
            try {
               JsonElement element = slots.get(i);
               JsonObject slot = element.getAsJsonObject();
               String id = slot.get("item").getAsString();
               this.savedHotbar[i] = id.isEmpty() ? null : id;
               this.savedHotbarCounts[i] = Math.max(0, slot.get("count").getAsInt());
            } catch (RuntimeException ignored) {
               // A damaged entry leaves that slot unset; the rest of the picture is still used.
               this.savedHotbar[i] = null;
            }
         }
      }

      this.hotbarSaved = true;
   }

   @Override
   public void onEnable() {
      this.state = State.IDLE;
      this.stateTicks = 0;
      this.cooldown = 0;
      this.clicks = 0;
      this.moved = 0;
      this.boxPos = null;
      this.originalSlot = -1;
      this.borrowedInventory = -1;
      this.borrowedHotbar = -1;
      this.openedMenu = false;
      this.mining = false;
      this.skippedSlots.clear();
      this.triedHotbar.clear();
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (this.mining) {
         this.mining = false;
         BlockBreaking.release();
         if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }
      }

      if (player != null) {
         if (this.openedMenu && player.containerMenu instanceof ShulkerBoxMenu) {
            player.closeContainer();
         }

         this.returnBorrowedSlot(client, player);
         if (this.originalSlot >= 0 && this.originalSlot < 9) {
            player.getInventory().setSelectedSlot(this.originalSlot);
         }
      }

      this.openedMenu = false;
      this.originalSlot = -1;
      this.borrowedInventory = -1;
      this.borrowedHotbar = -1;
      this.boxPos = null;
      this.state = State.IDLE;
   }

   /** The box and everything remembered about it belong to the world that was left. */
   @Override
   public void onWorldChange() {
      this.mining = false;
      this.openedMenu = false;
      this.boxPos = null;
      // Slots remembered for the old player must not be swapped around in the new one's inventory.
      this.borrowedInventory = -1;
      this.borrowedHotbar = -1;
      this.originalSlot = -1;
      this.finish("Stopped: you changed worlds", HudOverlay.Severity.WARNING);
   }

   /**
    * Puts the item that made room for the shulker back into its hotbar slot, unless Match Hotbar already filled
    * that slot from the saved layout this run.
    */
   private void returnBorrowedSlot(Minecraft client, LocalPlayer player) {
      if (this.borrowedInventory >= 0
         && this.borrowedHotbar >= 0
         && !this.triedHotbar.contains(this.borrowedHotbar)
         && Inventories.canClick(player)) {
         Inventories.moveToHotbar(client, player, this.borrowedInventory, this.borrowedHotbar);
      }

      this.borrowedInventory = -1;
      this.borrowedHotbar = -1;
   }

   private void enter(State next) {
      this.state = next;
      this.stateTicks = 0;
   }

   private void finish(String message, HudOverlay.Severity severity) {
      HudOverlay.toast("Auto Regear", message, severity);
      this.setEnabled(false, false);
   }

   private void fail(String message) {
      this.finish(message, HudOverlay.Severity.ERROR);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (!ClientState.ready(client)) {
         this.fail("Stopped: you cannot act right now");
         return;
      }

      if (++this.stateTicks > this.state.timeout) {
         this.fail("Timed out while " + this.state.label.toLowerCase(java.util.Locale.ROOT));
         return;
      }

      switch (this.state) {
         case IDLE -> this.begin(client, player);
         case PLACING -> this.place(client, player);
         case WAIT_PLACED -> {
            if (this.isBox(client.level)) {
               this.enter(State.OPENING);
            }
         }
         case OPENING -> this.open(client, player);
         case WAIT_OPEN -> {
            if (player.containerMenu instanceof ShulkerBoxMenu) {
               this.openedMenu = true;
               this.cooldown = 1;
               this.enter(State.LOOTING);
            }
         }
         case LOOTING -> this.loot(client, player);
         case CLOSING -> {
            if (!(player.containerMenu instanceof ShulkerBoxMenu) && client.gui.screen() == null) {
               this.openedMenu = false;
               if (this.mineBack.get()) {
                  this.enter(State.MINING);
               } else {
                  this.done();
               }
            } else if (player.containerMenu instanceof ShulkerBoxMenu) {
               player.closeContainer();
            }
         }
         case MINING -> this.mine(client, player);
         case WAIT_PICKUP -> {
            if (this.countShulkers(player) >= this.shulkersBefore) {
               this.done();
            } else if (this.stateTicks >= State.WAIT_PICKUP.timeout) {
               this.finish("Restocked " + this.moved + " stack(s); the box was not picked up", HudOverlay.Severity.WARNING);
            }
         }
      }
   }

   private void done() {
      this.finish(this.moved > 0 ? "Restocked " + this.moved + " stack(s)" : "Nothing to restock from that box", HudOverlay.Severity.SUCCESS);
   }

   private boolean isBox(Level level) {
      return this.boxPos != null && level.getBlockState(this.boxPos).getBlock() instanceof ShulkerBoxBlock;
   }

   private int countShulkers(LocalPlayer player) {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         if (SHULKER.test(player.getInventory().getItem(i))) {
            count++;
         }
      }

      return count + (SHULKER.test(player.getOffhandItem()) ? 1 : 0);
   }

   private void begin(Minecraft client, LocalPlayer player) {
      if (client.gui.screen() != null || !Inventories.canClick(player)) {
         this.fail("Close the open screen first");
         return;
      }

      if (this.wanted(player).isEmpty() && !(this.matchHotbar.get() && this.hotbarSaved)) {
         this.fail("Nothing to refill: every count is zero");
         return;
      }

      int index = Inventories.find(player, SHULKER, false);
      if (index < 0) {
         this.fail("No shulker box in your inventory");
         return;
      }

      BlockPos spot = this.findSpot(client, player);
      if (spot == null) {
         this.fail("No free spot nearby to put the box down");
         return;
      }

      this.originalSlot = player.getInventory().getSelectedSlot();
      this.shulkersBefore = this.countShulkers(player);
      if (index >= 9) {
         int hotbar = this.spareHotbarSlot(player);
         boolean displaced = !player.getInventory().getItem(hotbar).isEmpty();
         Inventories.moveToHotbar(client, player, index, hotbar);
         if (displaced) {
            this.borrowedInventory = index;
            this.borrowedHotbar = hotbar;
         }
      }

      this.boxPos = spot;
      this.enter(State.PLACING);
   }

   /** An empty hotbar slot if there is one, otherwise the selected slot. */
   private int spareHotbarSlot(LocalPlayer player) {
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getItem(i).isEmpty()) {
            return i;
         }
      }

      return player.getInventory().getSelectedSlot();
   }

   /**
    * The nearest free block with a plain solid block under it and nothing above it. The box is then placed against
    * the floor, so it faces up and its lid has room to open.
    */
   private BlockPos findSpot(Minecraft client, LocalPlayer player) {
      Level level = client.level;
      BlockPos feet = player.blockPosition();
      Vec3 eye = player.getEyePosition();
      double reach = this.range.get();
      BlockPos best = null;
      double bestDistance = Double.MAX_VALUE;
      for (int dx = -4; dx <= 4; dx++) {
         for (int dz = -4; dz <= 4; dz++) {
            for (int dy = -2; dy <= 1; dy++) {
               if (dx == 0 && dz == 0) {
                  continue;
               }

               BlockPos pos = feet.offset(dx, dy, dz);
               double distance = Vec3.atCenterOf(pos).distanceToSqr(eye);
               if (distance >= bestDistance || !BlockPlacer.inReach(player, pos, reach)) {
                  continue;
               }

               BlockState here = level.getBlockState(pos);
               BlockState floor = level.getBlockState(pos.below());
               BlockPos above = pos.above();
               if (BlockPlacer.replaceable(level, pos)
                  && here.getFluidState().isEmpty()
                  && floor.isSolidRender()
                  && !floor.hasBlockEntity()
                  && level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                  && !BlockPlacer.occupied(level, pos)) {
                  best = pos;
                  bestDistance = distance;
               }
            }
         }
      }

      return best;
   }

   private boolean aim(LocalPlayer player) {
      return !this.rotate.get() || RotationManager.request(player, RotationManager.toward(player, this.boxPos), 0.0F, true, this.priority());
   }

   private void place(Minecraft client, LocalPlayer player) {
      if (this.isBox(client.level)) {
         this.enter(State.OPENING);
      } else if (client.gui.screen() == null
         && this.aim(player)
         && BlockPlacer.place(client, this.boxPos, SHULKER, this.swing.get(), true, false)) {
         this.enter(State.WAIT_PLACED);
      }
   }

   private void open(Minecraft client, LocalPlayer player) {
      if (!this.isBox(client.level)) {
         this.fail("The shulker box is gone");
         return;
      }

      // A sneaking player places or uses the held item instead of opening the box, so wait for the key to come up.
      if (player.isShiftKeyDown() || client.gui.screen() != null || !this.aim(player)) {
         return;
      }

      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(this.boxPos).add(0.0, 0.5, 0.0), Direction.UP, this.boxPos, false);
      client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
      if (this.swing.get()) {
         Swing.interact(player, InteractionHand.MAIN_HAND);
      }

      this.enter(State.WAIT_OPEN);
   }

   private void loot(Minecraft client, LocalPlayer player) {
      if (!(player.containerMenu instanceof ShulkerBoxMenu menu)) {
         // Closed from outside (the box broke, Escape was pressed): carry on with what was taken.
         this.openedMenu = false;
         this.enter(State.CLOSING);
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         return;
      }

      if (this.clicks < MAX_CLICKS && (this.takeNext(client, player, menu) || this.arrangeHotbar(client, player, menu))) {
         this.clicks++;
         this.stateTicks = 0;
         this.cooldown = this.clickDelay.intValue();
         return;
      }

      player.closeContainer();
      this.enter(State.CLOSING);
   }

   /** Moves the next stack the layout is short of out of the box. False when there is nothing more to take. */
   private boolean takeNext(Minecraft client, LocalPlayer player, AbstractContainerMenu menu) {
      Map<String, Integer> missing = RegearPlan.deficits(this.wanted(player), this.owned(player));
      if (missing.isEmpty()) {
         return false;
      }

      List<String> keys = new ArrayList<>(menu.slots.size());
      int[] counts = new int[menu.slots.size()];
      for (int i = 0; i < menu.slots.size(); i++) {
         Slot slot = menu.slots.get(i);
         ItemStack stack = slot.getItem();
         boolean usable = slot.container != player.getInventory()
            && !stack.isEmpty()
            && !this.skippedSlots.contains(i)
            && (player.getInventory().getFreeSlot() >= 0 || player.getInventory().getSlotWithRemainingSpace(stack) >= 0);
         keys.add(usable ? this.keyOf(stack) : null);
         counts[i] = usable ? stack.getCount() : 0;
      }

      int pick = RegearPlan.nextPick(keys, counts, missing);
      if (pick < 0) {
         return false;
      }

      int before = menu.slots.get(pick).getItem().getCount();
      client.gameMode.handleContainerInput(menu.containerId, pick, 0, ContainerInput.QUICK_MOVE, player);
      if (menu.slots.get(pick).getItem().getCount() >= before) {
         // Nothing left the slot (no room for this item): never try it again in this run.
         this.skippedSlots.add(pick);
      } else {
         this.moved++;
      }

      return true;
   }

   /** Puts one saved hotbar item back into its slot, from the box first and the inventory second. */
   private boolean arrangeHotbar(Minecraft client, LocalPlayer player, AbstractContainerMenu menu) {
      if (!this.matchHotbar.get() || !this.hotbarSaved) {
         return false;
      }

      for (int hotbar = 0; hotbar < 9; hotbar++) {
         String id = this.savedHotbar[hotbar];
         if (id == null || this.triedHotbar.contains(hotbar) || id.equals(idOf(player.getInventory().getItem(hotbar)))) {
            continue;
         }

         int source = this.findSource(player, menu, id);
         if (source >= 0) {
            this.triedHotbar.add(hotbar);
            client.gameMode.handleContainerInput(menu.containerId, source, hotbar, ContainerInput.SWAP, player);
            if (menu.slots.get(source).container != player.getInventory()) {
               this.moved++;
            }

            return true;
         }
      }

      return false;
   }

   /**
    * The menu slot to fetch a saved hotbar item from: the box, then the main inventory, then a hotbar slot that is
    * not itself meant to hold that item. -1 when there is none.
    */
   private int findSource(LocalPlayer player, AbstractContainerMenu menu, String id) {
      int fromInventory = -1;
      int fromHotbar = -1;
      for (int i = 0; i < menu.slots.size(); i++) {
         Slot slot = menu.slots.get(i);
         if (!id.equals(idOf(slot.getItem()))) {
            continue;
         }

         if (slot.container != player.getInventory()) {
            return i;
         }

         int index = slot.getContainerSlot();
         if (index >= 9 && index < 36) {
            if (fromInventory < 0) {
               fromInventory = i;
            }
         } else if (index >= 0 && index < 9 && !id.equals(this.savedHotbar[index]) && fromHotbar < 0) {
            fromHotbar = i;
         }
      }

      return fromInventory >= 0 ? fromInventory : fromHotbar;
   }

   private void mine(Minecraft client, LocalPlayer player) {
      if (!this.isBox(client.level)) {
         if (this.mining) {
            this.mining = false;
            BlockBreaking.release();
            client.gameMode.stopDestroyBlock();
         }

         this.enter(State.WAIT_PICKUP);
         return;
      }

      if (client.gui.screen() != null || !this.aim(player)) {
         return;
      }

      BlockState box = client.level.getBlockState(this.boxPos);
      int tool = Tools.bestSlot(player, box, true);
      int held = player.getInventory().getSelectedSlot();
      Runnable hit = () -> {
         this.mining = true;
         BlockBreaking.keep();
         if (client.gameMode.continueDestroyBlock(this.boxPos, Direction.UP) && this.swing.get()) {
            Swing.attack(player, InteractionHand.MAIN_HAND);
         }
      };
      if (tool != held && tool >= 0 && Hands.claimMainHand()) {
         Inventories.withHotbarSlot(player, tool, hit);
      } else {
         hit.run();
      }
   }

   /** The layout as targets per key, in a fixed order: staples, extra items, then the saved hotbar. */
   private Map<String, Integer> wanted(LocalPlayer player) {
      Map<String, Integer> targets = new LinkedHashMap<>();
      put(targets, TOTEMS, this.totems.intValue());
      put(targets, CRYSTALS, this.crystals.intValue());
      put(targets, GAPPLES, this.gapples.intValue());
      put(targets, OBSIDIAN, this.obsidian.intValue());
      put(targets, PEARLS, this.pearls.intValue());
      put(targets, XP, this.xpBottles.intValue());
      put(targets, FOOD, this.food.intValue());
      for (String id : this.extraItems.get()) {
         put(targets, id, this.extraCount.intValue());
      }

      if (this.matchHotbar.get() && this.hotbarSaved) {
         Map<String, Integer> hotbar = new LinkedHashMap<>();
         for (int i = 0; i < 9; i++) {
            if (this.savedHotbar[i] != null) {
               hotbar.merge(this.savedHotbar[i], Math.max(1, this.savedHotbarCounts[i]), Integer::sum);
            }
         }

         for (Map.Entry<String, Integer> entry : hotbar.entrySet()) {
            String key = this.keyOfId(entry.getKey());
            // An item a staple or the extra list already covers keeps that target; the rest get the saved amount.
            if (key == null) {
               targets.put(entry.getKey(), entry.getValue());
            }
         }
      }

      return targets;
   }

   private static void put(Map<String, Integer> targets, String key, int count) {
      if (count > 0) {
         targets.put(key, count);
      }
   }

   private Map<String, Integer> owned(LocalPlayer player) {
      Map<String, Integer> have = new LinkedHashMap<>();
      for (int i = 0; i < 36; i++) {
         this.count(have, player.getInventory().getItem(i));
      }

      this.count(have, player.getOffhandItem());
      return have;
   }

   private void count(Map<String, Integer> have, ItemStack stack) {
      String key = stack.isEmpty() ? null : this.keyOf(stack);
      if (key != null) {
         have.merge(key, stack.getCount(), Integer::sum);
      }
   }

   private static String idOf(ItemStack stack) {
      return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   }

   /** The target a stack counts toward, or null for something the layout does not ask for. */
   private String keyOf(ItemStack stack) {
      if (stack.is(Items.TOTEM_OF_UNDYING)) {
         return TOTEMS;
      } else if (stack.is(Items.END_CRYSTAL)) {
         return CRYSTALS;
      } else if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
         return GAPPLES;
      } else if (stack.is(Items.OBSIDIAN)) {
         return OBSIDIAN;
      } else if (stack.is(Items.ENDER_PEARL)) {
         return PEARLS;
      } else if (stack.is(Items.EXPERIENCE_BOTTLE)) {
         return XP;
      }

      String id = idOf(stack);
      if (this.extraItems.contains(id)) {
         return id;
      } else if (stack.has(DataComponents.FOOD)) {
         return FOOD;
      } else {
         return this.matchHotbar.get() && this.hotbarSaved && this.inSavedHotbar(id) ? id : null;
      }
   }

   /** {@link #keyOf} for a bare item id from the saved hotbar: non-null when a staple or the extra list covers it. */
   private String keyOfId(String id) {
      return BuiltInRegistries.ITEM
         .getOptional(net.minecraft.resources.Identifier.tryParse(id))
         .map(item -> {
            ItemStack probe = new ItemStack(item);
            if (probe.is(Items.TOTEM_OF_UNDYING)
               || probe.is(Items.END_CRYSTAL)
               || probe.is(Items.GOLDEN_APPLE)
               || probe.is(Items.ENCHANTED_GOLDEN_APPLE)
               || probe.is(Items.OBSIDIAN)
               || probe.is(Items.ENDER_PEARL)
               || probe.is(Items.EXPERIENCE_BOTTLE)
               || this.extraItems.contains(id)
               || probe.has(DataComponents.FOOD)) {
               return this.keyOf(probe);
            }

            return null;
         })
         .orElse(null);
   }

   private boolean inSavedHotbar(String id) {
      for (String saved : this.savedHotbar) {
         if (id.equals(saved)) {
            return true;
         }
      }

      return false;
   }

   private enum State {
      IDLE("Starting", 5),
      PLACING("Placing", 30),
      WAIT_PLACED("Placing", 20),
      OPENING("Opening", 40),
      WAIT_OPEN("Opening", 40),
      LOOTING("Restocking", 100),
      CLOSING("Closing", 20),
      MINING("Mining", 400),
      WAIT_PICKUP("Picking up", 60);

      private final String label;
      /** Ticks this step may take before the run is abandoned. */
      private final int timeout;

      State(String label, int timeout) {
         this.label = label;
         this.timeout = timeout;
      }
   }
}
