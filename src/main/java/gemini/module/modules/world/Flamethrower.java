package gemini.module.modules.world;

import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import gemini.util.Targets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Flamethrower extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "How far a target may be", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between ignitions", 4.0, 1.0, 40.0, 1.0));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Burn players", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Burn hostile mobs", true));
   private final BooleanSetting skipBurning = this.addSetting(new BooleanSetting("Skip Burning", "Ignore targets that are already on fire", true));
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the fire spot in packets", true));
   private int wait;

   public Flamethrower() {
      super("Flamethrower", "Ignite the ground beneath nearby enemies.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (WorldHelpers.ready(client) && client.gui.screen() == null && this.wait-- <= 0) {
         this.wait = this.delay.intValue();
         LocalPlayer player = client.player;
         Level level = client.level;
         int slot = Inventories.findHotbar(player, stack -> stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE));
         if (slot >= 0) {
            for (Entity entity : client.level.entitiesForRendering()) {
               if (Targets.isCandidate(player, entity, this.players.get(), this.mobs.get(), false, true)
                  && (!this.skipBurning.get() || !entity.isOnFire())
                  && !(Targets.reach(player, entity) > this.range.get())) {
                  LivingEntity target = (LivingEntity)entity;
                  BlockPos feet = target.blockPosition();
                  if (level.getBlockState(feet).isAir() && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                     if (this.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, feet.below()), 0.0F, true, ActionPriority.WORLD_UTILITY)) {
                        this.wait = 0;
                        return;
                     }

                     if (!Hands.claimMainHand()) {

                        return;

                     }


                     int previous = player.getInventory().getSelectedSlot();
                     player.getInventory().setSelectedSlot(slot);
                     Vec3 hit = Vec3.atCenterOf(feet.below()).add(0.0, 0.5, 0.0);
                     client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, feet.below(), false));
                     Swing.interact(player, InteractionHand.MAIN_HAND);
                     player.getInventory().setSelectedSlot(previous);
                     return;
                  }
               }
            }
         }
      }
   }
}
