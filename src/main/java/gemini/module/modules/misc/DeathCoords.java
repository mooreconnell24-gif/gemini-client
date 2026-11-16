package gemini.module.modules.misc;

import com.google.gson.JsonObject;
import gemini.Gemini;
import gemini.command.CommandManager;
import gemini.module.Category;
import gemini.module.Module;
import gemini.module.modules.world.WaypointsModule;
import gemini.module.setting.ActionSetting;
import gemini.module.setting.BooleanSetting;
import gemini.util.SingleplayerGate;
import gemini.util.SpCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;

public final class DeathCoords extends Module {
   private final BooleanSetting pin = this.addSetting(new BooleanSetting("Pin Waypoint", "Drop a waypoint where you died", false));
   private boolean announced;
   private boolean hasDeath;
   private String dimension = "minecraft:overworld";
   private int x;
   private int y;
   private int z;
   /** The save the death happened in ("" for deaths recorded before this was kept: those match any save). */
   private String world = "";
   private String infoText;

   public DeathCoords() {
      super("Death Coords", "Write your death coordinates in client chat and teleport back to them.", Category.CLIENT);
      this.addSetting(new ActionSetting("Teleport To Death", "Go back to where you last died", this::teleport));
   }

   @Override
   public void onTick() {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null) {
         if (!player.isDeadOrDying()) {
            this.announced = false;
         } else if (!this.announced) {
            this.announced = true;
            BlockPos pos = player.blockPosition();
            this.hasDeath = true;
            this.dimension = player.level().dimension().identifier().toString();
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.world = worldKey();
            this.infoText = null;
            this.save();
            CommandManager.feedback("§bDeath Coords §7" + this.x + " " + this.y + " " + this.z + " §8(" + this.dimension.replace("minecraft:", "") + ")");
            if (this.pin.get() && Gemini.getInstance() != null) {
               Gemini.getInstance().modules().byClass(WaypointsModule.class).ifPresent(waypoints -> waypoints.pin("Death", this.x, this.y, this.z));
            }
         }
      }
   }

   private void teleport() {
      if (!this.hasDeath || !this.world.isEmpty() && !this.world.equals(worldKey())) {
         CommandManager.feedback("§cYou have not died in this world yet.");
      } else if (!SingleplayerGate.allowed()) {
         CommandManager.feedback(SingleplayerGate.denialMessage());
      } else if (SpCommands.teleport(this.dimension, this.x + 0.5, this.y, this.z + 0.5)) {
         CommandManager.feedback("§bDeath Coords §7teleporting to your last death.");
      }
   }

   /** Which save is open: its seed and name, or "" outside a single-player world. */
   private static String worldKey() {
      IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
      try {
         return server == null ? "" : server.overworld().getSeed() + "|" + server.getWorldData().getLevelName();
      } catch (RuntimeException ignored) {
         return "";
      }
   }

   @Override
   public String info() {
      if (!this.hasDeath) {
         return null;
      }

      if (this.infoText == null) {
         this.infoText = this.x + " " + this.y + " " + this.z;
      }

      return this.infoText;
   }

   @Override
   public void writeExtra(JsonObject json) {
      json.addProperty("deathSet", this.hasDeath);
      json.addProperty("deathDim", this.dimension);
      json.addProperty("deathX", this.x);
      json.addProperty("deathY", this.y);
      json.addProperty("deathZ", this.z);
      json.addProperty("deathWorld", this.world);
   }

   @Override
   public void readExtra(JsonObject json) {
      this.hasDeath = json.has("deathSet") && json.get("deathSet").getAsBoolean();
      if (json.has("deathDim")) {
         this.dimension = json.get("deathDim").getAsString();
      }

      if (json.has("deathX") && json.has("deathY") && json.has("deathZ")) {
         this.x = json.get("deathX").getAsInt();
         this.y = json.get("deathY").getAsInt();
         this.z = json.get("deathZ").getAsInt();
      }

      this.world = json.has("deathWorld") ? json.get("deathWorld").getAsString() : "";
      this.infoText = null;
   }
}
