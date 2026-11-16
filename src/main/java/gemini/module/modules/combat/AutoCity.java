package gemini.module.modules.combat;

import gemini.util.InfoText;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.render.ShapeSettings;
import gemini.ui.HudOverlay;
import gemini.util.BlockBreaking;
import gemini.util.BlockPlacer;
import gemini.util.ClientState;
import gemini.util.Hands;
import gemini.util.Holes;
import gemini.util.RotationManager;
import gemini.util.Swing;
import gemini.util.Tools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks open the surround of an enemy sitting in a hole. The block to mine is picked by {@link CityPlanner}
 * (crystal spot first, then the quickest break, then the closest), and it is mined the same way PacketMine and
 * the other module-driven breakers do it: {@code continueDestroyBlock} every tick with {@link BlockBreaking#keep()}
 * so vanilla does not abort the break in between. Detonating the crystals is left to Auto Crystal.
 */
public final class AutoCity extends Module {
   private final InfoText progressText = new InfoText();
   private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest block you will mine", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting targetRange = this.addSetting(new NumberSetting("Target Range", "Enemy search distance", 6.0, 2.0, 10.0, 0.5));
   private final BooleanSetting autoTool = this.addSetting(
      new BooleanSetting("Auto Tool", "Hold the fastest hotbar tool while mining and put your slot back afterwards", true)
   );
   private final BooleanSetting crystal = this.addSetting(
      new BooleanSetting("Pre-place Crystal", "Put a crystal beside the block just before it breaks, or in the gap once it has", true)
   );
   private final NumberSetting crystalAt = this.addSetting(
      new NumberSetting("Pre-place At", "Break progress (percent) at which the crystal goes down", 85.0, 50.0, 100.0, 5.0).showIf(this.crystal::get)
   );
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Rotate", "Face the block while mining it", true));
   private final BooleanSetting silentAim = this.addSetting(
      new BooleanSetting("Silent Aim", "Face it in packets only, leaving the camera alone", true).showIf(this.rotate::get)
   );
   private final BooleanSetting autoDisable = this.addSetting(new BooleanSetting("Auto Disable", "Switch off once one block has been broken", true));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Show the block being mined and how far along it is", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Highlight colour", 10).showIf(this.render::get));
   private final ShapeSettings shape = new ShapeSettings(this, "Both", true, this.render::get);
   private final Targeting targeting = Targeting.builder(this).box("Outline", 10).build();
   private LivingEntity target;
   private BlockPos cityPos;
   private Direction cityDir;
   private float progress;
   private int toolSlot = -1;
   private int restoreSlot = -1;
   private boolean breaking;
   private boolean crystalPlaced;

   public AutoCity() {
      super(
         "Auto City",
         "Mine open the surround of the enemy in a hole that Priority picks (the next one if none of its blocks can be mined), with an optional crystal pre-place.",
         Category.COMBAT
      );
   }

   @Override
   public String info() {
      return this.cityPos == null ? null : Math.round(this.progress * 100.0F) + "%";
   }

   @Override
   public void onEnable() {
      this.reset();
   }

   @Override
   public void onDisable() {
      Minecraft client = Minecraft.getInstance();
      this.stopBreaking(client);
      this.restoreSlot(client.player);
      this.reset();
      this.targeting.clear();
   }

   @Override
   public void onWorldChange() {
      this.breaking = false;
      this.restoreSlot = -1;
      this.reset();
      this.targeting.clear();
   }

   private void reset() {
      this.target = null;
      this.cityPos = null;
      this.cityDir = null;
      this.progress = 0.0F;
      this.toolSlot = -1;
      this.crystalPlaced = false;
   }

   private void stopBreaking(Minecraft client) {
      if (this.breaking) {
         this.breaking = false;
         BlockBreaking.release();
         if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }
      }
   }

   private void restoreSlot(LocalPlayer player) {
      if (this.restoreSlot >= 0 && player != null) {
         player.getInventory().setSelectedSlot(this.restoreSlot);
      }

      this.restoreSlot = -1;
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.readyNoScreen(client)) {
         this.stopBreaking(client);
         return;
      }

      LocalPlayer player = client.player;
      Level level = client.level;
      if (this.cityPos != null && level.getBlockState(this.cityPos).isAir()) {
         this.finished(client, player);
         return;
      }

      if (!this.stillValid(player)) {
         this.stopBreaking(client);
         this.restoreSlot(player);
         this.reset();
         this.pick(client, player);
         if (this.cityPos == null) {
            return;
         }
      }

      this.mine(client, player);
   }

   /** The current block is still worth mining: it is there, in reach, and the target still sits next to it. */
   private boolean stillValid(LocalPlayer player) {
      if (this.cityPos == null || this.target == null || !this.target.isAlive() || this.target.isRemoved()) {
         return false;
      }

      return this.target.blockPosition().relative(this.cityDir).equals(this.cityPos) && BlockPlacer.inReach(player, this.cityPos, this.range.get());
   }

   private static boolean surrounded(Level level, BlockPos feet) {
      if (Holes.isHole(level, feet)) {
         return true;
      }

      for (Direction side : SIDES) {
         BlockPos pos = feet.relative(side);
         if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   /**
    * Goes for the enemy in a hole that Priority picks; when none of its surround blocks can be mined from here,
    * the next one by priority is tried.
    */
   private void pick(Minecraft client, LocalPlayer player) {
      Level level = client.level;
      LivingEntity enemy = TargetFallback.first(
         exclude -> this.targeting
            .pick(player, client.level.entitiesForRendering(), this.targetRange.get(), living -> exclude.test(living) && surrounded(level, living.blockPosition())),
         candidate -> this.planCity(level, player, candidate),
         TargetFallback.TRIES
      );
      if (enemy == null) {
         this.targeting.clear();
      } else {
         this.targeting.setTarget(enemy);
      }
   }

   /** Chooses the surround block of {@code enemy} to mine; false (and nothing chosen) when none can be. */
   private boolean planCity(Level level, LocalPlayer player, LivingEntity enemy) {
      BlockPos feet = enemy.blockPosition();
      List<CityPlanner.Candidate> candidates = new ArrayList<>(4);
      for (int i = 0; i < SIDES.length; i++) {
         BlockPos pos = feet.relative(SIDES[i]);
         BlockState state = level.getBlockState(pos);
         if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F || !BlockPlacer.inReach(player, pos, this.range.get())) {
            continue;
         }

         int ticks = this.autoTool.get() ? Tools.breakTicks(player, level, pos, state) : heldBreakTicks(player, level, pos, state);
         if (ticks == Integer.MAX_VALUE) {
            continue;
         }

         boolean spot = CrystalSpots.placeable(level, outerBase(pos, SIDES[i])) || afterBreakSpot(level, pos);
         candidates.add(new CityPlanner.Candidate(i, spot, ticks, Vec3.atCenterOf(pos).distanceToSqr(player.getEyePosition())));
      }

      CityPlanner.Candidate best = CityPlanner.best(candidates);
      if (best == null) {
         return false;
      }

      this.target = enemy;
      this.cityDir = SIDES[best.id()];
      this.cityPos = feet.relative(this.cityDir);
      this.progress = 0.0F;
      this.crystalPlaced = false;
      return true;
   }

   private static int heldBreakTicks(LocalPlayer player, Level level, BlockPos pos, BlockState state) {
      float perTick = state.getDestroyProgress(player, level, pos);
      return perTick <= 0.0F ? Integer.MAX_VALUE : (int)Math.ceil(1.0F / perTick);
   }

   /** The base outside the mined block: a crystal there sits right behind it and hits the target once it is gone. */
   private static BlockPos outerBase(BlockPos city, Direction dir) {
      return city.relative(dir).below();
   }

   /** Whether a crystal can go into the gap itself once the block is broken. */
   private static boolean afterBreakSpot(Level level, BlockPos city) {
      return CrystalSpots.isBase(level.getBlockState(city.below())) && level.getBlockState(city.above()).isAir();
   }

   private void mine(Minecraft client, LocalPlayer player) {
      Level level = client.level;
      BlockState state = level.getBlockState(this.cityPos);
      if (this.rotate.get()
         && !RotationManager.request(player, RotationManager.toward(player, this.cityPos), 0.0F, this.silentAim.get(), this.priority())) {
         return;
      }

      if (this.autoTool.get()) {
         if (this.toolSlot < 0) {
            this.toolSlot = Tools.bestSlot(player, state, true);
         }

         int held = player.getInventory().getSelectedSlot();
         if (this.toolSlot != held) {
            if (!Hands.claimMainHand()) {
               return;
            }

            if (this.restoreSlot < 0) {
               this.restoreSlot = held;
            }

            player.getInventory().setSelectedSlot(this.toolSlot);
         } else if (this.restoreSlot >= 0 && !Hands.claimMainHand()) {
            // Another module outranks this one on the hand this tick; it may switch away, so wait.
            return;
         }
      }

      if (this.crystal.get() && !this.crystalPlaced && this.progress * 100.0F >= this.crystalAt.floatValue()) {
         BlockPos base = outerBase(this.cityPos, this.cityDir);
         if (CrystalSpots.placeable(level, base) && CrystalSpots.hasCrystal(player)) {
            this.crystalPlaced = CrystalSpots.place(client, player, base, this.silentAim.get() || !this.rotate.get(), this.priority());
         }
      }

      this.breaking = true;
      BlockBreaking.keep();
      if (client.gameMode.continueDestroyBlock(this.cityPos, Direction.UP)) {
         Swing.attack(player, InteractionHand.MAIN_HAND);
      }

      this.progress = Math.min(1.0F, this.progress + state.getDestroyProgress(player, level, this.cityPos));
   }

   private void finished(Minecraft client, LocalPlayer player) {
      this.stopBreaking(client);
      this.restoreSlot(player);
      if (this.crystal.get() && !this.crystalPlaced && CrystalSpots.placeable(client.level, this.cityPos.below()) && CrystalSpots.hasCrystal(player)) {
         CrystalSpots.place(client, player, this.cityPos.below(), this.silentAim.get() || !this.rotate.get(), this.priority());
      }

      if (this.autoDisable.get()) {
         HudOverlay.toast("Auto City", "Surround block broken", HudOverlay.Severity.SUCCESS);
         this.setEnabled(false, false);
      } else {
         this.reset();
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
      BlockPos pos = this.cityPos;
      if (!this.render.get() || pos == null) {
         return;
      }

      int argb = this.color.argb();
      AABB full = new AABB(pos);
      Render3D.box(full, argb, true, false, this.shape.width(), 0, this.shape.throughWalls());
      double inset = 0.5 * (1.0 - this.progress);
      if (this.progress > 0.01F) {
         this.shape.box(full.deflate(inset), argb);
      }

      Render3D.text(this.progressText.whole(Math.round(this.progress * 100.0F), "%"), Vec3.atCenterOf(pos).add(0.0, 0.7, 0.0), -1, 0.8F, true);
   }
}
