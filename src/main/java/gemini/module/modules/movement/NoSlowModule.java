package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.MoveUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class NoSlowModule extends Module {
   private static volatile NoSlowModule instance;
   private final BooleanSetting items = this.addSetting(
      new BooleanSetting("Items", "Walk at normal speed while eating, drinking, drawing a bow or blocking", true)
   );
   private final NumberSetting strength = this.addSetting(
      new NumberSetting("Strength", "How much of the item slowdown is removed (percent)", 100.0, 10.0, 100.0, 5.0).showIf(this.items::get)
   );
   private final BooleanSetting webs = this.addSetting(new BooleanSetting("Cobwebs", "Move through cobwebs at full speed", true));
   private final BooleanSetting berries = this.addSetting(new BooleanSetting("Berry Bushes", "Move through sweet berry bushes at full speed", true));
   private final BooleanSetting snow = this.addSetting(new BooleanSetting("Powder Snow", "Move through powder snow at full speed", true));
   private final BooleanSetting slowBlocks = this.addSetting(
      new BooleanSetting("Soul Sand And Honey", "Walk at normal speed over soul sand and honey blocks", true)
   );

   public NoSlowModule() {
      super("No Slow", "Removes the slowdown from using items, cobwebs, berry bushes, powder snow, soul sand and honey.", Category.MOVEMENT);
      instance = this;
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("NoWeb");
      names.add("TerrainSpeed");
      return names;
   }

   /** Called from the entity mixin: whether the local player's "stuck in block" slowdown for this block is skipped. */
   public static boolean cancels(BlockState state) {
      NoSlowModule module = instance;
      if (module == null || !module.isEnabled()) {
         return false;
      } else if (state.is(Blocks.COBWEB)) {
         return module.webs.get();
      } else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
         return module.berries.get();
      } else {
         return state.is(Blocks.POWDER_SNOW) && module.snow.get();
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (!ClientState.ready(client)) {
         return;
      }

      LocalPlayer player = client.player;
      if (player.isPassenger() || !player.onGround() || !MoveUtil.hasInput(player)) {
         return;
      }

      double normal = MoveUtil.baseSpeed(player);
      double target = 0.0;
      if (this.items.get() && player.isUsingItem()) {
         double slowed = normal * 0.2;
         target = slowed + (normal - slowed) * (this.strength.get() / 100.0);
      }

      if (this.slowBlocks.get()) {
         BlockState below = client.level.getBlockState(player.getBlockPosBelowThatAffectsMyMovement());
         if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.HONEY_BLOCK)) {
            target = Math.max(target, player.isUsingItem() ? target : normal);
         }
      }

      if (target > 0.0 && MoveUtil.horizontalSpeed(player) < target) {
         MoveUtil.setHorizontal(player, target);
      }
   }
}
