package gemini.module;

import static gemini.module.DevCheck.fail;
import static gemini.module.DevCheck.info;
import static gemini.module.DevCheck.step;

import gemini.module.modules.misc.ClickGuiModule;
import gemini.util.MovePackets;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Switches every module on by itself for a few ticks (the self-test runs them all at once, which hides who did what)
 * and checks that the player is left with a finite position, rotation and velocity, that no movement packet had
 * to be dropped, and that the module did not crash.
 */
final class DevCheckSweep {
   private static final double[] saved = new double[3];
   private static int droppedBefore;

   private DevCheckSweep() {
   }

   private static boolean finite(LocalPlayer player) {
      Vec3 motion = player.getDeltaMovement();
      return Double.isFinite(player.getX()) && Double.isFinite(player.getY()) && Double.isFinite(player.getZ())
         && Float.isFinite(player.getYRot()) && Float.isFinite(player.getXRot())
         && Double.isFinite(motion.x) && Double.isFinite(motion.y) && Double.isFinite(motion.z);
   }

   static void queue(ModuleManager modules) {
      step(5, () -> {
         ModuleManager.drainFailures();
         SpCommands.run("gamemode creative");
         LocalPlayer player = Minecraft.getInstance().player;
         saved[0] = player.getX();
         saved[1] = player.getY();
         saved[2] = player.getZ();
      });
      int[] swept = new int[1];
      for (Module module : modules.getModules()) {
         if (module instanceof ClickGuiModule || module.selfTestExempt() || module.isEnabled()) {
            continue;
         }

         step(4, () -> {
            droppedBefore = MovePackets.droppedCount();
            module.setHeld(true);
            swept[0]++;
         });
         step(1, () -> {
            LocalPlayer player = Minecraft.getInstance().player;
            boolean ok = finite(player);
            int dropped = MovePackets.droppedCount() - droppedBefore;
            module.setHeld(false);
            if (!ok || dropped > 0) {
               fail(module.getName() + ": left the player with NaN position/rotation/velocity (" + dropped + " movement packet(s) dropped)");
               player.setPos(saved[0], saved[1], saved[2]);
               player.setDeltaMovement(Vec3.ZERO);
               player.setYRot(0.0F);
               player.setXRot(0.0F);
            }

            for (ModuleManager.Failure failure : ModuleManager.drainFailures()) {
               fail(failure.module() + " " + failure.hook() + ": " + failure.error());
            }
         });
      }

      step(1, () -> info("swept " + swept[0] + " module(s) one at a time"));
   }
}
