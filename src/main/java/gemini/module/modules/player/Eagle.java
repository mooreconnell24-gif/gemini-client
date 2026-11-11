package gemini.module.modules.player;

import gemini.util.ClientState;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class Eagle extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Edge sneaks at drops; Always sneaks over air", "Edge", "Edge", "Always")
   );
   private final NumberSetting lookAhead = this.addSetting(
      new NumberSetting("Look Ahead", "How far past your hitbox counts as the edge", 0.3, 0.05, 0.6, 0.05)
   );
   private final BooleanSetting onlyBackwards = this.addSetting(new BooleanSetting("Only Backwards", "Only sneak while holding the back key", false));
   private boolean sneaking;

   public Eagle() {
      super("Eagle", "Hold sneak for you at block edges while bridging.", Category.PLAYER);
   }

   @Override
   public void onDisable() {
      this.release();
   }

   private void release() {
      if (this.sneaking) {
         this.sneaking = false;
         Minecraft client = Minecraft.getInstance();
         client.options.keyShift.setDown(MoveUtil.physicalKeyDown(client.options.keyShift));
      }
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (ClientState.ready(client) && client.gui.screen() == null) {
         LocalPlayer player = client.player;
         if (player.onGround() && (!this.onlyBackwards.get() || client.options.keyDown.isDown())) {
            boolean edge = this.mode.is("Always") ? nothingBelow(client, player, 0.0) : nothingBelow(client, player, this.lookAhead.get());
            if (edge) {
               this.sneaking = true;
               client.options.keyShift.setDown(true);
            } else {
               this.release();
            }
         } else {
            this.release();
         }
      } else {
         this.release();
      }
   }

   private static boolean nothingBelow(Minecraft client, LocalPlayer player, double extra) {
      Level level = client.level;
      double y = player.getY() - 0.5;
      double half = player.getBbWidth() / 2.0 + extra;

      for (double dx : new double[]{-half, half}) {
         for (double dz : new double[]{-half, half}) {
            BlockPos pos = BlockPos.containing(player.getX() + dx, y, player.getZ() + dz);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
               return false;
            }
         }
      }

      return true;
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
