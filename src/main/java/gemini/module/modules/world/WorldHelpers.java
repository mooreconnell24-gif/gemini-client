package gemini.module.modules.world;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the world modules (formerly statics on the module container classes). */
public final class WorldHelpers {
   private WorldHelpers() {
   }

   /** Weak, so a closed world's server is not kept alive by this cache. */
   private static WeakReference<IntegratedServer> keyServer = new WeakReference<>(null);
   private static String key = "";
   private static ResourceKey<Level> dimensionKey;
   private static String dimensionText = "";

   /**
    * The open single-player world's seed as text: what Waypoints, Stash Finder and Home store to tell one world's
    * entries from another's. Empty when no integrated server runs. Renderers and {@code info()} ask for it every
    * frame, so it is formatted once per server rather than on every call.
    */
   static String worldKey() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server == null) {
         return "";
      }

      if (server != keyServer.get()) {
         try {
            key = Long.toString(server.overworld().getSeed());
         } catch (RuntimeException ignored) {
            // the overworld is not loaded yet: try again next time
            return "";
         }

         keyServer = new WeakReference<>(server);
      }

      return key;
   }

   /** The dimension id as text ("minecraft:overworld"), formatted once per dimension rather than on every call. */
   static String dimensionName(ResourceKey<Level> dimension) {
      if (dimension != dimensionKey) {
         dimensionKey = dimension;
         dimensionText = dimension.identifier().toString();
      }

      return dimensionText;
   }

   static Direction faceToward(LocalPlayer player, BlockPos pos) {
      Vec3 eye = player.getEyePosition();
      return Direction.getApproximateNearest(eye.x - (pos.getX() + 0.5), eye.y - (pos.getY() + 0.5), eye.z - (pos.getZ() + 0.5));
   }

}
