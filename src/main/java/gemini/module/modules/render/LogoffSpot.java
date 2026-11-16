package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Marks where players disappeared because they left the game. A player that only walked out of view, died or
 * changed dimension is still in the player list and is not marked; a marked player who comes back loses the mark.
 */
public final class LogoffSpot extends Module {
   private static final int MAX_SPOTS = 12;
   private final Map<UUID, Spot> activePlayers = new HashMap<>();
   private final List<Spot> loggedSpots = new ArrayList<>();
   private int tickStamp;

   public LogoffSpot() {
      super("LogoffSpot", "Marks where nearby players left the game.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.activePlayers.clear();
      this.loggedSpots.clear();
   }

   /** Everything remembered here belongs to the world that was just left. */
   @Override
   public void onWorldChange() {
      this.activePlayers.clear();
      this.loggedSpots.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || client.player == null) {
         return;
      }

      int stamp = ++this.tickStamp;
      for (Entity entity : client.level.entitiesForRendering()) {
         if (entity != client.player && entity instanceof Player p) {
            UUID id = p.getUUID();
            Spot spot = this.activePlayers.get(id);
            if (spot == null) {
               spot = new Spot(id, p.getName().getString());
               this.activePlayers.put(id, spot);
               this.forgetLogged(id);
            }

            spot.box = p.getBoundingBox();
            spot.seen = stamp;
         }
      }

      ClientPacketListener connection = client.getConnection();
      Iterator<Spot> it = this.activePlayers.values().iterator();
      while (it.hasNext()) {
         Spot spot = it.next();
         if (spot.seen != stamp) {
            it.remove();
            // Still in the player list: out of range, dead or in another dimension, not logged off.
            if (connection == null || connection.getPlayerInfo(spot.id) == null) {
               this.loggedSpots.add(spot);
               if (this.loggedSpots.size() > MAX_SPOTS) {
                  this.loggedSpots.removeFirst();
               }
            }
         }
      }
   }

   private void forgetLogged(UUID id) {
      for (int i = this.loggedSpots.size() - 1; i >= 0; i--) {
         if (this.loggedSpots.get(i).id.equals(id)) {
            this.loggedSpots.remove(i);
         }
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      for (int i = 0; i < this.loggedSpots.size(); i++) {
         Spot spot = this.loggedSpots.get(i);
         AABB box = spot.box;
         if (box != null) {
            Render3D.box(box, -14249, true, true, 1.6F, 45, true);
            Render3D.text(spot.label, (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5 + 1.1, (box.minZ + box.maxZ) * 0.5, -14249, 0.85F, true);
         }
      }
   }

   private static final class Spot {
      final UUID id;
      final String label;
      AABB box;
      int seen;

      Spot(UUID id, String name) {
         this.id = id;
         this.label = name + " (LOGGED)";
      }
   }
}
