package gemini.module.modules.bot;

import gemini.event.ActionPriority;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.bot.BuildSchematic.Material;
import gemini.module.modules.bot.BuildSchematic.Placement;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.ListSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.ui.HudOverlay;
import gemini.util.BaritoneBridge;
import gemini.util.BlockBreaking;
import gemini.util.BlockPlacer;
import gemini.util.BuiltInBaritone;
import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Inventories;
import gemini.util.PlaceSettings;
import gemini.util.RotationManager;
import gemini.util.Swing;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a pattern from your inventory at the block you are looking at (or a few blocks ahead). The layout comes
 * from {@link BuildSchematic}; this module turns it into world positions facing the way you face, checks the
 * materials once up front, then places block by block in schematic order: it walks into reach through
 * {@link BaritoneBridge} (Baritone, or the built-in pathfinder without it), places with the shared
 * {@link PlaceSettings} rules, clears spots that must be empty and blocks that are in the way when allowed, and
 * gives up with a toast when nothing has progressed for a while. Switching it off cancels the walk.
 *
 * <p>For Wall/Platform/Pillar/Bridge any solid block already standing at a spot counts as done, so building over
 * existing terrain just fills the gaps.
 */
public final class AutoBuildBot extends Module {
   private final ModeSetting pattern = this.addSetting(
      new ModeSetting("Pattern", "What to build", "Nether Portal", "Nether Portal", "Wither", "Cobble Wall", "Platform", "Pillar", "Bridge")
   );
   private final BooleanSetting corners = this.addSetting(
      new BooleanSetting("Corners", "Fill the portal frame corners (without them the sides need Air Place)", true).showIf(() -> this.pattern.is("Nether Portal"))
   );
   private final BooleanSetting ignite = this.addSetting(
      new BooleanSetting("Ignite", "Light the finished portal with flint and steel", true).showIf(() -> this.pattern.is("Nether Portal"))
   );
   private final BooleanSetting confirmWither = this.addSetting(
      new BooleanSetting(
            "Confirm Wither", "Must be turned on for each wither build because it spawns a boss; it turns itself off once a build starts", false
         )
         .showIf(() -> this.pattern.is("Wither"))
   );
   private final NumberSetting wallLength = this.addSetting(
      new NumberSetting("Wall Length", "Wall length, in blocks", 5.0, 1.0, 32.0, 1.0).showIf(() -> this.pattern.is("Cobble Wall"))
   );
   private final NumberSetting wallHeight = this.addSetting(
      new NumberSetting("Wall Height", "Wall height, in blocks", 3.0, 1.0, 16.0, 1.0).showIf(() -> this.pattern.is("Cobble Wall"))
   );
   private final NumberSetting platformSize = this.addSetting(
      new NumberSetting("Platform Size", "Platform width (N x N)", 5.0, 1.0, 15.0, 1.0).showIf(() -> this.pattern.is("Platform"))
   );
   private final NumberSetting platformDepth = this.addSetting(
      new NumberSetting("Platform Depth", "Blocks below your feet (1 = the layer you stand on)", 1.0, 1.0, 4.0, 1.0).showIf(() -> this.pattern.is("Platform"))
   );
   private final NumberSetting pillarHeight = this.addSetting(
      new NumberSetting("Pillar Height", "Pillar height, in blocks", 5.0, 1.0, 64.0, 1.0).showIf(() -> this.pattern.is("Pillar"))
   );
   private final NumberSetting bridgeLength = this.addSetting(
      new NumberSetting("Bridge Length", "Bridge length, in blocks", 10.0, 1.0, 64.0, 1.0).showIf(() -> this.pattern.is("Bridge"))
   );
   private final ModeSetting fillSource = this.addSetting(
      new ModeSetting("Material", "Blocks for walls, platforms, pillars and bridges: the block you hold when starting, or the list", "Held Block", "Held Block", "List")
         .showIf(this::usesFill)
   );
   private final ListSetting fillBlocks = this.addSetting(
      new ListSetting("Blocks", "Blocks the List material may use", ListSetting.Kind.BLOCK, "minecraft:cobblestone")
         .showIf(() -> this.usesFill() && this.fillSource.is("List"))
   );
   private final PlaceSettings placer = new PlaceSettings(this, 1, 2, 4.5);
   private final BooleanSetting walk = this.addSetting(new BooleanSetting("Walk", "Walk into reach of the next block with Baritone", true));
   private final BooleanSetting clearObstructions = this.addSetting(
      new BooleanSetting("Clear Obstructions", "Mine blocks that are where the pattern needs air or a different block", true)
   );
   private final NumberSetting giveUp = this.addSetting(new NumberSetting("Give Up After", "Seconds without progress before stopping", 20.0, 5.0, 120.0, 5.0));
   private final BooleanSetting ghost = this.addSetting(new BooleanSetting("Ghost", "Show the blocks still to place", true));
   private final ColorSetting ghostColor = this.addSetting(new ColorSetting("Ghost Color", "Colour of the blocks still to place", 200).showIf(this.ghost::get));
   private final List<BlockPos> positions = new ArrayList<>();
   private final List<Material> materials = new ArrayList<>();
   private final List<BlockPos> batch = new ArrayList<>();
   private boolean[] done = new boolean[0];
   private Set<Block> fillSet = Set.of();
   private BlockPos ignitionBase;
   private boolean ignited;
   private boolean walking;
   private BlockPos walkGoal;
   private int walkCooldown;
   private int placedCount;
   private int lastProgress;
   private int idleTicks;
   /** Closest the player has come to the spot being walked to; walking only counts as progress while this shrinks. */
   private double walkBest = Double.MAX_VALUE;
   /** The block this module is breaking, so switching off can abort it. */
   private BlockPos mining;
   /** Ticks until the portal may be lit again after a try that did not light it. */
   private int igniteWait;
   private String infoPattern;
   private int infoPlaced = -1;
   private int infoTotal = -1;
   private String infoText;

   public AutoBuildBot() {
      super(
         "Auto Build Bot",
         "Builds a nether portal, wither, wall, platform, pillar or bridge from your inventory where you look, walking into reach with Baritone.",
         Category.WORLD
      );
   }

   @Override
   public ActionPriority priority() {
      return ActionPriority.BOT;
   }

   @Override
   public boolean selfTestExempt() {
      return true;
   }

   @Override
   protected boolean restoresEnabledState() {
      return false;
   }

   private boolean usesFill() {
      return !this.pattern.is("Nether Portal") && !this.pattern.is("Wither");
   }

   @Override
   public String info() {
      // Runs every frame: rebuild the text only when something in it changed.
      String name = this.pattern.get();
      int total = this.positions.size();
      if (!name.equals(this.infoPattern) || this.placedCount != this.infoPlaced || total != this.infoTotal) {
         this.infoPattern = name;
         this.infoPlaced = this.placedCount;
         this.infoTotal = total;
         this.infoText = total == 0 ? name : name + " " + this.placedCount + "/" + total;
      }

      return this.infoText;
   }

   @Override
   public void onEnable() {
      this.clearPlan();
      String problem = this.plan();
      if (problem != null) {
         HudOverlay.toast("Auto Build Bot", problem, HudOverlay.Severity.ERROR);
         this.clearPlan();
         this.setEnabled(false, false);
      } else if (this.pattern.is("Wither")) {
         // The confirmation covers this one build; the next wither needs it switched on again.
         this.confirmWither.set(false);
      }
   }

   @Override
   public void onDisable() {
      this.stopWalking();
      this.stopMining();
      this.clearPlan();
      this.placer.reset();
   }

   @Override
   public void onWorldChange() {
      this.setEnabled(false, false);
   }

   private void clearPlan() {
      this.positions.clear();
      this.materials.clear();
      this.batch.clear();
      this.done = new boolean[0];
      this.ignitionBase = null;
      this.ignited = false;
      this.walkGoal = null;
      this.walkCooldown = 0;
      this.placedCount = 0;
      this.lastProgress = 0;
      this.idleTicks = 0;
      this.walkBest = Double.MAX_VALUE;
      this.mining = null;
      this.igniteWait = 0;
   }

   /** Cancels the walk only if it is still ours: a task another module or a command started since is left alone. */
   private void stopWalking() {
      if (this.walking) {
         BaritoneBridge.stopTaskOf(this);
         this.walking = false;
         this.walkGoal = null;
      }

      this.walkBest = Double.MAX_VALUE;
   }

   private void stopMining() {
      Minecraft client = Minecraft.getInstance();
      if (this.mining != null && client.gameMode != null) {
         BlockBreaking.release();
         client.gameMode.stopDestroyBlock();
      }

      this.mining = null;
   }

   /** Works out the world positions and checks materials; returns what is wrong, or null to go ahead. */
   private String plan() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player == null || level == null) {
         return "No world loaded";
      }

      if (this.pattern.is("Wither") && !this.confirmWither.get()) {
         return "Turn on Confirm Wither first: it spawns a boss";
      }

      Direction facing = player.getDirection();
      Direction right = facing.getClockWise();
      BlockPos target = client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && BlockPlacer.inReach(player, hit.getBlockPos(), this.placer.range.get() + 1.0)
         ? hit.getBlockPos().above()
         : player.blockPosition().relative(facing, 3);
      List<Placement> schematic;
      BlockPos origin;
      switch (this.pattern.get()) {
         case "Nether Portal" -> {
            schematic = BuildSchematic.netherPortal(this.corners.get());
            origin = target.relative(right, -1);
         }
         case "Wither" -> {
            schematic = BuildSchematic.wither();
            origin = target;
         }
         case "Cobble Wall" -> {
            schematic = BuildSchematic.wall(this.wallLength.intValue(), this.wallHeight.intValue());
            origin = target.relative(right, -(this.wallLength.intValue() - 1) / 2);
         }
         case "Platform" -> {
            schematic = BuildSchematic.platform(this.platformSize.intValue());
            origin = player.blockPosition().below(this.platformDepth.intValue());
         }
         case "Pillar" -> {
            schematic = BuildSchematic.pillar(this.pillarHeight.intValue());
            origin = target;
         }
         default -> {
            schematic = BuildSchematic.bridge(this.bridgeLength.intValue());
            origin = player.blockPosition().below().relative(facing);
         }
      }

      if (this.usesFill()) {
         if (this.fillSource.is("List")) {
            this.fillSet = this.fillBlocks.blocks();
         } else {
            ItemStack held = player.getMainHandItem();
            this.fillSet = held.getItem() instanceof BlockItem item ? Set.of(item.getBlock()) : Set.of();
         }

         if (this.fillSet.isEmpty()) {
            return this.fillSource.is("List") ? "The Blocks list is empty" : "Hold the block to build with";
         }
      }

      for (Placement placement : schematic) {
         this.positions.add(origin.relative(right, placement.right()).above(placement.up()).relative(facing, placement.forward()));
         this.materials.add(placement.material());
      }

      this.done = new boolean[this.positions.size()];
      if (this.pattern.is("Nether Portal") && this.ignite.get()) {
         Placement base = BuildSchematic.portalIgnitionBase();
         this.ignitionBase = origin.relative(right, base.right()).above(base.up()).relative(facing, base.forward());
      }

      // Materials: what the spots not already filled still need, against the whole inventory.
      Map<Material, Integer> needed = new EnumMap<>(Material.class);
      for (int i = 0; i < this.positions.size(); i++) {
         Material material = this.materials.get(i);
         if (material != Material.AIR && !this.satisfied(level.getBlockState(this.positions.get(i)), material)) {
            needed.merge(material, 1, Integer::sum);
         }
      }

      StringBuilder missing = new StringBuilder();
      for (Map.Entry<Material, Integer> entry : needed.entrySet()) {
         int have = count(player, this.itemFilter(entry.getKey()));
         if (have < entry.getValue()) {
            if (missing.length() > 0) {
               missing.append(", ");
            }

            missing.append(entry.getValue() - have).append(' ').append(label(entry.getKey()));
         }
      }

      if (this.ignitionBase != null && count(player, stack -> stack.is(Items.FLINT_AND_STEEL)) == 0) {
         if (missing.length() > 0) {
            missing.append(", ");
         }

         missing.append("flint and steel");
      }

      return missing.length() == 0 ? null : "Missing " + missing;
   }

   private static String label(Material material) {
      return switch (material) {
         case OBSIDIAN -> "obsidian";
         case SOUL_SAND -> "soul sand/soil";
         case WITHER_SKULL -> "wither skulls";
         default -> "blocks";
      };
   }

   private static int count(LocalPlayer player, Predicate<ItemStack> filter) {
      int total = 0;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = player.getInventory().getItem(i);
         if (!stack.isEmpty() && filter.test(stack)) {
            total += stack.getCount();
         }
      }

      return total;
   }

   private Predicate<ItemStack> itemFilter(Material material) {
      return switch (material) {
         case OBSIDIAN -> BlockPlacer.blockItem(Blocks.OBSIDIAN);
         case SOUL_SAND -> stack -> stack.getItem() instanceof BlockItem item && (item.getBlock() == Blocks.SOUL_SAND || item.getBlock() == Blocks.SOUL_SOIL);
         case WITHER_SKULL -> stack -> stack.is(Items.WITHER_SKELETON_SKULL);
         case FILL -> {
            Set<Block> allowed = this.fillSet;
            yield stack -> stack.getItem() instanceof BlockItem item && allowed.contains(item.getBlock());
         }
         default -> stack -> false;
      };
   }

   private boolean satisfied(BlockState state, Material material) {
      return switch (material) {
         case AIR -> state.isAir() || state.canBeReplaced();
         case OBSIDIAN -> state.is(Blocks.OBSIDIAN);
         case SOUL_SAND -> state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
         case WITHER_SKULL -> state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.WITHER_SKELETON_WALL_SKULL);
         case FILL -> !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
      };
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (this.positions.isEmpty() || !ClientState.readyNoScreen(client)) {
         return;
      }

      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      // A spot counts once it has been seen right; the wither's blocks vanish when it spawns and must not reopen.
      int placed = 0;
      for (int i = 0; i < this.positions.size(); i++) {
         if (!this.done[i] && this.satisfied(level.getBlockState(this.positions.get(i)), this.materials.get(i))) {
            this.done[i] = true;
         }

         if (this.done[i]) {
            placed++;
         }
      }

      this.placedCount = placed;
      if (placed != this.lastProgress) {
         this.lastProgress = placed;
         this.idleTicks = 0;
      } else if (++this.idleTicks > this.giveUp.get() * 20.0) {
         this.fail("No progress for " + this.giveUp.intValue() + "s");
         return;
      }

      int next = -1;
      for (int i = 0; i < this.done.length; i++) {
         if (!this.done[i]) {
            next = i;
            break;
         }
      }

      if (next < 0) {
         if (this.ignitionBase != null && !this.ignited) {
            this.igniteTick(client, player);
         } else {
            this.finish();
         }

         return;
      }

      BlockPos pos = this.positions.get(next);
      Material material = this.materials.get(next);
      BlockState state = level.getBlockState(pos);
      boolean inReach = BlockPlacer.inReach(player, pos, this.placer.range.get());
      if (!inReach) {
         this.walkToward(level, player, pos);
         return;
      }

      this.stopWalking();
      if (material == Material.AIR || !BlockPlacer.replaceable(level, pos)) {
         // Something stands where air or a different block belongs.
         if (!this.clearObstructions.get()) {
            this.fail("Blocked at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
         } else {
            this.mine(client, player, pos);
         }

         return;
      }

      if (this.mining != null) {
         // The obstruction is gone: nothing is being broken any more.
         this.mining = null;
      }

      if (!this.placer.ready()) {
         return;
      }

      Predicate<ItemStack> filter = this.itemFilter(material);
      if (!this.ensureHotbar(client, player, filter)) {
         this.fail("Ran out of " + label(material));
         return;
      }

      // The next block, plus any later ones of the same material that are already placeable, up to the burst size.
      this.batch.clear();
      for (int i = next; i < this.positions.size() && this.batch.size() < this.placer.perTick.intValue(); i++) {
         if (this.done[i] || this.materials.get(i) != material) {
            continue;
         }

         BlockPos candidate = this.positions.get(i);
         if (BlockPlacer.replaceable(level, candidate)
            && BlockPlacer.inReach(player, candidate, this.placer.range.get())
            && (this.placer.airPlace.get() || BlockPlacer.findPlacement(level, candidate, false) != null)) {
            this.batch.add(candidate);
         } else if (i == next) {
            // The first one is not placeable yet; the rest must wait for it to keep supports first.
            break;
         }
      }

      if (!this.batch.isEmpty()) {
         this.placer.place(client, this.batch, filter);
      }
   }

   private void mine(Minecraft client, LocalPlayer player, BlockPos pos) {
      if (!Hands.claimMainHand()) {
         return;
      }

      if (!this.placer.rotate.get() || RotationManager.request(player, RotationManager.toward(player, pos), 0.0F, true, ActionPriority.BOT)) {
         BlockBreaking.keep();
         client.gameMode.continueDestroyBlock(pos, Direction.UP);
         Swing.attack(player, InteractionHand.MAIN_HAND);
         this.mining = pos;
      }
   }

   /** Makes sure a matching stack is on the hotbar, moving one up from the inventory if needed. */
   private boolean ensureHotbar(Minecraft client, LocalPlayer player, Predicate<ItemStack> filter) {
      if (Inventories.findHotbar(player, filter) >= 0) {
         return true;
      }

      int index = Inventories.find(player, filter, false);
      if (index < 9 || !Inventories.inventoryOpenOrClosed(player)) {
         return false;
      }

      int slot = 8;
      for (int i = 0; i < 9; i++) {
         if (player.getInventory().getItem(i).isEmpty()) {
            slot = i;
            break;
         }
      }

      Inventories.moveToHotbar(client, player, index, slot);
      return Inventories.findHotbar(player, filter) >= 0;
   }

   private void walkToward(ClientLevel level, LocalPlayer player, BlockPos pos) {
      if (!this.walk.get()) {
         return;
      }

      // Stand a couple of blocks short of the target, on the side the player is coming from.
      Vec3 away = player.position().subtract(Vec3.atCenterOf(pos));
      double length = Math.sqrt(away.x * away.x + away.z * away.z);
      int x = pos.getX() + (length < 1.0E-3 ? 0 : (int)Math.round(away.x / length * 2.0));
      int z = pos.getZ() + (length < 1.0E-3 ? 0 : (int)Math.round(away.z / length * 2.0));
      BlockPos goal = new BlockPos(x, BuiltInBaritone.findSurfaceY(level, x, z, pos.getY()), z);
      if (this.walkCooldown > 0) {
         this.walkCooldown--;
      }

      if (this.walking && !BaritoneBridge.holdsTask(this) && BaritoneBridge.isActive()) {
         // Someone else (a command, another bot) took the pathfinder: do not fight over it.
         this.walking = false;
         this.fail("The pathfinder was given another task");
         return;
      }

      // Getting closer is progress too; the give-up timer is for standing still or walking in circles.
      double distance = player.position().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      if (distance < this.walkBest - 0.25) {
         this.walkBest = distance;
         this.idleTicks = 0;
      }

      if (!goal.equals(this.walkGoal) || !BaritoneBridge.holdsTask(this) && this.walkCooldown == 0) {
         this.walkGoal = goal;
         this.walkCooldown = 40;
         this.walking = BaritoneBridge.gotoBlock(goal);
         if (this.walking) {
            BaritoneBridge.claimTask(this);
         }
      }
   }

   private void igniteTick(Minecraft client, LocalPlayer player) {
      BlockPos base = this.ignitionBase;
      if (client.level.getBlockState(base.above()).is(Blocks.NETHER_PORTAL)) {
         this.ignited = true;
         return;
      }

      if (this.igniteWait > 0) {
         this.igniteWait--;
         return;
      }

      if (!BlockPlacer.inReach(player, base, this.placer.range.get())) {
         this.walkToward(client.level, player, base);
         return;
      }

      this.stopWalking();
      Predicate<ItemStack> flint = stack -> stack.is(Items.FLINT_AND_STEEL);
      if (!this.ensureHotbar(client, player, flint)) {
         this.fail("No flint and steel");
         return;
      }

      int slot = Inventories.findHotbar(player, flint);
      if (slot < 0 || !Hands.claimMainHand()) {
         return;
      }

      if (this.placer.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, base), 0.0F, true, ActionPriority.BOT)) {
         return;
      }

      BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(base).add(0.0, 0.5, 0.0), Direction.UP, base, false);
      Inventories.withHotbarSlot(player, slot, () -> {
         client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
         Swing.interact(player, InteractionHand.MAIN_HAND);
      });
      // Done once the portal block shows up; if it does not (the frame is off, the fire went out), try again in a
      // second until the give-up timer runs out.
      this.igniteWait = 20;
   }

   private void finish() {
      String what = this.pattern.get();
      HudOverlay.toast("Auto Build Bot", what + " finished", HudOverlay.Severity.SUCCESS);
      this.setEnabled(false, false);
   }

   private void fail(String reason) {
      HudOverlay.toast("Auto Build Bot", reason, HudOverlay.Severity.ERROR);
      this.setEnabled(false, false);
   }

   @Override
   public void onRender3D(float partialTick) {
      if (!this.ghost.get() || this.positions.isEmpty()) {
         return;
      }

      int color = this.ghostColor.argb();
      boolean first = true;
      for (int i = 0; i < this.positions.size() && i < this.done.length; i++) {
         if (this.done[i]) {
            continue;
         }

         BlockPos pos = this.positions.get(i);
         if (this.materials.get(i) == Material.AIR) {
            Render3D.block(pos.getX(), pos.getY(), pos.getZ(), 0xFFE8605D, true, false, 1.2F, 0, false);
         } else if (first) {
            Render3D.boxGlow(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0, color, 1.8F, 70, true);
            first = false;
         } else {
            Render3D.block(pos.getX(), pos.getY(), pos.getZ(), color, true, true, 1.0F, 30, false);
         }
      }
   }
}
