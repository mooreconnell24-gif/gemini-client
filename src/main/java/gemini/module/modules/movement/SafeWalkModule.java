package gemini.module.modules.movement;

import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ModeSetting;
import gemini.module.setting.NumberSetting;
import gemini.util.ClientState;
import gemini.util.MoveUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Keeps you on the block you stand on. "Stop" holds you at the edge the way sneaking does, without sneaking (the
 * player mixin asks {@link #stopsAtEdges()}); "Sneak" presses the sneak key for you at the edge, which is what
 * bridging backwards needs because blocks can then be placed against the side of the block below.
 */
public final class SafeWalkModule extends Module {
   private final ModeSetting mode = this.addSetting(
      new ModeSetting("Mode", "Stop holds you at the edge without sneaking; Sneak presses sneak for you at the edge, for bridging", "Stop", "Stop", "Sneak")
   );
   private final NumberSetting lookAhead = this.addSetting(
      new NumberSetting("Look Ahead", "How far ahead of you the edge is looked for, in blocks; higher sneaks earlier", 0.15, 0.0, 0.6, 0.05).showIf(() -> this.mode.is("Sneak"))
   );
   private final BooleanSetting onlyBackwards = this.addSetting(
      new BooleanSetting("Only Backwards", "Only sneak while holding the back key", false).showIf(() -> this.mode.is("Sneak"))
   );
   private boolean sneaking;

   public SafeWalkModule() {
      super("Safe Walk", "Stops you walking off edges.", Category.MOVEMENT);
   }

   @Override
   public List<String> aliases() {
      List<String> names = new ArrayList<>(super.aliases());
      names.add("Eagle");
      return names;
   }

   /** Whether the edge stop of the player mixin applies (Stop mode). */
   public boolean stopsAtEdges() {
      return this.mode.is("Stop");
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
      if (!this.mode.is("Sneak") || !ClientState.ready(client) || client.gui.screen() != null) {
         this.release();
         return;
      }

      LocalPlayer player = client.player;
      if (player.onGround() && (!this.onlyBackwards.get() || client.options.keyDown.isDown()) && nothingBelow(client.level, player, this.lookAhead.get())) {
         this.sneaking = true;
         client.options.keyShift.setDown(true);
      } else {
         this.release();
      }
   }

   /**
    * True when the point {@code ahead} blocks in front of the middle of the player's feet (along the way they are
    * moving) has nothing solid under it. Sneaking from that moment keeps the player on the block: vanilla holds a
    * sneaking player as long as any part of them is still supported.
    */
   private static boolean nothingBelow(Level level, LocalPlayer player, double ahead) {
      double vx = player.getDeltaMovement().x;
      double vz = player.getDeltaMovement().z;
      double speed = Math.sqrt(vx * vx + vz * vz);
      double x = player.getX();
      double z = player.getZ();
      if (speed > 1.0E-4) {
         x += vx / speed * ahead;
         z += vz / speed * ahead;
      }

      BlockPos pos = BlockPos.containing(x, player.getY() - 0.5, z);
      return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
   }

   @Override
   public String info() {
      return this.mode.get();
   }
}
