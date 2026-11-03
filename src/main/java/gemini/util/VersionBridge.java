package gemini.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Small helpers for version-dependent world rules on the Minecraft build Gemini targets. */
public final class VersionBridge {
   private VersionBridge() {
   }

   public static String activeVersionLabel() {
      try {
         return SharedConstants.getCurrentVersion().name() + " (single-player)";
      } catch (Throwable ignored) {
         return "unknown (single-player)";
      }
   }

   public static boolean canPlaceCrystalSpace(Level level, BlockPos basePos) {
      return level != null && basePos != null && level.getBlockState(basePos.above()).isAir();
   }

   public static int minWorldY(Level level) {
      return level == null ? -64 : level.getMinY();
   }

   public static int maxWorldY(Level level) {
      return level == null ? 320 : level.getMaxY();
   }
}
