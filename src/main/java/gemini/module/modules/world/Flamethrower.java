package gemini.module.modules.world;

import gemini.util.ClientState;
import gemini.util.Swing;
import gemini.module.Category;
import gemini.event.ActionPriority;
import gemini.util.Hands;
import gemini.module.Module;
import gemini.module.Targeting;
import java.util.function.Predicate;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.Inventories;
import gemini.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Flamethrower extends Module {
   private final NumberSetting range = this.addSetting(new NumberSetting("Range", "Farthest target, in blocks", 4.5, 1.0, 6.0, 0.5));
   private final NumberSetting delay = this.addSetting(new NumberSetting("Delay", "Ticks between fires", 4.0, 1.0, 40.0, 1.0));
   private final BooleanSetting players = this.addSetting(new BooleanSetting("Players", "Set fire under players", true));
   private final BooleanSetting mobs = this.addSetting(new BooleanSetting("Mobs", "Set fire under hostile mobs", true));
   private final BooleanSetting skipBurning = this.addSetting(new BooleanSetting("Skip Burning", "Ignore targets that are already on fire", true));
   private final BooleanSetting rotate = this.addSetting(new BooleanSetting("Silent Aim", "Aim at the fire spot without moving your camera", true));
   private final Targeting targeting = Targeting.builder(this)
      .filters(this.players::get, this.mobs::get, () -> false, () -> true)
      .build();
   private final Predicate<LivingEntity> burnable = this::burnable;
   private int wait;

   public Flamethrower() {
      super("Flamethrower", "Sets fire to the ground under nearby enemies.", Category.WORLD);
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client) || client.gui.screen() != null) {
         this.targeting.clear();
         return;
      }

      // Picked every tick so the box follows the choice; fire is only lit every Delay ticks.
      LocalPlayer player = client.player;
      LivingEntity target = this.targeting.pick(player, client.level.entitiesForRendering(), this.range.get(), this.burnable);
      if (this.wait-- > 0) {
         return;
      }

      this.wait = this.delay.intValue();
      int slot = Inventories.findHotbar(player, stack -> stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE));
      if (slot < 0 || target == null) {
         return;
      }

      BlockPos ground = target.blockPosition().below();
      if (this.rotate.get() && !RotationManager.request(player, RotationManager.toward(player, ground), 0.0F, true, ActionPriority.WORLD_UTILITY)) {
         this.wait = 0;
         return;
      }

      if (!Hands.claimMainHand()) {
         return;
      }

      int previous = player.getInventory().getSelectedSlot();
      player.getInventory().setSelectedSlot(slot);
      Vec3 hit = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
      client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, ground, false));
      Swing.interact(player, InteractionHand.MAIN_HAND);
      player.getInventory().setSelectedSlot(previous);
   }

   /** Not already burning (with Skip Burning), standing in air on top of a solid block that fire can sit on. */
   private boolean burnable(LivingEntity target) {
      Level level = Minecraft.getInstance().level;
      if (level == null || this.skipBurning.get() && target.isOnFire()) {
         return false;
      }

      BlockPos feet = target.blockPosition();
      BlockPos below = feet.below();
      return level.getBlockState(feet).isAir() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
   }

   @Override
   public void onRender3D(float partialTick) {
      this.targeting.render(partialTick);
   }

   @Override
   public void onDisable() {
      this.targeting.clear();
      this.wait = 0;
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.onDisable();
   }
}
