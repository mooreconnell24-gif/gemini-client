package gemini.xray;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public final class XrayData {
   private static final ConcurrentHashMap<Long, long[]> SECTIONS = new ConcurrentHashMap<>();

   private XrayData() {
   }

   static void commit(long sectionKey, LongArrayList positions) {
      if (positions.isEmpty()) {
         SECTIONS.remove(sectionKey);
      } else {
         SECTIONS.put(sectionKey, positions.toLongArray());
      }
   }

   public static void clear() {
      SECTIONS.clear();
   }

   public static int sections() {
      return SECTIONS.size();
   }

   public static void prune(int cameraSectionX, int cameraSectionZ, int radius) {
      SECTIONS.keySet().removeIf(key -> Math.abs(SectionPos.x(key) - cameraSectionX) > radius || Math.abs(SectionPos.z(key) - cameraSectionZ) > radius);
   }

   public static void visitNear(BlockPos origin, double range, XrayData.Visitor visitor) {
      double rangeSq = range * range;
      int sectionRange = (int)Math.ceil(range / 16.0) + 1;
      int ox = origin.getX() >> 4;
      int oy = origin.getY() >> 4;
      int oz = origin.getZ() >> 4;

      for (Entry<Long, long[]> entry : SECTIONS.entrySet()) {
         long key = entry.getKey();
         if (Math.abs(SectionPos.x(key) - ox) <= sectionRange
            && Math.abs(SectionPos.z(key) - oz) <= sectionRange
            && Math.abs(SectionPos.y(key) - oy) <= sectionRange) {
            for (long packed : entry.getValue()) {
               double dx = BlockPos.getX(packed) + 0.5 - origin.getX();
               double dy = BlockPos.getY(packed) + 0.5 - origin.getY();
               double dz = BlockPos.getZ(packed) + 0.5 - origin.getZ();
               if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                  visitor.visit(packed);
               }
            }
         }
      }
   }

   public static void visitAll(XrayData.Visitor visitor) {
      for (long[] positions : SECTIONS.values()) {
         for (long packed : positions) {
            visitor.visit(packed);
         }
      }
   }

   @FunctionalInterface
   public interface Visitor {
      void visit(long var1);
   }
}
