package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.PlacementModuleBase;
import gemini.module.Targeting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cobwebs where the enemy is going, not where it was. Web Aura webs the enemy's feet; this predicts its path from
 * its velocity with {@link WebPath} and webs the blocks ahead of it (and optionally its feet too). Your own
 * blocks, and every block your own motion is about to carry you into, are never webbed.
 */
public final class AutoWebFill extends PlacementModuleBase {
   private static final Predicate<ItemStack> COBWEB = stack -> stack.getItem() instanceof BlockItem item && item.getBlock() == Blocks.COBWEB;
   private final NumberSetting enemyRange = this.addSetting(new NumberSetting("Enemy Range", "Target distance for cobwebs", 6.0, 2.0, 10.0, 0.5));
   private final NumberSetting predictTicks = this.addSetting(
      new NumberSetting("Prediction Ticks", "How many ticks ahead the path is predicted", 8.0, 1.0, 20.0, 1.0)
   );
   private final NumberSetting ahead = this.addSetting(new NumberSetting("Webs Ahead", "Most path blocks ahead of the enemy to web", 2.0, 1.0, 6.0, 1.0));
   private final BooleanSetting feet = this.addSetting(new BooleanSetting("Feet", "Also web the block the enemy stands in", true));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Show the predicted path and the planned webs", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Path and plan colour", 190).showIf(this.render::get));
   private final Targeting targeting = Targeting.builder(this).box("Outline", 190).build();
   private final List<Vec3> path = new ArrayList<>();
   private final List<BlockPos> planned = new ArrayList<>();

   public AutoWebFill() {
      super("Auto Web Fill", "Place cobwebs along the path the enemy Priority picks is about to walk.", Category.COMBAT, 1, 2, 4.5);
   }

   @Override
   public String info() {
      return this.planned.isEmpty() ? null : Integer.toString(this.planned.size());
   }

   @Override
   public void onDisable() {
      this.path.clear();
      this.planned.clear();
      this.placer.reset();
      this.targeting.clear();
   }

   @Override
   public void onWorldChange() {
      this.path.clear();
      this.planned.clear();
      this.targeting.clear();
   }

   @Override
   protected boolean shouldPlace(Minecraft client, LocalPlayer player) {
      // The plan is drawn even while the placement delay runs, so it is worked out before the base asks the placer.
      this.plan(client, player);
      return true;
   }

   @Override
   protected void collectTargets(Minecraft client, LocalPlayer player, List<BlockPos> targets) {
      targets.addAll(this.planned);
   }

   private void plan(Minecraft client, LocalPlayer player) {
      this.path.clear();
      this.planned.clear();
      Level level = client.level;
      LivingEntity enemy = this.targeting.pick(player, client.level.entitiesForRendering(), this.enemyRange.get(), null);
      if (enemy == null) {
         return;
      }

      Vec3 velocity = enemy.getDeltaMovement();
      Vec3 moved = enemy.position().subtract(enemy.xo, enemy.yo, enemy.zo);
      // Other players' motion is not sent to the client; their last step is the better guess then.
      if (velocity.horizontalDistanceSqr() < moved.horizontalDistanceSqr()) {
         velocity = moved;
      }

      List<int[]> blocks = WebPath.predict(
         enemy.getX(), enemy.getY() + 0.1, enemy.getZ(), velocity.x, velocity.z, this.predictTicks.intValue(), WebPath.GROUND_DRAG, (x, y, z) -> walkable(level, x, y, z)
      );
      AABB selfZone = player.getBoundingBox().expandTowards(player.getDeltaMovement().scale(3.0)).inflate(0.05);
      int aheadLeft = this.ahead.intValue();
      for (int i = 0; i < blocks.size(); i++) {
         int[] b = blocks.get(i);
         BlockPos pos = new BlockPos(b[0], b[1], b[2]);
         this.path.add(Vec3.atBottomCenterOf(pos).add(0.0, 0.05, 0.0));
         boolean wanted = i == 0 ? this.feet.get() : aheadLeft-- > 0;
         if (wanted && !selfZone.intersects(new AABB(pos)) && BlockPlacer.replaceable(level, pos) && !level.getBlockState(pos).is(Blocks.COBWEB)) {
            this.planned.add(pos);
         }
      }
   }

   /** Feet and head space free to walk through (a cobweb counts as free) and something solid to stand on. */
   private static boolean walkable(Level level, int x, int y, int z) {
      BlockPos feetPos = new BlockPos(x, y, z);
      BlockPos below = feetPos.below();
      BlockState floor = level.getBlockState(below);
      return level.getBlockState(feetPos).getCollisionShape(level, feetPos).isEmpty()
         && level.getBlockState(feetPos.above()).getCollisionShape(level, feetPos.above()).isEmpty()
         && !floor.getCollisionShape(level, below).isEmpty();
   }

   @Override
   protected Predicate<ItemStack> blocks() {
      return COBWEB;
   }

   /** The webs are meant to go where the enemy stands or is about to. */
   @Override
   protected boolean avoidEntities() {
      return false;
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
      if (!this.render.get()) {
         return;
      }

      int argb = this.color.argb();
      Render3D.polyline(this.path, argb, 1.8F, true);
      for (BlockPos pos : this.planned) {
         Render3D.block(pos, argb, true, true, 1.4F, 40, true);
      }
   }
}
