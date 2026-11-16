package gemini.module.modules.misc;

import gemini.module.Category;
import gemini.module.Module;
import gemini.ui.HudOverlay;
import gemini.util.InfoText;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * Counts setbacks: the world moving you back a short way. Position packets that are really teleports (joining,
 * respawning, a portal, /tp) are told apart by distance and timing and are not counted.
 */
public final class FlagCheck extends Module {
   /** A position sent back more than this far away is a teleport, not a correction of your movement. */
   static final double MAX_SETBACK = 8.0;
   /** Position packets in the first ticks after you appear are the spawn placement. */
   private static final int SPAWN_TICKS = 40;
   private int flags;
   private final InfoText infoText = new InfoText();

   public FlagCheck() {
      super("FlagCheck", "Counts and warns you each time the game pulls you back to an earlier position, ignoring teleports and respawns.", Category.CLIENT);
   }

   @Override
   public void onEnable() {
      this.flags = 0;
   }

   @Override
   public String info() {
      return this.infoText.whole(this.flags, "");
   }

   @Override
   public boolean onReceivePacket(Packet<?> packet) {
      if (packet instanceof ClientboundPlayerPositionPacket position) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && player.tickCount > SPAWN_TICKS && isSetback(player.getX(), player.getY(), player.getZ(), position.change().position(), position.relatives())) {
            this.flags++;
            HudOverlay.toast("FlagCheck", "Setback #" + this.flags + " detected", HudOverlay.Severity.WARNING);
         }
      }

      return false;
   }

   /** Whether a move to {@code target} (coordinates relative to the current ones where {@code relatives} says so) is a short setback. */
   static boolean isSetback(double x, double y, double z, Vec3 target, Set<Relative> relatives) {
      double dx = relatives.contains(Relative.X) ? target.x : target.x - x;
      double dy = relatives.contains(Relative.Y) ? target.y : target.y - y;
      double dz = relatives.contains(Relative.Z) ? target.z : target.z - z;
      double distanceSq = dx * dx + dy * dy + dz * dz;
      boolean allRelative = relatives.contains(Relative.X) && relatives.contains(Relative.Y) && relatives.contains(Relative.Z);
      // "Stay where you are" with only a new rotation (a /rotate, for example) moves nothing back.
      return distanceSq <= MAX_SETBACK * MAX_SETBACK && !(allRelative && distanceSq < 1.0E-8);
   }
}
