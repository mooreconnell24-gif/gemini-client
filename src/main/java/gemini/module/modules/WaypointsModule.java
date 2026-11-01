package gemini.module.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class WaypointsModule extends Module {
   private final List<WaypointsModule.Waypoint> waypoints = new ArrayList<>();

   public WaypointsModule() {
      super("Waypoints", "Pin locations and see how far away they are.", Category.WORLD);
      this.addSetting(new ActionSetting("Drop pin", "Save your current position", this::dropPin));
      this.addSetting(new ActionSetting("Clear", "Remove every pin", this::clear));
   }

   public List<WaypointsModule.Waypoint> waypoints() {
      return Collections.unmodifiableList(this.waypoints);
   }

   public void dropPin() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         BlockPos pos = client.player.blockPosition();
         String dimension = client.level.dimension().identifier().toString();
         WaypointsModule.Waypoint waypoint = new WaypointsModule.Waypoint("Pin " + (this.waypoints.size() + 1), pos.getX(), pos.getY(), pos.getZ(), dimension);
         this.waypoints.add(waypoint);
         CommandManager.feedback("§bGemini §7pinned §f" + waypoint.name() + " §7at §f" + waypoint.x() + " " + waypoint.y() + " " + waypoint.z());
         if (!this.isSavedEnabled()) {
            this.setEnabled(true);
         } else {
            this.save();
         }
      }
   }

   public void clear() {
      this.waypoints.clear();
      CommandManager.feedback("§bGemini §7waypoints cleared");
      this.save();
   }

   @Override
   public void writeExtra(JsonObject object) {
      JsonArray array = new JsonArray();

      for (WaypointsModule.Waypoint waypoint : this.waypoints) {
         JsonObject entry = new JsonObject();
         entry.addProperty("name", waypoint.name());
         entry.addProperty("x", waypoint.x());
         entry.addProperty("y", waypoint.y());
         entry.addProperty("z", waypoint.z());
         entry.addProperty("dimension", waypoint.dimension());
         array.add(entry);
      }

      object.add("waypoints", array);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.waypoints.clear();
      if (object.has("waypoints")) {
         for (JsonElement element : object.getAsJsonArray("waypoints")) {
            JsonObject entry = element.getAsJsonObject();
            this.waypoints
               .add(
                  new WaypointsModule.Waypoint(
                     entry.get("name").getAsString(),
                     entry.get("x").getAsInt(),
                     entry.get("y").getAsInt(),
                     entry.get("z").getAsInt(),
                     entry.get("dimension").getAsString()
                  )
               );
         }
      }
   }

   public record Waypoint(String name, int x, int y, int z, String dimension) {
   }
}
