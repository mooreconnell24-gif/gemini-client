package gemini.module.modules.combat;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.render.Render3D;
import gemini.util.BlockPlacer;
import gemini.util.PlaceSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class AutoTrap extends Module {
   private final PlaceSettings placer = new PlaceSettings(this, 1, 3, 4.8);
   private final ModeSetting mode = this.addSetting(new ModeSetting("Mode", "Trap block coverage", "Full", "Full", "Head", "Feet"));
   private final NumberSetting targetRange = this.addSetting(new NumberSetting("Target Range", "Enemy search range", 5.0, 2.0, 7.0, 0.2));
   private final NumberSetting predictTicks = this.addSetting(new NumberSetting("Predict Ticks", "Predict enemy movement ticks ahead", 2.0, 0.0, 6.0, 1.0));
   private final BooleanSetting antiStep = this.addSetting(new BooleanSetting("Anti Step", "Cap top of trap to block stepping out", true));
   private final BooleanSetting render = this.addSetting(new BooleanSetting("Render", "Highlight queued trap blocks", true));
   private final List<BlockPos> currentPlan = new ArrayList<>();

   public AutoTrap() {
      super("AutoTrap", "Encase nearby enemies in obsidian with predictive aim.", Category.COMBAT);
   }

   @Override
   public void onDisable() {
      this.currentPlan.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (CombatHelpers.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         LivingEntity enemy = CombatHelpers.nearestEnemy(client, player, this.targetRange.get(), true);
         if (enemy == null) {
            this.currentPlan.clear();
         } else {
            Vec3 predicted = enemy.position().add(enemy.getDeltaMovement().scale(this.predictTicks.get()));
            BlockPos base = BlockPos.containing(predicted.x, predicted.y + 0.1, predicted.z);
            this.currentPlan.clear();

            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
               if (!this.mode.is("Head")) {
                  this.currentPlan.add(base.relative(dir));
               }

               if (!this.mode.is("Feet")) {
                  this.currentPlan.add(base.above().relative(dir));
               }
            }

            if (!this.mode.is("Feet")) {
               this.currentPlan.add(base.above().relative(Direction.NORTH).above());
               this.currentPlan.add(base.above(2));
               if (this.antiStep.get()) {
                  this.currentPlan.add(base.above(3));
               }
            }

            if (this.placer.ready()) {
               this.placer.place(client, this.currentPlan, CombatHelpers.obsidianOrResistant());
            }
         }
      } else {
         this.currentPlan.clear();
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      if (this.render.get() && client.level != null) {
         for (BlockPos pos : this.currentPlan) {
            if (BlockPlacer.replaceable(client.level, pos)) {
               Render3D.block(pos, -16718337, true, true, 1.5F, 45, true);
            }
         }
      }
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.currentPlan.clear();
   }
}
