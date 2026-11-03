package gemini.module.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.module.setting.ColorSetting;
import gemini.render.Render3D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class WaypointsModule extends Module {
   private static final double LABEL_RANGE = 96.0;
   private final List<WaypointsModule.Waypoint> waypoints = new ArrayList<>();
   private final BooleanSetting beacons = this.addSetting(new BooleanSetting("3D Beacons", "Draw a marker and a beam at every pin in the world", true));
   private final BooleanSetting labels = this.addSetting(new BooleanSetting("Labels", "Show each pin's name and distance in the world", true));
   private final ColorSetting color = this.addSetting(new ColorSetting("Color", "Beacon and label colour", 190));

   public WaypointsModule() {
      super("Waypoints", "Pin locations and see how far away they are.", Category.WORLD);
      this.addSetting(new ActionSetting("Drop pin", "Save your current position (or use .waypoints add <name>)", this::dropPin));
      this.addSetting(new ActionSetting("Clear", "Remove every pin in this world", this::clear));
   }

   public List<WaypointsModule.Waypoint> waypoints() {
      return Collections.unmodifiableList(this.waypoints);
   }

   /** Pins belong to one world (identified by its seed); pins saved before that existed show everywhere. */
   public boolean visible(WaypointsModule.Waypoint waypoint) {
      Minecraft client = Minecraft.getInstance();
      if (client.level == null || !waypoint.dimension().equals(client.level.dimension().identifier().toString())) {
         return false;
      } else {
         return waypoint.world().isEmpty() || waypoint.world().equals(currentWorld());
      }
   }

   private static String currentWorld() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      try {
         return server == null ? "" : Long.toString(server.overworld().getSeed());
      } catch (RuntimeException ignored) {
         return "";
      }
   }

   public void dropPin() {
      this.add(null);
   }

   private void add(String name) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null && client.level != null) {
         BlockPos pos = client.player.blockPosition();
         String dimension = client.level.dimension().identifier().toString();
         String label = name == null || name.isBlank() ? "Pin " + (this.inThisWorld().size() + 1) : name.trim();
         WaypointsModule.Waypoint waypoint = new WaypointsModule.Waypoint(label, pos.getX(), pos.getY(), pos.getZ(), dimension, currentWorld());
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
      String world = currentWorld();
      int before = this.waypoints.size();
      this.waypoints.removeIf(waypoint -> waypoint.world().isEmpty() || waypoint.world().equals(world));
      CommandManager.feedback("§bGemini §7removed " + (before - this.waypoints.size()) + " waypoints");
      this.save();
   }

   private List<WaypointsModule.Waypoint> inThisWorld() {
      String world = currentWorld();
      List<WaypointsModule.Waypoint> list = new ArrayList<>();
      for (WaypointsModule.Waypoint waypoint : this.waypoints) {
         if (waypoint.world().isEmpty() || waypoint.world().equals(world)) {
            list.add(waypoint);
         }
      }

      return list;
   }

   private WaypointsModule.Waypoint find(String query) {
      List<WaypointsModule.Waypoint> list = this.inThisWorld();
      try {
         int index = Integer.parseInt(query) - 1;
         if (index >= 0 && index < list.size()) {
            return list.get(index);
         }
      } catch (NumberFormatException ignored) {
      }

      for (WaypointsModule.Waypoint waypoint : list) {
         if (waypoint.name().equalsIgnoreCase(query)) {
            return waypoint;
         }
      }

      for (WaypointsModule.Waypoint waypoint : list) {
         if (waypoint.name().toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) {
            return waypoint;
         }
      }

      return null;
   }

   @Override
   public boolean onCommand(String[] args) {
      if (args.length == 0) {
         return false;
      }

      String rest = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "add", "pin", "set" -> this.add(rest.isEmpty() ? null : rest);
         case "remove", "del", "delete" -> {
            WaypointsModule.Waypoint waypoint = rest.isEmpty() ? null : this.find(rest);
            if (waypoint == null) {
               CommandManager.feedback("§cNo waypoint called §f" + rest + "§c. Try §f.waypoints list");
            } else {
               this.waypoints.remove(waypoint);
               CommandManager.feedback("§bGemini §7removed §f" + waypoint.name());
               this.save();
            }
         }
         case "list", "ls" -> {
            List<WaypointsModule.Waypoint> list = this.inThisWorld();
            if (list.isEmpty()) {
               CommandManager.feedback("§7No waypoints in this world yet. Use §f.waypoints add <name>§7.");
            }

            LocalPlayer player = Minecraft.getInstance().player;
            for (int i = 0; i < list.size(); i++) {
               WaypointsModule.Waypoint waypoint = list.get(i);
               String where = waypoint.dimension().replace("minecraft:", "");
               String distance = player != null && this.visible(waypoint) ? "  §7" + Math.round(Math.sqrt(player.blockPosition().distSqr(new BlockPos(waypoint.x(), waypoint.y(), waypoint.z())))) + "m" : "";
               CommandManager.feedback("§f" + (i + 1) + ". §b" + waypoint.name() + " §7" + waypoint.x() + " " + waypoint.y() + " " + waypoint.z() + " §8(" + where + ")" + distance);
            }
         }
         case "tp", "goto" -> {
            WaypointsModule.Waypoint waypoint = rest.isEmpty() ? null : this.find(rest);
            if (waypoint == null) {
               CommandManager.feedback("§cNo waypoint called §f" + rest + "§c. Try §f.waypoints list");
            } else if (SoloModules.teleport(waypoint.dimension(), waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5)) {
               CommandManager.feedback("§bGemini §7teleporting to §f" + waypoint.name());
            }
         }
         case "clear" -> this.clear();
         default -> {
            return false;
         }
      }

      return true;
   }

   @Override
   public void onRender3D(float partialTick) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null || client.level == null || !this.beacons.get() && !this.labels.get()) {
         return;
      }

      int argb = this.color.argb();
      Vec3 eye = player.getEyePosition(partialTick);
      for (WaypointsModule.Waypoint waypoint : this.waypoints) {
         if (!this.visible(waypoint)) {
            continue;
         }

         Vec3 base = new Vec3(waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5);
         double distance = eye.distanceTo(base);
         if (this.beacons.get() && distance < 256.0) {
            Render3D.block(new BlockPos(waypoint.x(), waypoint.y(), waypoint.z()), argb, true, true, 1.5F, 40, true);
            Render3D.line(base, base.add(0.0, 48.0, 0.0), argb, 2.0F, true);
         }

         if (this.labels.get()) {
            // Far pins are drawn along the same line of sight but closer, so the label stays readable.
            Vec3 anchor = base.add(0.0, 1.6, 0.0);
            double shown = eye.distanceTo(anchor);
            if (shown > LABEL_RANGE) {
               anchor = eye.add(anchor.subtract(eye).normalize().scale(LABEL_RANGE));
               shown = LABEL_RANGE;
            }

            float scale = (float)Math.max(0.6, shown / 14.0);
            Render3D.text(waypoint.name() + "  " + Math.round(distance) + "m", anchor, argb, scale, true);
         }
      }
   }

   @Override
   public String info() {
      int count = this.inThisWorld().size();
      return count == 0 ? null : Integer.toString(count);
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
         entry.addProperty("world", waypoint.world());
         array.add(entry);
      }

      object.add("waypoints", array);
   }

   @Override
   public void readExtra(JsonObject object) {
      this.waypoints.clear();
      if (object.has("waypoints") && object.get("waypoints").isJsonArray()) {
         for (JsonElement element : object.getAsJsonArray("waypoints")) {
            try {
               JsonObject entry = element.getAsJsonObject();
               this.waypoints
                  .add(
                     new WaypointsModule.Waypoint(
                        entry.get("name").getAsString(),
                        entry.get("x").getAsInt(),
                        entry.get("y").getAsInt(),
                        entry.get("z").getAsInt(),
                        entry.has("dimension") ? entry.get("dimension").getAsString() : "minecraft:overworld",
                        entry.has("world") ? entry.get("world").getAsString() : ""
                     )
                  );
            } catch (RuntimeException ignored) {
               // A damaged entry should not wipe the rest of the pins.
            }
         }
      }
   }

   public record Waypoint(String name, int x, int y, int z, String dimension, String world) {
   }
}
