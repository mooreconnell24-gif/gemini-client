package gemini.util;

import gemini.event.EventBus;
import gemini.module.Module;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The integrated server disconnects the player ("Invalid move player packet received") when a movement packet
 * carries a NaN position or a non-finite rotation, the same check vanilla's {@code containsInvalidValues} makes.
 * Such a packet can only come from a module producing a bad angle or velocity, so it is dropped and logged.
 */
public final class MovePackets {
   private static final Logger LOGGER = LoggerFactory.getLogger("Gemini Movement");
   private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
   private static volatile int dropped;

   private MovePackets() {
   }

   public static boolean invalid(ServerboundMovePlayerPacket move) {
      return Double.isNaN(move.getX(0.0)) || Double.isNaN(move.getY(0.0)) || Double.isNaN(move.getZ(0.0))
         || !Float.isFinite(move.getYRot(0.0F)) || !Float.isFinite(move.getXRot(0.0F));
   }

   public static void dropped(ServerboundMovePlayerPacket move) {
      dropped++;
      Module current = EventBus.current();
      String where = current != null ? current.getName() : "the vanilla movement update";
      if (REPORTED.add(where)) {
         LOGGER.warn("Dropped a movement packet with an invalid position or rotation (sent from {}); a module produced NaN", where, new IllegalStateException("invalid move packet"));
      }
   }

   /** How many invalid movement packets were dropped since the game started. */
   public static int droppedCount() {
      return dropped;
   }
}
