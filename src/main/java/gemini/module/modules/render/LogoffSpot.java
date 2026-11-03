package gemini.module.modules.render;

import gemini.module.Category;
import gemini.module.Module;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class LogoffSpot extends Module {
   private final Map<UUID, LogoffSpot.Spot> activePlayers = new HashMap<>();
   private final List<LogoffSpot.Spot> loggedSpots = new ArrayList<>();

   public LogoffSpot() {
      super("LogoffSpot", "Mark 3D positions where nearby players log out.", Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.activePlayers.clear();
      this.loggedSpots.clear();
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null) {
         Map<UUID, LogoffSpot.Spot> current = new HashMap<>();

         for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity instanceof Player p) {
               current.put(p.getUUID(), new LogoffSpot.Spot(p.getName().getString(), p.getBoundingBox()));
            }
         }

         for (Entry<UUID, LogoffSpot.Spot> prev : this.activePlayers.entrySet()) {
            if (!current.containsKey(prev.getKey())) {
               this.loggedSpots.add(prev.getValue());
               if (this.loggedSpots.size() > 12) {
                  this.loggedSpots.removeFirst();
               }
            }
         }

         this.activePlayers.clear();
         this.activePlayers.putAll(current);
      }
   }

   @Override
   public void onRender3D(float partialTick) {
      for (LogoffSpot.Spot spot : this.loggedSpots) {
         Render3D.box(spot.box, -14249, true, true, 1.6F, 45, true);
         Render3D.text(spot.name + " (LOGGED)", spot.box.getCenter().add(0.0, 1.1, 0.0), -14249, 0.85F, true);
      }
   }

   private record Spot(String name, AABB box) {
   }
}
